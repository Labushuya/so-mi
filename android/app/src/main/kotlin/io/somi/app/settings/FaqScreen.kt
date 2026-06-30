package io.somi.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.somi.app.LocalSongbirdColors
import io.somi.app.components.SongbirdTopBar

data class FaqEntry(
    val question: String,
    val answer: String,
    val category: String,
)

val FAQ_ENTRIES = listOf(
    // Commands & Tools
    FaqEntry("Commands & Tool-Übersicht",
        "Erinnerungen speichern:\n• /merke Ich bin SRE bei Delos Cloud\n• /merke Ich liebe Kaffee #Vorlieben kaffee,getränke\n• merk dir: Ich spiele Gitarre #Hobby gitarre,musik\n• Das ist wichtig: [Fakt]\n• FYI: [Fakt]\n→ Mit #Kategorie landet der Fakt dort. Gibt es die Kategorie nicht, erstellt So-Mi sie automatisch. kw1,kw2 danach sind Keywords.\n\nTools mit @-Commands:\n• @wetter Berlin morgen\n• @alarm 15min Fenster schließen\n• @kurs 250 EUR in USD\n• @web KI-Regulierung 2026\n• @kalender diese Woche\n• @termin Meeting morgen 14 Uhr\n• @news (aktuelle Nachrichten)\n• TL;DR: [langer Text]\n\nGespräch verwalten:\n• /suche [Begriff] — Nachrichten durchsuchen\n• /umbenennen — Gespräch umbenennen\n• /archivieren — Gespräch ausblenden\n• /leeren — Alle Nachrichten löschen\n\nTipp: / oder @ tippen öffnet das Command-Popup mit allen verfügbaren Befehlen und Syntax-Vorlagen.",
        "Commands"),
    // Erinnerungen
    FaqEntry("Wie sage ich So-Mi, dass sie sich etwas merken soll?",
        "Sag einfach 'Merke dir, ...' oder 'Vergiss nicht, ...' oder tippe /note. So-Mi speichert den Fakt automatisch in der richtigen Kategorie.",
        "Erinnerungen"),
    FaqEntry("Wo werden meine Erinnerungen gespeichert?",
        "In SoMi/memory/*.md auf deinem Gerät (externalFilesDir). Die Dateien sind für dich lesbar. Einstellungen → Daten → Dateien anzeigen zeigt den genauen Pfad.",
        "Erinnerungen"),
    FaqEntry("Kann ich Erinnerungen löschen oder verschieben?",
        "Ja. Einstellungen → So-Mi → Lernen → Erinnerungen → Kategorie aufklappen → ✕ zum Löschen, ↗ zum Verschieben, ✎ zum Bearbeiten.",
        "Erinnerungen"),
    FaqEntry("Was ist eine eigene Kategorie und wie lege ich sie an?",
        "Eigene Kategorien sind frei benennbare Buckets für deine Erinnerungen (z.B. '🍕 Essen', 'Beruf & Job'). Erinnerungen → '+ Kategorie'. Mit Keywords kannst du steuern welche Fakten automatisch dahin wandern.",
        "Erinnerungen"),
    FaqEntry("Was sind Keywords für Kategorien?",
        "Keywords helfen So-Mi zu erkennen, in welche eigene Kategorie ein Fakt gehört. Wenn du 'engineer' als Keyword für 'Beruf & Job' hast, landet 'Ich bin Site Reliability Engineer' dort automatisch.",
        "Erinnerungen"),
    // Daten & Backup
    FaqEntry("Was löscht den Embedder und was nicht?",
        "Der Embedder (model.onnx + tokenizer.json) enthält KEINE Nutzerdaten. Löschen und neu installieren ist sicher.\n\nKritische Nutzerdaten:\n• SoMi/memory/ — Erinnerungen\n• SoMi/soul/soul.md — Persönlichkeit\n• SoMi/db/somi.db — Chat-Verlauf\n\nUncritical (wiederherstellbar):\n• SoMi/llm/ — LLM-Modelle (neu downloaden)\n• Embedder — neu downloaden",
        "Daten & Backup"),
    FaqEntry("Was sichert das Backup und was nicht?",
        "Das Backup (Einstellungen → Daten → Backup erstellen) sichert: memory/, soul/, settings/. Es sichert NICHT: LLM-Modelle (~4-9 GB), Embedder (~470 MB), Chat-Verlauf (db/). Chat-Verlauf-Backup kommt in einer späteren Version.",
        "Daten & Backup"),
    FaqEntry("Wie stelle ich ein Backup wieder her?",
        "Einstellungen → Anzeige & Daten → 'Backup importieren' → ZIP-Datei aus dem Datei-Manager wählen. Die Dateien werden wiederhergestellt ohne die App neu zu installieren.",
        "Daten & Backup"),
    // LLM & Modelle
    FaqEntry("Welches Modell ist das beste für mein Gerät?",
        "Die Ampel im LLM-Katalog zeigt es: Grün = problemlos, Gelb = knapp aber machbar, Rot = wahrscheinlich zu wenig RAM. Für den HONOR Magic V2 (16 GB) ist 7B grün, 12B gelb, 14B nicht empfohlen.",
        "LLM & Modelle"),
    FaqEntry("Was passiert wenn ich ein zu großes LLM lade?",
        "So-Mi friert beim Ladebildschirm ein und der Prozess wird vom System beendet. Beim Neustart lädt sie automatisch das letzte funktionierende Modell (i.d.R. 7B).",
        "LLM & Modelle"),
    FaqEntry("Wie wechsle ich das Modell?",
        "Einstellungen → Modelle & Abhängigkeiten → 'Anderes LLM laden' → Modell wählen → 'Herunterladen' oder 'Aktivieren' wenn es schon installiert ist.",
        "LLM & Modelle"),
    // Slash Commands
    FaqEntry("Was sind Slash-Commands?",
        "Befehle die du direkt im Chat eingeben kannst ohne Trigger-Wort. Tippe / im Eingabefeld oder tippe auf den /-Button für eine vollständige Liste.",
        "Slash-Commands"),
    FaqEntry("Welche Slash-Commands gibt es?",
        "/note — Fakt direkt speichern\n/merke — Fakt speichern (Deutsch)\n/remember — Fakt speichern (English)\n/trigger_error — Test-Band (rot)\n/trigger_warning — Test-Band (gelb)\n/trigger_success — Test-Band (grün)\n/trigger_info — Test-Band (grau)",
        "Slash-Commands"),
    // System
    FaqEntry("Was bedeutet das gelbe Band 'Gedächtnis-Modell fehlt'?",
        "Das Embedder-Modell (~470 MB) ist noch nicht heruntergeladen. So-Mi kann sich noch nichts merken. Einstellungen → Modelle & Abhängigkeiten → Gedächtnis-Modell (Embedder) → 'Erneut laden'.",
        "System"),
    FaqEntry("Warum antwortet So-Mi manchmal ohne Kontext?",
        "So-Mi nutzt deine gespeicherten Erinnerungen als Kontext. Wenn noch keine gespeichert sind oder der Embedder fehlt, antwortet sie allgemein. Je mehr du ihr sagst ('Merke dir, ich heiße ...'), desto besser wird sie.",
        "System"),
)

@Composable
fun FaqScreen(onBack: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    var search by remember { mutableStateOf("") }
    var expandedQuestion by remember { mutableStateOf<String?>(null) }

    val filtered = remember(search) {
        if (search.isBlank()) FAQ_ENTRIES
        else FAQ_ENTRIES.filter { e ->
            e.question.contains(search, ignoreCase = true) ||
            e.answer.contains(search, ignoreCase = true) ||
            e.category.contains(search, ignoreCase = true)
        }
    }
    val grouped = filtered.groupBy { it.category }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(songbird.obsidian)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(16.dp),
    ) {
        SongbirdTopBar(title = "Häufige Fragen", onBack = onBack)
        Spacer(Modifier.height(8.dp))

        // Search
        BasicTextField(
            value = search,
            onValueChange = { search = it },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = songbird.bone),
            cursorBrush = SolidColor(songbird.crimson),
            decorationBox = { inner ->
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(songbird.aiBubble, RoundedCornerShape(8.dp))
                        .border(1.dp, songbird.bubbleBorder, RoundedCornerShape(8.dp))
                        .padding(10.dp),
                ) {
                    if (search.isEmpty()) Text("Frage suchen…", color = songbird.glass, style = MaterialTheme.typography.bodyMedium)
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            grouped.forEach { (category, entries) ->
                item {
                    Text(
                        category,
                        color = songbird.crimson,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                }
                items(entries, key = { it.question }) { entry ->
                    FaqItem(
                        entry = entry,
                        expanded = expandedQuestion == entry.question,
                        onToggle = { expandedQuestion = if (expandedQuestion == entry.question) null else entry.question },
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Text("Keine Ergebnisse für \"$search\".", color = songbird.glass, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp))
                }
            }
        }
    }
}

@Composable
private fun FaqItem(entry: FaqEntry, expanded: Boolean, onToggle: () -> Unit) {
    val songbird = LocalSongbirdColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(songbird.aiBubble, RoundedCornerShape(8.dp))
            .border(1.dp, songbird.bubbleBorder, RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onToggle() }.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                entry.question,
                color = songbird.bone,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Text(if (expanded) "▲" else "▼", color = songbird.glass, style = MaterialTheme.typography.labelSmall)
        }
        if (expanded) {
            HorizontalDivider(color = songbird.bubbleBorder)
            Text(
                entry.answer,
                color = songbird.glass,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
