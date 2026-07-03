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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Checks GitHub Releases and handles APK download + install.
 *
 * [downloadAndInstall] returns a Flow<Int?> that emits:
 *   0..99  — live download progress (polled every 500 ms)
 *   null   — terminal: installer intent fired (success) or error
 *
 * Design notes:
 *  - The BroadcastReceiver is the sole authority for "download done" — it closes
 *    the channel and fires the installer. The progress-polling loop only emits
 *    percentage values; it never closes the channel itself. This fixes the
 *    "stuck at 60%" issue where the polling loop saw STATUS != RUNNING after
 *    the download finished but before the receiver fired, and stopped emitting
 *    without closing the channel.
 *  - Installer uses ACTION_INSTALL_PACKAGE (not ACTION_VIEW) — required for
 *    REQUEST_INSTALL_PACKAGES to take effect on Android 8+ / MagicOS. ACTION_VIEW
 *    with a DM content:// URI routes to the media browser on some OEM variants
 *    instead of the PackageInstaller, causing the "no reaction" bug.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL =
        "https://api.github.com/repos/Labushuya/so-mi/releases/latest"
    private const val CACHE_TTL_MS = 30_000L
    private const val DOWNLOAD_TIMEOUT_MS = 180_000L
    private const val PROGRESS_POLL_MS = 600L

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
            Log.i(TAG, "download enqueued id=$downloadId dest=${destFile.absolutePath}")

            // ── BroadcastReceiver: sole authority for "done" ──────────────────────
            // It closes the channel and fires the installer. Progress loop only emits
            // percentage; it never closes the channel on its own (fixes "stuck at X%").
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id != downloadId) return
                    runCatching { ctx.unregisterReceiver(this) }

                    val cursor = dm.query(DownloadManager.Query().setFilterById(downloadId))
                    val success = cursor?.use { c ->
                        c.moveToFirst() &&
                        c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                            DownloadManager.STATUS_SUCCESSFUL
                    } ?: false

                    if (success && destFile.exists()) {
                        Log.i(TAG, "download complete, launching installer")
                        launchInstaller(ctx, destFile)
                    } else {
                        Log.e(TAG, "download failed or file missing (success=$success)")
                    }
                    trySend(null)  // terminal
                    close()
                }
            }
            context.registerReceiver(
                receiver,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED,
            )

            // ── Progress polling — emit only, never close ─────────────────────────
            var timeoutMs = 0L
            while (!isClosedForSend) {
                val p = queryProgress(dm, downloadId)
                if (p != null) trySend(p)
                delay(PROGRESS_POLL_MS)
                timeoutMs += PROGRESS_POLL_MS
                if (timeoutMs >= DOWNLOAD_TIMEOUT_MS) {
                    Log.w(TAG, "download timed out after ${timeoutMs}ms, cancelling")
                    runCatching { context.unregisterReceiver(receiver) }
                    dm.remove(downloadId)
                    trySend(null)
                    close()
                    break
                }
            }

            awaitClose {
                downloadInFlight.set(false)
                runCatching { context.unregisterReceiver(receiver) }
            }
        }.flowOn(Dispatchers.IO)

    private fun queryProgress(dm: DownloadManager, id: Long): Int? {
        val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: return null
        return cursor.use { c ->
            if (!c.moveToFirst()) return@use null
            when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED -> {
                    val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done  = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    if (total <= 0L) 0 else ((done * 100L) / total).toInt().coerceIn(0, 99)
                }
                // STATUS_SUCCESSFUL / STATUS_FAILED / STATUS_PENDING:
                // don't emit — let the BroadcastReceiver close the channel.
                else -> null
            }
        }
    }

    private fun launchInstaller(context: Context, apkFile: File) {
        // ACTION_INSTALL_PACKAGE + FileProvider URI is the correct path on Android 8+
        // when REQUEST_INSTALL_PACKAGES is declared. ACTION_VIEW with a DM content://
        // URI is routed to the media browser on many OEM ROMs (MagicOS included) and
        // never reaches the PackageInstaller — "Ohne Scan installieren" is unreachable.
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            putExtra(Intent.EXTRA_RETURN_RESULT, false)
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
