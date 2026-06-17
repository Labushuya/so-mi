package io.somi.tools.registry

import io.somi.tools.model.ToolDefinition

object BuiltInToolDefinitions {

    val getWeather = ToolDefinition(
        id = "get_weather",
        description = "Aktuelles Wetter und Vorhersage für einen Ort abrufen",
        paramSchema = """{"type":"object","properties":{"location":{"type":"string"},"days":{"type":"integer","default":1}},"required":["location"]}""",
        regexPatterns = listOf(
            Regex("""wetter\s+(?:in\s+|für\s+|von\s+)?(\w[\w\s]{1,25})"""),
            Regex("""wie\s+warm\s+(?:ist|wird)\s+es"""),
            Regex("""regnet\s+es\s+(?:heute|morgen|gerade)?"""),
            Regex("""wird\s+es\s+(?:regnen|schneien|sonnig|warm|kalt|bewölkt)"""),
            Regex("""brauche\s+ich\s+(?:einen?\s+)?(?:schirm|regenschirm|jacke)"""),
            Regex("""wie\s+ist\s+(?:das\s+wetter|die\s+temperatur)"""),
        ),
        paramExtractor = { query ->
            val lower = query.lowercase()
            // Dynamic day-offset: understands "morgen", "übermorgen", "in N Tagen",
            // "am Wochenende", "nächsten Montag/Freitag", "nächste Woche"
            val inNDays = Regex("""in\s+(\d+)\s+tag""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val days = when {
                lower.contains("übermorgen") -> 3
                inNDays != null && inNDays in 1..7 -> inNDays
                lower.contains("am wochenende") || lower.contains("wochenende") -> {
                    val cal = java.util.Calendar.getInstance()
                    val today = cal.get(java.util.Calendar.DAY_OF_WEEK)
                    val sat = java.util.Calendar.SATURDAY
                    val d = ((sat - today + 7) % 7).let { if (it == 0) 7 else it }
                    d.coerceIn(1, 7)
                }
                lower.contains("nächsten montag") || lower.contains("nächsten mo") -> {
                    val cal = java.util.Calendar.getInstance()
                    val today = cal.get(java.util.Calendar.DAY_OF_WEEK)
                    val d = ((java.util.Calendar.MONDAY - today + 7) % 7).let { if (it == 0) 7 else it }
                    d.coerceIn(1, 7)
                }
                lower.contains("nächsten freitag") || lower.contains("nächsten fr") -> {
                    val cal = java.util.Calendar.getInstance()
                    val today = cal.get(java.util.Calendar.DAY_OF_WEEK)
                    val d = ((java.util.Calendar.FRIDAY - today + 7) % 7).let { if (it == 0) 7 else it }
                    d.coerceIn(1, 7)
                }
                lower.contains("nächste woche") -> 7
                lower.contains("diese woche") || (lower.contains("woche") && !lower.contains("wochenende")) -> 5
                lower.contains("morgen") -> 2
                else -> 1
            }
            // Strip time/day words before extracting location name
            val cleaned = lower
                .replace(Regex("""in\s+\d+\s+tagen?"""), " ")
                .replace(Regex("""\b(morgen|heute|übermorgen|nächste[ns]?\s+\w+|am\s+wochenende|wochenende|diese\s+woche|nächste\s+woche|aktuell|gerade|jetzt|wird|wie|wird es|nächsten)\b"""), " ")
                .replace(Regex("""\s{2,}"""), " ").trim()
            val m = Regex("""wetter\s+(?:in\s+|für\s+|von\s+)?(\w[\w\s]{1,25})""").find(cleaned)
            val loc = m?.groupValues?.getOrNull(1)?.trim().orEmpty()
            mapOf("location" to loc, "days" to days)
        },
    )

    val searchWeb = ToolDefinition(
        id = "search_web",
        description = "Im Internet nach aktuellen Informationen suchen",
        paramSchema = """{"type":"object","properties":{"query":{"type":"string"},"max_results":{"type":"integer","default":5}},"required":["query"]}""",
        regexPatterns = listOf(
            Regex("""@web\b"""),
            Regex("""such[e]?\s+(?:im\s+internet|online|im\s+web|im\s+netz)"""),
            Regex("""such\s+online\s+nach"""),
            Regex("""google\s+(?:mal|nach|für\s+mich)"""),
            Regex("""aktuelle[sn]?\s+(?:news|nachrichten|infos?)\s+(?:zu|über)"""),
        ),
        paramExtractor = { query ->
            val clean = query
                .replace(Regex("@web\\b"), "")
                .replace(Regex("such[e]?\\s+(?:im\\s+internet|online|im\\s+web|im\\s+netz)\\s+(?:nach\\s+)?"), "")
                .replace(Regex("such\\s+online\\s+nach\\s*"), "")
                .replace(Regex("google\\s+(?:mal|nach|für\\s+mich)\\s*"), "")
                .replace(Regex("aktuelle[sn]?\\s+(?:news|nachrichten|infos?)\\s+(?:zu|über)\\s*"), "")
                .trim()
            mapOf("query" to clean.ifBlank { query }, "max_results" to 5)
        },
    )

    val searchMemory = ToolDefinition(
        id = "search_memory",
        description = "In gespeicherten Erinnerungen und Notizen suchen",
        paramSchema = """{"type":"object","properties":{"query":{"type":"string"},"k":{"type":"integer","default":10}},"required":["query"]}""",
        regexPatterns = listOf(
            Regex("""erinnerst\s+du\s+(?:dich|dir)\s+(?:noch\s+)?(?:an|dass|was)"""),
            Regex("""was\s+weißt\s+du\s+(?:über|von|zu)\s+\w"""),
            Regex("""habe\s+ich\s+(?:dir\s+)?(?:mal\s+)?(?:gesagt|erzählt)"""),
            Regex("""such\s+(?:in\s+)?(?:meinen?\s+)?(?:erinnerungen?|notizen?|gedächtnis)"""),
            Regex("""@memory\b"""),
            Regex("""@erinnerung\b"""),
        ),
        paramExtractor = { query ->
            val clean = query.replace(Regex("@memory|@erinnerung"), "").trim()
            mapOf("query" to clean.ifBlank { query }, "k" to 10)
        },
    )
}
