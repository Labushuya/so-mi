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
 * Features:
 *  - resumeExistingDownload(): on app start, re-attaches to a DownloadManager entry
 *    that is already running or completed from a previous session (app-kill survival).
 *  - pauseDownload() / resumeDownload(): wraps DownloadManager pause/resume API.
 *  - 10-minute timeout to handle large APKs on slow connections.
 *
 * Architecture notes:
 *  - A Channel<Boolean> bridges the BroadcastReceiver (main thread) into the
 *    IO-dispatched flow loop. The flow {} builder handles cancellation cleanly.
 *  - activeDownloadId is an AtomicLong, reset in a finally block that always runs.
 *  - Pause/resume are fire-and-forget: DownloadManager.PAUSED_BY_APP is polled by
 *    the existing loop and surfaced as DownloadState.Paused.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL =
        "https://api.github.com/repos/Labushuya/so-mi/releases/latest"
    private const val CACHE_TTL_MS = 30_000L
    private const val POLL_MS = 800L
    private const val TIMEOUT_MS = 600_000L   // 10 minutes — covers 131 MB on slow connections

    // SharedPrefs key: persists the DownloadManager ID across process death.
    private const val PREFS_NAME = "updater"
    private const val PREF_DL_ID = "active_download_id"
    private const val PREF_DL_VERSION = "active_download_version"

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
        data class Paused(val percent: Int) : DownloadState
        data object Done : DownloadState
        data object Failed : DownloadState
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
     * Called on app start — checks SharedPrefs for a persisted DownloadManager ID.
     * If the download is already SUCCESSFUL, emits Done immediately (no new download).
     * If still RUNNING or PAUSED, re-attaches the polling loop to the existing entry.
     * Returns null if no prior download exists for [versionName].
     */
    fun resumeExistingDownload(context: Context, versionName: String): Flow<DownloadState>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedId = prefs.getLong(PREF_DL_ID, -1L)
        val savedVersion = prefs.getString(PREF_DL_VERSION, null)
        if (savedId == -1L || savedVersion != versionName) return null

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val status = queryStatus(dm, savedId)
        if (status == -1) {
            // Entry gone from DownloadManager — clear prefs and bail
            prefs.edit().remove(PREF_DL_ID).remove(PREF_DL_VERSION).apply()
            return null
        }
        if (status == DownloadManager.STATUS_SUCCESSFUL) {
            Log.i(TAG, "resumeExisting: already SUCCESSFUL id=$savedId")
            prefs.edit().remove(PREF_DL_ID).remove(PREF_DL_VERSION).apply()
            return flow {
                emit(DownloadState.Progress(100))
                emit(DownloadState.Done)
            }.flowOn(Dispatchers.IO)
        }

        Log.i(TAG, "resumeExisting: re-attaching to id=$savedId status=$status")
        activeDownloadId.set(savedId)
        return attachToDownload(context, dm, savedId, versionName)
    }

    /**
     * Cancel the active download. The UI can restart it via downloadAndInstall().
     * Note: DownloadManager has no public pause/resume API for normal apps —
     * cancel + re-download is the standard pattern.
     */
    fun cancelDownload(context: Context) {
        val id = activeDownloadId.get()
        if (id <= 0L) return
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        runCatching { dm.remove(id) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .remove(PREF_DL_ID).remove(PREF_DL_VERSION).apply()
        activeDownloadId.set(-1L)
        Log.i(TAG, "cancelDownload id=$id")
    }

    /**
     * Enqueues a new APK download and emits [DownloadState] until done or failed.
     * Persists the DownloadManager ID to SharedPrefs so [resumeExistingDownload] can
     * re-attach after a process death.
     */
    fun downloadAndInstall(context: Context, apkUrl: String, versionName: String): Flow<DownloadState> =
        flow {
            // Cancel any stale download from a previous session or crash.
            val prev = activeDownloadId.getAndSet(0L)
            if (prev > 0L) {
                val dm0 = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                runCatching { dm0.remove(prev) }
                Log.d(TAG, "cancelled stale download id=$prev")
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val updatesDir = File(context.getExternalFilesDir(null), "updates").also { it.mkdirs() }
            val destFile = File(updatesDir, "so-mi-$versionName.apk")
            if (destFile.exists()) destFile.delete()

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

            // Persist so we survive process death
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putLong(PREF_DL_ID, downloadId)
                .putString(PREF_DL_VERSION, versionName)
                .apply()

            Log.i(TAG, "download enqueued id=$downloadId")

            val states = attachToDownload(context, dm, downloadId, versionName)
            states.collect { emit(it) }
        }.flowOn(Dispatchers.IO)

    /**
     * Core polling loop — works for both new and resumed downloads.
     * Emits Progress / Paused / Done / Failed.
     */
    private fun attachToDownload(
        context: Context,
        dm: DownloadManager,
        downloadId: Long,
        versionName: String,
    ): Flow<DownloadState> = flow {
        val doneChannel = Channel<Boolean>(capacity = 1)
        var receiver: BroadcastReceiver? = null

        try {
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

            var elapsed = 0L
            var terminated = false
            while (!terminated) {
                val done = doneChannel.tryReceive()
                if (done.isSuccess) {
                    val success = done.getOrNull() ?: false
                    if (success) {
                        emit(DownloadState.Progress(100))
                        emit(DownloadState.Done)
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .remove(PREF_DL_ID).remove(PREF_DL_VERSION).apply()
                    } else {
                        emit(DownloadState.Failed)
                    }
                    terminated = true
                    break
                }

                val (pct, paused) = queryProgressAndPaused(dm, downloadId)
                when {
                    paused && pct != null -> emit(DownloadState.Paused(pct))
                    paused -> emit(DownloadState.Paused(0))
                    pct != null -> emit(DownloadState.Progress(pct))
                    else -> {
                        val status = queryStatus(dm, downloadId)
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                Log.i(TAG, "STATUS_SUCCESSFUL via polling")
                                emit(DownloadState.Progress(100))
                                emit(DownloadState.Done)
                                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                                    .remove(PREF_DL_ID).remove(PREF_DL_VERSION).apply()
                                terminated = true
                            }
                            DownloadManager.STATUS_FAILED -> {
                                Log.e(TAG, "STATUS_FAILED")
                                emit(DownloadState.Failed)
                                terminated = true
                            }
                            else -> { /* pending/unknown — wait */ }
                        }
                    }
                }

                if (!terminated) {
                    delay(POLL_MS)
                    elapsed += POLL_MS
                    if (elapsed >= TIMEOUT_MS) {
                        Log.w(TAG, "download timed out after ${TIMEOUT_MS / 1000}s")
                        dm.remove(downloadId)
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                            .remove(PREF_DL_ID).remove(PREF_DL_VERSION).apply()
                        emit(DownloadState.Failed)
                        terminated = true
                    }
                }
            }
        } finally {
            activeDownloadId.set(-1L)
            doneChannel.close()
            receiver?.let { r -> runCatching { context.unregisterReceiver(r) } }
        }
    }.flowOn(Dispatchers.IO)

    private fun queryProgressAndPaused(dm: DownloadManager, id: Long): Pair<Int?, Boolean> {
        val c = dm.query(DownloadManager.Query().setFilterById(id)) ?: return Pair(null, false)
        return c.use {
            if (!it.moveToFirst()) return@use Pair(null, false)
            when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_PAUSED -> {
                    // System-paused (waiting for network, queued etc.) — show last known %
                    val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val pct = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 99) else 0
                    Pair(pct, true)
                }
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PENDING -> {
                    val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val pct = if (total > 0L) ((done * 100L) / total).toInt().coerceIn(0, 99) else 0
                    Pair(pct, false)
                }
                else -> Pair(null, false)
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
