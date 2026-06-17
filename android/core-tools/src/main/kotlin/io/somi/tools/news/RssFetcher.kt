package io.somi.tools.news

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class NewsItem(val title: String, val source: String)

class RssFetcher {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val feeds = listOf(
        "Tagesschau" to "https://www.tagesschau.de/xml/rss2",
        "Spiegel" to "https://www.spiegel.de/schlagzeilen/tops/index.rss",
        "Heise" to "https://www.heise.de/rss/heise-top-atom.xml",
    )

    suspend fun fetch(maxPerFeed: Int = 3): List<NewsItem> = withContext(Dispatchers.IO) {
        feeds.flatMap { (source, url) ->
            runCatching { fetchFeed(url, source, maxPerFeed) }
                .onFailure { Log.w("RssFetcher", "Feed $source failed", it) }
                .getOrDefault(emptyList())
        }
    }

    private fun fetchFeed(url: String, source: String, max: Int): List<NewsItem> {
        val body = http.newCall(Request.Builder().url(url).build()).execute()
            .use { r -> if (r.isSuccessful) r.body?.string() else null } ?: return emptyList()
        val titleRegex = Regex("<title><!\\[CDATA\\[(.+?)]]></title>|<title>([^<]+)</title>")
        return titleRegex.findAll(body)
            .map { m -> m.groupValues[1].ifBlank { m.groupValues[2] }.trim() }
            .filter { t -> t.isNotBlank() && t.length > 10 && !t.contains("RSS") }
            .take(max).map { t -> NewsItem(t, source) }.toList()
    }
}
