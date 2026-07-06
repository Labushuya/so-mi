package io.somi.data

/**
 * Static catalog of KIWIX ZIM files So-Mi can download.
 *
 * SHA-256 verified live against https://download.kiwix.org/zim/wiktionary/
 * wiktionary_de_all_nopic_2026-04.zim.sha256 on 2026-07-06.
 */
data class ZimManifest(
    val id: String,
    val displayName: String,
    val description: String,
    val filename: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

object ZimCatalog {

    val WIKTIONARY_DE = ZimManifest(
        id = "wiktionary_de_2026_04",
        displayName = "Wiktionary Deutsch",
        description = "Deutsches Wörterbuch — Definitionen, Grammatik, Etymologie. Offline-Lexikon für So-Mi.",
        filename = "wiktionary_de_all_nopic_2026-04.zim",
        url = "https://download.kiwix.org/zim/wiktionary/wiktionary_de_all_nopic_2026-04.zim",
        sizeBytes = 1_290_000_000L,
        sha256 = "947e4f1754756dca68a570f4e02782d47f3acc5d555dd90755e9f251b42db8de",
    )

    val ALL: List<ZimManifest> = listOf(WIKTIONARY_DE)

    fun byId(id: String): ZimManifest? = ALL.firstOrNull { it.id == id }
}
