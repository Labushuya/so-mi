package io.somi.tools.weather

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class OpenMeteoClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(location: String, days: Int): String = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(location, "UTF-8")
        val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=de&format=json"
        val geoBody = get(geoUrl) ?: return@withContext "Ort '$location' nicht gefunden."
        val geoJson = JSONObject(geoBody)
        val results = geoJson.optJSONArray("results") ?: return@withContext "Ort '$location' nicht gefunden."
        if (results.length() == 0) return@withContext "Ort '$location' nicht gefunden."
        val loc = results.getJSONObject(0)
        val lat = loc.getDouble("latitude")
        val lon = loc.getDouble("longitude")
        val name = loc.optString("name", location)
        val cappedDays = days.coerceIn(1, 7)
        val weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&daily=temperature_2m_max,temperature_2m_min,weathercode" +
            "&current_weather=true&timezone=auto&forecast_days=$cappedDays"
        val weatherBody = get(weatherUrl) ?: return@withContext "Wetterdaten für $name nicht verfügbar."
        parseWeather(name, weatherBody, cappedDays)
    }

    private fun parseWeather(name: String, body: String, days: Int): String {
        val j = JSONObject(body)
        val current = j.optJSONObject("current_weather")
        val daily = j.optJSONObject("daily")
        val sb = StringBuilder()
        if (current != null) {
            val temp = current.optDouble("temperature", Double.NaN)
            val code = current.optInt("weathercode", 0)
            if (!temp.isNaN()) sb.append("Wetter $name: aktuell ${temp.toInt()}°C, ${wmoDesc(code)}.\n")
        }
        if (daily != null && days > 1) {
            val dates = daily.optJSONArray("time") ?: return sb.toString()
            val maxTemps = daily.optJSONArray("temperature_2m_max")
            val minTemps = daily.optJSONArray("temperature_2m_min")
            val codes = daily.optJSONArray("weathercode")
            sb.append("Vorhersage:\n")
            for (i in 0 until minOf(days, dates.length())) {
                val date = dates.getString(i)
                val max = maxTemps?.optDouble(i)?.toInt() ?: 0
                val min = minTemps?.optDouble(i)?.toInt() ?: 0
                val desc = wmoDesc(codes?.optInt(i) ?: 0)
                sb.append("- $date: $max°C / $min°C, $desc\n")
            }
        }
        return sb.toString().trim()
    }

    private fun wmoDesc(code: Int): String = when (code) {
        0 -> "sonnig"; 1, 2 -> "leicht bewölkt"; 3 -> "bedeckt"
        45, 48 -> "neblig"; 51, 53, 55 -> "Nieselregen"
        61, 63, 65 -> "Regen"; 71, 73, 75 -> "Schnee"
        80, 81, 82 -> "Schauer"; 95, 96, 99 -> "Gewitter"
        else -> "wechselhaft"
    }

    private fun get(url: String): String? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (r.isSuccessful) r.body?.string() else null
        }
    }.onFailure { Log.w("OpenMeteoClient", "HTTP error", it) }.getOrNull()
}
