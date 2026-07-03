package io.somi.app

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Checks GitHub Releases and handles APK download.
 *
 * Install strategy: we let the system DownloadManager notification handle
 * opening the PackageInstaller. This is the only method that works reliably
 * on all OEM variants (MagicOS, OneUI, stock AOSP) without requiring a system
 * signature or Play Store distribution. In-app install intents (ACTION_INSTALL_PACKAGE,
 * ACTION_VIEW) are blocked by Android 14+ policy on sideload apps with targetSdk 35.
 *
 * [downloadAndInstall] returns a Flow<DownloadState> for in-app progress display.
 * The actual PackageInstaller is opened when the user taps the system notification,
 * or automatically via ACTION_DOWNLOAD_COMPLETE intent if the system supports it.
 *
 * No global AtomicBoolean singleton — the Flow itself is the guard. Multiple
 * simultaneous downloads are prevented by [activeDownloadId].
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL =
        "https://api.github.com/repos/Labushuya/so-mi/releases/latest"
    private const val CACHE_TTL_MS = 30_000L
    private const val POLL_MS = 500L
    private const val TIMEOUT_MS = 180_000L

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val lastCheckTime = AtomicLong(0L)
    private val lastResult = AtomicReference<UpdateInfo?>(null)

    // -1 = no active download. Set when a download starts, cleared when it ends.
    private val activeDownloadId = AtomicLong(-1L)

    data class UpdateInfo(
        val latestVersion: String,
        val apkUrl: String,
        val releaseNotes: String,
        val isNewer: Boolean,
        val isUpToDate: Boolean,
    )

    sealed interface DownloadState {
        data class Progress(val percent: Int) : DownloadState
        data object Done : DownloadState     // download finished, tap notification to install
        data object Failed : DownloadState
        data object AlreadyRunning : DownloadState
    }

    suspend fun check(currentVersionName: String): UpdateInfo? =
        fetchLatest(currentVersionName).also {
            lastResult.set(it)
            lastCheckTime.set(System.currentTimeMillis())
        }

    suspend fun checkManually(currentVersionName: String): UpdateInfo? {
        val age = System.currentTimeMillis() - lastCheckTime.get()
        if (age < CACHE_TTL_MS) {
            Log.d(TAG, "throttled, returning cached (age ${age}ms)")
            return lastResult.get()
        }
        return fetchLatest(currentVersionName).also {
            lastResult.set(it)
            lastCheckTime.set(System.currentTimeMillis())
        }
    }

    /**
     * Starts downloading the APK via DownloadManager and emits progress.
     *
     * Emits [DownloadState.Progress] (0..100) during download.
     * Emits [DownloadState.Done] when complete — user must tap the system
     * notification to open PackageInstaller (this is intentional; see class KDoc).
     * Emits [DownloadState.Failed] on error or timeout.
     * Emits [DownloadState.AlreadyRunning] if another download is active.
     *
     * The Flow completes after Done/Failed/AlreadyRunning.
     */
    fun downloadAndInstall(context: Context, apkUrl: String, versionName: String): Flow<DownloadState> =
        callbackFlow {
            // Guard: prevent concurrent downloads
            if (activeDownloadId.get() != -1L) {
                Log.d(TAG, "download already running id=${activeDownloadId.get()}")
                send(DownloadState.AlreadyRunning)
                close()
                return@callbackFlow
            }

            val updatesDir = File(context.getExternalFilesDir(null), "updates").also { it.mkdirs() }
            val destFile = File(updatesDir, "so-mi-$versionName.apk")
            if (destFile.exists()) destFile.delete()

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = dm.enqueue(
                DownloadManager.Request(Uri.parse(apkUrl)).apply {
                    setTitle("So-Mi v$versionName")
                    setDescription("Tippe nach dem Download auf diese Meldung zum Installieren")
                    // VISIBILITY_VISIBLE_NOTIFY_COMPLETED: notification stays after download
                    // and opens PackageInstaller when tapped — most reliable on all OEMs.
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationUri(Uri.fromFile(destFile))
                    setAllowedNetworkTypes(
                        DownloadManager.Request.NETWORK_WIFI or
                        DownloadManager.Request.NETWORK_MOBILE
                    )
                    setMimeType("application/vnd.android.package-archive")
                }
            )
            activeDownloadId.set(downloadId)
            Log.i(TAG, "download enqueued id=$downloadId")

            var elapsed = 0L
            var finalState: DownloadState = DownloadState.Failed

            try {
                while (true) {
                    val state = queryState(dm, downloadId)
                    when (state) {
                        is DownloadState.Progress -> {
                            send(state)
                            delay(POLL_MS)
                            elapsed += POLL_MS
                            if (elapsed >= TIMEOUT_MS) {
                                Log.w(TAG, "download timed out")
                                dm.remove(downloadId)
                                finalState = DownloadState.Failed
                                break
                            }
                        }
                        is DownloadState.Done -> {
                            send(DownloadState.Progress(100))
                            finalState = DownloadState.Done
                            Log.i(TAG, "download complete — user should tap notification to install")
                            break
                        }
                        else -> {
                            Log.e(TAG, "download failed state=$state")
                            finalState = DownloadState.Failed
                            break
                        }
                    }
                }
            } finally {
                activeDownloadId.set(-1L)
            }

            send(finalState)
            close()
            awaitClose { /* nothing to unregister */ }
        }.flowOn(Dispatchers.IO)

    private fun queryState(dm: DownloadManager, id: Long): DownloadState {
        val cursor = dm.query(DownloadManager.Query().setFilterById(id))
            ?: return DownloadState.Failed
        return cursor.use { c ->
            if (!c.moveToFirst()) return@use DownloadState.Failed
            when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> DownloadState.Done
                DownloadManager.STATUS_FAILED     -> DownloadState.Failed
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_PENDING    -> {
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done  = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val pct = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 100) else 0
                    DownloadState.Progress(pct)
                }
                else -> DownloadState.Failed
            }
        }
    }

    private suspend fun fetchLatest(currentVersionName: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val req = Request.Builder()
                    .url(RELEASES_URL)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                val body = http.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) return@runCatching null
                    r.body?.string() ?: return@runCatching null
                }
                val json = JSONObject(body)
                val tagName = json.getString("tag_name").removePrefix("v")
                val assets = json.getJSONArray("assets")
                val apkUrl = (0 until assets.length())
                    .map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                    ?.getString("browser_download_url")
                    ?: return@runCatching null
                val notes = json.optString("body", "").lines().take(5).joinToString("\n")
                val isNewer = isVersionNewer(tagName, currentVersionName)
                Log.i(TAG, "latest=$tagName current=$currentVersionName newer=$isNewer")
                UpdateInfo(tagName, apkUrl, notes, isNewer, isUpToDate = !isNewer)
            }.onFailure { Log.w(TAG, "update check failed", it) }.getOrNull()
        }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val l = parts(latest); val c = parts(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }; val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
