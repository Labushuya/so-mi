package io.somi.ui.chat

/**
 * v0.33.0 — Central registry of all slash commands So-Mi understands.
 * Used by the Composer autocomplete popup and the ? help button.
 */
object SlashCommandRegistry {

    data class Command(
        val command: String,          // e.g. "/trigger_error"
        val description: String,      // shown in popup
        val category: String = "Allgemein",
    )

    val ALL: List<Command> = listOf(
        // Memory
        Command("/note", "Fakt direkt speichern ohne Triggerwort", "Erinnerungen"),
        Command("/merke", "Fakt direkt speichern (Deutsch)", "Erinnerungen"),
        Command("/remember", "Save a fact directly (English)", "Erinnerungen"),

        // Keywords
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
