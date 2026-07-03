package io.somi.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Checks GitHub Releases for a newer version of So-Mi.
 * Uses the public GitHub API — no auth token needed for public repos.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val RELEASES_URL =
        "https://api.github.com/repos/Labushuya/so-mi/releases/latest"

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    data class UpdateInfo(
        val latestVersion: String,
        val apkUrl: String,
        val releaseNotes: String,
        val isNewer: Boolean,
    )

    suspend fun check(currentVersionName: String): UpdateInfo? = withContext(Dispatchers.IO) {
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
            Log.i(TAG, "Latest: $tagName, current: $currentVersionName, newer: $isNewer")
            UpdateInfo(tagName, apkUrl, notes, isNewer)
        }.onFailure { Log.w(TAG, "Update check failed", it) }.getOrNull()
    }

    /** Returns true if latest > current using semantic versioning comparison. */
    private fun isVersionNewer(latest: String, current: String): Boolean {
        fun parts(v: String) = v.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val l = parts(latest)
        val c = parts(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }

    /** Opens the APK URL in the browser for manual download + install. */
    fun openInstallPage(context: Context, apkUrl: String) {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(apkUrl)).apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
