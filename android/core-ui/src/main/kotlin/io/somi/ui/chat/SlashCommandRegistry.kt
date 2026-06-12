package io.somi.ui.chat

/**
 * v0.33.0 — Central registry of all slash commands So-Mi understands.
 * v0.39.0 — Chat commands added (/search, /clear, /rename, /archive)
 */
object SlashCommandRegistry {

    data class Command(
        val command: String,
        val description: String,
        val category: String = "Allgemein",
    )

    val ALL: List<Command> = listOf(
        // Memory
        Command("/note", "Fakt direkt speichern ohne Triggerwort", "Erinnerungen"),
        Command("/merke", "Fakt direkt speichern (Deutsch)", "Erinnerungen"),
        Command("/remember", "Save a fact directly (English)", "Erinnerungen"),

        // Chat management
        Command("/search", "Nachrichten in diesem Gespräch suchen", "Gespräch"),
        Command("/clear", "Dieses Gespräch leeren (Nachrichten löschen)", "Gespräch"),
        Command("/rename", "Dieses Gespräch umbenennen", "Gespräch"),
        Command("/archive", "Dieses Gespräch archivieren (ausblenden)", "Gespräch"),

        // Test
        Command("/trigger_error", "Test-Band: Fehler (rot)", "Test"),
        Command("/trigger_warning", "Test-Band: Warnung (gelb)", "Test"),
        Command("/trigger_success", "Test-Band: Erfolg (grün)", "Test"),
        Command("/trigger_info", "Test-Band: Hinweis (grau)", "Test"),
        Command("/trigger_chatband", "Test-Band: Standard", "Test"),
    )

    fun matching(input: String): List<Command> {
        if (input.isBlank() || !input.startsWith("/")) return ALL
        val q = input.lowercase()
        return ALL.filter { it.command.startsWith(q) || it.description.lowercase().contains(q.removePrefix("/")) }
    }
}
