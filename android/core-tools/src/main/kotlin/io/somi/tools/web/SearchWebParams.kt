package io.somi.tools.web

import kotlinx.serialization.Serializable

@Serializable
data class SearchWebParams(val query: String, val maxResults: Int = 5)
