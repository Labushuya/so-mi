package io.somi.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
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
import kotlin.coroutines.resume

object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL =
        "https://api.github.com/repos/Labushuya/so-mi/releases/latest"
    private const val CACHE_TTL_MS = 30_000L
    private const val DOWNLOAD_TIMEOUT_MS = 120_000L

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val lastCheckTime = AtomicLong(0L)
    private val lastResult = AtomicReference<UpdateInfo?>(null)

    // Prevents concurrent downloads from multiple taps.
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
     * Downloads the APK via DownloadManager and opens it with ACTION_VIEW using
     * the DownloadManager's own content:// URI — no FileProvider needed, works
     * on all OEM variants including MagicOS.
     *
     * Guarded by [downloadInFlight] so that rapid taps only start one download.
     * Returns false immediately if a download is already running.
     *
     * Caller should show a "Laden…" state while this suspends and reset it in finally.
     */
    suspend fun downloadAndInstall(context: Context, apkUrl: String, versionName: String): Boolean {
        // Debounce: reject concurrent calls
        if (!downloadInFlight.compareAndSet(false, true)) {
            Log.d(TAG, "download already in flight, ignoring tap")
            return false
        }
        return try {
            withContext(Dispatchers.IO) {
                val updatesDir = File(context.getExternalFilesDir(null), "updates")
                    .also { it.mkdirs() }
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
                    // Prevent DownloadManager from notifying before we open the installer
                    setMimeType("application/vnd.android.package-archive")
                }
                val downloadId = dm.enqueue(req)
                Log.i(TAG, "download enqueued id=$downloadId")

                val success = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                    suspendCancellableCoroutine { cont ->
                        val receiver = object : BroadcastReceiver() {
                            override fun onReceive(ctx: Context, intent: Intent) {
                                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                                if (id != downloadId) return
                                runCatching { ctx.unregisterReceiver(this) }
                                val cursor = dm.query(
                                    DownloadManager.Query().setFilterById(downloadId)
                                )
                                val ok = cursor?.use { c ->
                                    c.moveToFirst() &&
                                    c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                                        DownloadManager.STATUS_SUCCESSFUL
                                } ?: false
                                if (cont.isActive) cont.resume(ok)
                            }
                        }
                        context.registerReceiver(
                            receiver,
                            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                            Context.RECEIVER_NOT_EXPORTED,
                        )
                        cont.invokeOnCancellation {
                            runCatching { context.unregisterReceiver(receiver) }
                            dm.remove(downloadId)
                        }
                    }
                } ?: run {
                    Log.w(TAG, "download timed out")
                    dm.remove(downloadId)
                    false
                }

                if (success) {
                    // Use DownloadManager's own content:// URI — avoids FileProvider
                    // incompatibilities on HONOR/MagicOS and opens PackageInstaller directly.
                    val installerUri = dm.getUriForDownloadedFile(downloadId)
                    if (installerUri != null) {
                        launchInstaller(context, installerUri)
                    } else {
                        Log.e(TAG, "DownloadManager returned null URI for $downloadId")
                    }
                    true
                } else {
                    false
                }
            }
        } finally {
            downloadInFlight.set(false)
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
