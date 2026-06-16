package io.somi.tools.web

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SearxngClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(4, TimeUnit.SECONDS)
        .callTimeout(5, TimeUnit.SECONDS)
        .build()
    private val mirrors = listOf("https://searx.be", "https://searx.info", "https://search.bus-hit.me")

    suspend fun search(query: String, maxResults: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        for (mirror in mirrors) {
            val result = runCatching { doSearch(mirror, query, maxResults) }.getOrNull()
            if (!result.isNullOrEmpty()) return@withContext result
        }
        emptyList()
    }

    private fun doSearch(base: String, query: String, maxResults: Int): List<SearchResult> {
        val body = FormBody.Builder()
            .add("q", query)
            .add("format", "json")
            .add("engines", "google,duckduckgo,brave")
            .add("language", "de-DE")
            .build()
        val req = Request.Builder().url("$base/search").post(body).build()
        val response = http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            r.body?.string() ?: return emptyList()
        }
        val j = JSONObject(response)
        val arr = j.optJSONArray("results") ?: return emptyList()
        return (0 until minOf(arr.length(), maxResults)).map { i ->
            val item = arr.getJSONObject(i)
            SearchResult(
                title = item.optString("title", ""),
                url = item.optString("url", ""),
                snippet = item.optString("content", ""),
            )
        }
    }
}

data class SearchResult(val title: String, val url: String, val snippet: String)
