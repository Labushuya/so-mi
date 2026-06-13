package io.somi.tools.weather

import kotlinx.serialization.Serializable

@Serializable
data class WeatherParams(val location: String, val days: Int = 1)
