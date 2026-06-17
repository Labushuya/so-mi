package io.somi.tools.exchange

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ExchangeRateClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun convert(from: String, to: String, amount: Double): String = withContext(Dispatchers.IO) {
        val url = "https://api.exchangerate-api.com/v4/latest/${from.uppercase()}"
        val body = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.onFailure { Log.w("ExchangeRateClient", "HTTP error", it) }.getOrNull()
            ?: return@withContext "Wechselkurs nicht verfügbar."
        runCatching {
            val json = JSONObject(body)
            val rates = json.getJSONObject("rates")
            val toUpper = to.uppercase()
            if (!rates.has(toUpper)) return@runCatching "Währung '$to' nicht gefunden."
            val rate = rates.getDouble(toUpper)
            val result = amount * rate
            "${String.format("%.2f", amount)} ${from.uppercase()} = ${String.format("%.2f", result)} ${to.uppercase()} (Kurs: $rate)"
        }.getOrElse { "Fehler beim Umrechnen." }
    }
}
