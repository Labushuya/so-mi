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
        val primary = runCatching { fetchPrimary(from, to, amount) }.getOrNull()
        if (primary != null) return@withContext primary
        Log.w("ExchangeRateClient", "Primary API failed, trying Frankfurter fallback")
        val fallback = runCatching { fetchFrankfurter(from, to, amount) }.getOrNull()
        if (fallback != null) return@withContext "[Frankfurter.app] $fallback"
        "Wechselkurs nicht verfügbar (beide APIs nicht erreichbar)."
    }

    private fun fetchPrimary(from: String, to: String, amount: Double): String? {
        val url = "https://api.exchangerate-api.com/v4/latest/${from.uppercase()}"
        val body = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.onFailure { Log.w("ExchangeRateClient", "Primary HTTP error", it) }.getOrNull()
            ?: return null
        return runCatching {
            val json = JSONObject(body)
            val rates = json.getJSONObject("rates")
            val toUpper = to.uppercase()
            if (!rates.has(toUpper)) return null
            val rate = rates.getDouble(toUpper)
            val result = amount * rate
            "${String.format("%.2f", amount)} ${from.uppercase()} = ${String.format("%.2f", result)} ${to.uppercase()} (Kurs: $rate)"
        }.getOrNull()
    }

    private fun fetchFrankfurter(from: String, to: String, amount: Double): String? {
        val url = "https://api.frankfurter.app/latest?from=${from.uppercase()}&to=${to.uppercase()}&amount=$amount"
        val body = runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            }
        }.onFailure { Log.w("ExchangeRateClient", "Frankfurter HTTP error", it) }.getOrNull()
            ?: return null
        return runCatching {
            val json = JSONObject(body)
            val rates = json.getJSONObject("rates")
            val toUpper = to.uppercase()
            if (!rates.has(toUpper)) {
                // Frankfurter only supports EUR as base; if FROM is not EUR the API may reject it
                Log.w("ExchangeRateClient", "Frankfurter: target currency '$toUpper' not in response — FROM may not be EUR-based")
                return null
            }
            val convertedAmount = rates.getDouble(toUpper)
            val rate = convertedAmount / amount
            "${String.format("%.2f", amount)} ${from.uppercase()} = ${String.format("%.2f", convertedAmount)} ${to.uppercase()} (Kurs: ${String.format("%.4f", rate)})"
        }.getOrNull()
    }
}
