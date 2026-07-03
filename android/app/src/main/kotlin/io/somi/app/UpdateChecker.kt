package io.somi.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Checks GitHub Releases and handles download + install.
 *
 * [downloadAndInstall] returns a Flow<Int?> that emits:
 *   0..100  — download progress in percent
 *   null    — terminal: installer opened (success) or failed/cancelled
 *
 * This lets the UI show live progress without relying on local remember{} state
 * that gets reset on recomposition. The Flow is hot via callbackFlow and tied to
 * the caller's coroutine scope.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL =
        "https://api.github.com/repos/Labushuya/so-mi/releases/latest"
    private const val CACHE_TTL_MS = 30_000L
    private const val DOWNLOAD_TIMEOUT_MS = 120_000L
    private const val PROGRESS_POLL_MS = 500L

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val lastCheckTime = AtomicLong(0L)
    private val lastResult = AtomicReference<UpdateInfo?>(null)
    private val downloadInFlight = AtomicBoolean(false)

    data class UpdateInfo(
        val latestVersion: String,
        val apkUrl: String,
        val releaseNotes: String,
        val isNewer: Boolean,
        val isUpToDate: Boolean,
    )

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
     * Downloads the APK and emits progress (0–100), then null as terminal.
     * Guarded by [downloadInFlight] — concurrent calls close immediately with null.
     * Collect this Flow in a coroutine scope tied to the UI lifecycle.
     */
    fun downloadAndInstall(context: Context, apkUrl: String, versionName: String): Flow<Int?> =
        callbackFlow {
            if (!downloadInFlight.compareAndSet(false, true)) {
                Log.d(TAG, "download already in flight, ignoring")
                trySend(null)
                close()
                return@callbackFlow
            }

            val updatesDir = File(context.getExternalFilesDir(null), "updates").also { it.mkdirs() }
            val destFile = File(updatesDir, "so-mi-$versionName.apk")
            if (destFile.exists()) destFile.delete()

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("So-Mi v$versionName")
                setDescription("Update wird heruntergeladen…")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destFile))
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
                )
                setMimeType("application/vnd.android.package-archive")
            }
            val downloadId = dm.enqueue(req)
            Log.i(TAG, "download enqueued id=$downloadId")

            // BroadcastReceiver for completion signal
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id != downloadId) return
                    runCatching { ctx.unregisterReceiver(this) }

                    val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                    val ok = cursor?.use { c ->
                        c.moveToFirst() &&
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                            DownloadManager.STATUS_SUCCESSFUL
                    } ?: false

                    if (ok) {
                        val installerUri = dm.getUriForDownloadedFile(downloadId)
                        if (installerUri != null) launchInstaller(ctx, installerUri)
                        else Log.e(TAG, "null URI from DM for $downloadId")
                    }
                    trySend(null)   // terminal — success or failure
                    close()
                }
            }
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED,
            )

            // Progress polling loop — runs until channel closes
            withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                while (!isClosedForSend) {
                    val progress = queryProgress(dm, downloadId)
                    if (progress != null) trySend(progress)
                    delay(PROGRESS_POLL_MS)
                }
            } ?: run {
                Log.w(TAG, "download timed out, cancelling")
                runCatching { context.unregisterReceiver(receiver) }
                dm.remove(downloadId)
                trySend(null)
                close()
            }

            awaitClose {
                downloadInFlight.set(false)
                runCatching { context.unregisterReceiver(receiver) }
            }
        }.flowOn(Dispatchers.IO)

    private fun queryProgress(dm: DownloadManager, downloadId: Long): Int? {
        val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId)) ?: return null
        return cursor.use { c ->
            if (!c.moveToFirst()) return@use null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_RUNNING &&
                status != DownloadManager.STATUS_PAUSED) return@use null
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            if (total <= 0) 0 else ((done * 100L) / total).toInt().coerceIn(0, 99)
        }
    }

    private fun launchInstaller(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "launchInstaller failed: $it") }
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
