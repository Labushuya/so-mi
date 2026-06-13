package io.somi.tools.memory

import kotlinx.serialization.Serializable

@Serializable
data class SearchMemoryParams(val query: String, val k: Int = 10)
