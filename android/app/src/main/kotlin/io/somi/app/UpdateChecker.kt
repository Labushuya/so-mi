package io.somi.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
 * GitHub release checker + APK download via DownloadManager.
 *
 * WHY THIS DESIGN:
 * - The DownloadManager transitions to STATUS_SUCCESSFUL asynchronously; polling
 *   alone can miss the final state if the status flips between two polls.
 * - ACTION_DOWNLOAD_COMPLETE fires reliably, but BroadcastReceiver + callbackFlow
 *   has a known gotcha: if the coroutine is cancelled before awaitClose() is reached,
 *   the atomic guard never resets.
 * - Solution: use a Channel as a one-shot done signal. The BroadcastReceiver sends
 *   to the Channel; the polling loop reads from it. The loop runs inside a plain
 *   flow {} builder (not callbackFlow), so cancellation is handled by Kotlin's
 *   normal coroutine machinery — no awaitClose() risk.
 * - The guard is an AtomicLong (activeDownloadId), reset in a finally block that
 *   the flow builder always executes.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL =
        "https://api.github.com/repos/Labushuya/so-mi/releases/latest"
    private const val CACHE_TTL_MS = 30_000L
    private const val POLL_MS = 800L
    private const val TIMEOUT_MS = 180_000L

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val lastCheckTime = AtomicLong(0L)
    private val lastResult = AtomicReference<UpdateInfo?>(null)
    // -1L = no active download
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
        data object Done : DownloadState
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
        if (age < CACHE_TTL_MS) return lastResult.get()
        return fetchLatest(currentVersionName).also {
            lastResult.set(it)
            lastCheckTime.set(System.currentTimeMillis())
        }
    }

    /**
     * Enqueues the APK download and emits [DownloadState] until the download
     * completes (Done) or fails (Failed).
     *
     * Architecture:
     *  - A Channel<Boolean> (success flag) bridges the BroadcastReceiver
     *    (which lives on the main thread) into the IO-dispatched flow loop.
     *  - The polling loop emits Progress every [POLL_MS] ms.
     *  - When the receiver fires, it sends to the channel and the loop breaks.
     *  - If the polling detects STATUS_SUCCESSFUL before the receiver fires
     *    (timing edge case), the loop breaks immediately.
     *  - All cleanup (receiver unregister, guard reset) happens in try/finally
     *    in the flow builder — survives both normal completion and cancellation.
     *
     * After Done, the user taps the system notification to open PackageInstaller.
     * We do NOT fire an install intent — ACTION_INSTALL_PACKAGE is blocked by
     * Android 14 policy for non-system sideload apps (targetSdk 35).
     */
    fun downloadAndInstall(context: Context, apkUrl: String, versionName: String): Flow<DownloadState> =
        flow {
            if (!activeDownloadId.compareAndSet(-1L, 0L)) {
                emit(DownloadState.AlreadyRunning)
                return@flow
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val updatesDir = File(context.getExternalFilesDir(null), "updates").also { it.mkdirs() }
            val destFile = File(updatesDir, "so-mi-$versionName.apk")
            if (destFile.exists()) destFile.delete()

            // Channel capacity=1; receiver sends at most one signal.
            val doneChannel = Channel<Boolean>(capacity = 1)
            var receiver: BroadcastReceiver? = null

            try {
                val downloadId = dm.enqueue(
                    DownloadManager.Request(Uri.parse(apkUrl)).apply {
                        setTitle("So-Mi v$versionName")
                        setDescription("Tippe nach Download auf diese Meldung zum Installieren")
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

                // Register BroadcastReceiver on the main thread.
                withContext(Dispatchers.Main) {
                    receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) {
                            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                            if (id != downloadId) return
                            val cursor = dm.query(DownloadManager.Query().setFilterById(id))
                            val success = cursor?.use { c ->
                                c.moveToFirst() &&
                                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                                    DownloadManager.STATUS_SUCCESSFUL
                            } ?: false
                            Log.i(TAG, "ACTION_DOWNLOAD_COMPLETE id=$id success=$success")
                            doneChannel.trySend(success)
                        }
                    }
                    context.registerReceiver(
                        receiver,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_NOT_EXPORTED,
                    )
                }

                // Polling loop — emits progress, exits on receiver signal or STATUS_SUCCESSFUL.
                var elapsed = 0L
                var terminated = false
                while (!terminated) {
                    // Check for receiver signal (non-blocking).
                    val done = doneChannel.tryReceive()
                    if (done.isSuccess) {
                        val success = done.getOrDefault(false)
                        emit(if (success) DownloadState.Progress(100) else DownloadState.Failed)
                        emit(if (success) DownloadState.Done else DownloadState.Failed)
                        terminated = true
                        break
                    }

                    // Poll DM for progress / early success detection.
                    val pct = queryProgress(dm, downloadId)
                    when {
                        pct == null -> {
                            // Status is not RUNNING/PAUSED/PENDING — could be SUCCESSFUL
                            // before receiver fires, or FAILED.
                            val status = queryStatus(dm, downloadId)
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                Log.i(TAG, "STATUS_SUCCESSFUL detected via polling before receiver")
                                emit(DownloadState.Progress(100))
                                emit(DownloadState.Done)
                                terminated = true
                            } else if (status == DownloadManager.STATUS_FAILED) {
                                Log.e(TAG, "STATUS_FAILED")
                                emit(DownloadState.Failed)
                                terminated = true
                            }
                            // else pending/unknown — keep waiting for receiver
                        }
                        else -> emit(DownloadState.Progress(pct))
                    }

                    if (!terminated) {
                        delay(POLL_MS)
                        elapsed += POLL_MS
                        if (elapsed >= TIMEOUT_MS) {
                            Log.w(TAG, "download timed out")
                            dm.remove(downloadId)
                            emit(DownloadState.Failed)
                            terminated = true
                        }
                    }
                }
            } finally {
                activeDownloadId.set(-1L)
                doneChannel.close()
                receiver?.let { r ->
                    runCatching { context.unregisterReceiver(r) }
                }
            }
        }.flowOn(Dispatchers.IO)

    private fun queryProgress(dm: DownloadManager, id: Long): Int? {
        val c = dm.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return c.use {
            if (!it.moveToFirst()) return@use null
            when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_PENDING -> {
                    val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done  = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    if (total <= 0L) 0 else ((done * 100L) / total).toInt().coerceIn(0, 99)
                }
                else -> null
            }
        }
    }

    private fun queryStatus(dm: DownloadManager, id: Long): Int {
        val c = dm.query(DownloadManager.Query().setFilterById(id)) ?: return -1
        return c.use {
            if (!it.moveToFirst()) -1
            else it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
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
                UpdateInfo(tagName, apkUrl, notes, isNewer, isUpToDate = !isNewer)
            }.onFailure { Log.w(TAG, "update check failed", it) }.getOrNull()
        }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.split(".").mapNotNull { it.filter(Char::isDigit).toIntOrNull() }
        val l = parts(latest); val c = parts(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }; val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true; if (lv < cv) return false
        }
        return false
    }
}
