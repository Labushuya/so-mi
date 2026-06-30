package io.somi.ui.chat

object SlashCommandRegistry {

    data class Command(
        val command: String,
        val description: String,
        val syntax: String = command,
        val placeholder: String = "",
        val example: String = "",
        val category: String = "Allgemein",
    )

    // @ Tool commands (dokumentarisch, für Popup)
    val AT_COMMANDS: List<Command> = listOf(
        Command("@web", "Im Internet suchen", "@web [Anfrage]", "Anfrage eingeben...", "@web KI-Regulierung EU 2026", "@ Tools"),
        Command("@wetter", "Wetter abrufen", "@wetter [Ort]", "Ort eingeben...", "@wetter Berlin morgen", "@ Tools"),
        Command("@erinnerung", "Erinnerungen suchen", "@erinnerung [Begriff]", "Suchbegriff...", "@erinnerung Arbeit", "@ Tools"),
        Command("@notizen", "Notizen suchen", "@notizen [Begriff]", "Suchbegriff optional...", "@notizen", "@ Tools"),
        Command("@alarm", "Alarm setzen", "@alarm [Zeit] [Text]", "z.B. 10min Fenster zu", "@alarm 15min Termin gleich", "@ Tools"),
        Command("@kurs", "Wechselkurs", "@kurs [Anfrage]", "z.B. 100 EUR in USD", "@kurs 250 EUR in USD", "@ Tools"),
        Command("@news", "Nachrichten-Briefing", "@news", "", "@news", "@ Tools"),
        Command("@kalender", "Termine anzeigen", "@kalender [Zeitraum]", "z.B. diese Woche", "@kalender morgen", "@ Tools"),
        Command("@termin", "Termin erstellen", "@termin [Titel] [Zeit]", "z.B. Meeting morgen 14 Uhr", "@termin Arzt morgen 10 Uhr", "@ Tools"),
        Command("@zusammenfassung", "Text zusammenfassen", "@zusammenfassung [Text]", "Text eingeben...", "TL;DR: [Text]", "@ Tools"),
    )

    val ALL: List<Command> = listOf(
        // Erinnerungen
        Command("/merke", "Fakt oder Notiz speichern", "/merke [fakt] #Kategorie kw1,kw2", "Fakt eingeben... #Kategorie optional", "/merke Ich bin SRE #Arbeit cloud,sre", "Erinnerungen"),
        Command("/note", "Save a fact (English)", "/note [fact] #Category kw1,kw2", "Enter fact... #Category optional", "/note I am an SRE #Work cloud", "Erinnerungen"),
        // Gespräch
        Command("/suche", "Nachrichten in diesem Gespräch suchen", "/suche [Begriff]", "Suchbegriff eingeben...", "/suche Wetter", "Gespräch"),
        Command("/search", "Search messages", "/search [term]", "Search term...", "", "Gespräch"),
        Command("/umbenennen", "Gespräch umbenennen", "/umbenennen", "", "", "Gespräch"),
        Command("/rename", "Rename conversation", "/rename", "", "", "Gespräch"),
        Command("/archivieren", "Gespräch ausblenden", "/archivieren", "", "", "Gespräch"),
        Command("/archive", "Archive conversation", "/archive", "", "", "Gespräch"),
        Command("/leeren", "Nachrichten löschen", "/leeren", "", "", "Gespräch"),
        Command("/clear", "Clear messages", "/clear", "", "", "Gespräch"),
        // Test (versteckt)
        Command("/trigger_error", "Test-Band: Fehler (rot)", "/trigger_error", "", "", "Test"),
        Command("/trigger_warning", "Test-Band: Warnung (gelb)", "/trigger_warning", "", "", "Test"),
        Command("/trigger_success", "Test-Band: Erfolg (grün)", "/trigger_success", "", "", "Test"),
        Command("/trigger_info", "Test-Band: Hinweis (grau)", "/trigger_info", "", "", "Test"),
        Command("/trigger_chatband", "Test-Band: Standard", "/trigger_chatband", "", "", "Test"),
    )

    fun matching(input: String): List<Command> {
        if (input.isBlank()) return ALL + AT_COMMANDS
        if (input.startsWith("@")) {
            val q = input.lowercase()
            return AT_COMMANDS.filter { it.command.startsWith(q) || it.description.lowercase().contains(q.removePrefix("@")) }
        }
        if (!input.startsWith("/")) return ALL + AT_COMMANDS
        val q = input.lowercase()
        return ALL.filter { it.command.startsWith(q) || it.description.lowercase().contains(q.removePrefix("/")) }
    }
}
