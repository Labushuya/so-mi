package io.somi.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Checks GitHub Releases for a newer version of So-Mi and handles the full
 * download + install flow via DownloadManager + ACTION_INSTALL_PACKAGE.
 *
 * Why DownloadManager instead of ACTION_VIEW:
 *  - GitHub APK URLs redirect. ACTION_VIEW in the browser triggers two
 *    download attempts (one for the redirect, one for the final URL) and
 *    leaves a dangling partial file every time.
 *  - DownloadManager follows redirects natively, shows system progress,
 *    and lands the file in the app's external files dir where FileProvider
 *    can serve it to the PackageInstaller — no browser required.
 *  - REQUEST_INSTALL_PACKAGES permission is declared in the manifest so the
 *    "Install without scan" prompt works (the button was dead without it).
 */
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

    data class UpdateInfo(
        val latestVersion: String,
        val apkUrl: String,
        val releaseNotes: String,
        val isNewer: Boolean,
        val isUpToDate: Boolean,
    )

    /** Auto-check on app start — always fresh, bypasses throttle. */
    suspend fun check(currentVersionName: String): UpdateInfo? =
        fetchLatest(currentVersionName).also {
            lastResult.set(it)
            lastCheckTime.set(System.currentTimeMillis())
        }

    /** Manual "Jetzt prüfen" button — throttled to one call per 30s. */
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
     * Downloads the APK via DownloadManager into the app's external files
     * dir, then launches ACTION_INSTALL_PACKAGE once the download completes.
     *
     * This avoids the double-download bug from using ACTION_VIEW + browser
     * (GitHub redirects confuse Chrome's download manager) and ensures the
     * "Install without scan" button is tappable (needs REQUEST_INSTALL_PACKAGES).
     */
    suspend fun downloadAndInstall(context: Context, apkUrl: String, versionName: String) {
        withContext(Dispatchers.IO) {
            val updatesDir = File(context.getExternalFilesDir(null), "updates").also { it.mkdirs() }
            val destFile = File(updatesDir, "so-mi-$versionName.apk")
            // Remove stale file from a previous failed attempt.
            if (destFile.exists()) destFile.delete()

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val req = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("So-Mi Update")
                setDescription("v$versionName wird heruntergeladen…")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destFile))
                setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or
                    DownloadManager.Request.NETWORK_MOBILE
                )
            }
            val downloadId = dm.enqueue(req)
            Log.i(TAG, "download enqueued id=$downloadId dest=${destFile.path}")

            // Wait for the download to complete (or time out after 2 min).
            val success = withTimeoutOrNull(DOWNLOAD_TIMEOUT_MS) {
                suspendCancellableCoroutine { cont ->
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(ctx: Context, intent: Intent) {
                            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                            if (id != downloadId) return
                            ctx.unregisterReceiver(this)
                            // Verify the download succeeded before resuming.
                            val query = DownloadManager.Query().setFilterById(downloadId)
                            val cursor = dm.query(query)
                            val ok = cursor?.use { c ->
                                c.moveToFirst() &&
                                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) ==
                                    DownloadManager.STATUS_SUCCESSFUL
                            } ?: false
                            cont.resume(ok)
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
                Log.w(TAG, "download timed out, cancelling")
                dm.remove(downloadId)
                false
            }

            if (success && destFile.exists()) {
                launchInstaller(context, destFile)
            } else {
                Log.e(TAG, "download failed or file missing after completion")
            }
        }
    }

    private fun launchInstaller(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.e(TAG, "launchInstaller failed", it) }
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
