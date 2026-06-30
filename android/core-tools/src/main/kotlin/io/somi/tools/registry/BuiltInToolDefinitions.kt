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
            Regex("""wie\s+ist\s+das\s+wetter"""),
            Regex("""soll\s+ich\s+(?:einen?\s+)?(?:schirm|regenschirm|jacke|mantel)\s+mitnehmen"""),
            Regex("""wie\s+kalt\s+ist\s+es\s+(?:gerade|heute|draußen)?"""),
            Regex("""was\s+für\s+wetter\s+(?:haben\s+wir|ist\s+es)"""),
            Regex("""wird\s+es\s+heute\s+(?:noch\s+)?regnen"""),
            Regex("""ist\s+es\s+warm\s+genug"""),
            Regex("""wettervorhersage\s+(?:für|in)"""),
            Regex("""@wetter"""),
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
            Regex("""schau\s+(?:mal\s+)?nach"""),
            Regex("""find[e]?\s+heraus\s+(?:ob|was|wie|warum)"""),
            Regex("""recherchier[e]?"""),
            Regex("""was\s+(?:findet|weiß)\s+man\s+(?:im\s+netz\s+)?über"""),
            Regex("""was\s+(?:ist|sind)\s+(?:aktuelle[sn]?\s+)?infos?\s+(?:zu|über|von)"""),
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
            Regex("""weißt\s+du\s+noch"""),
            Regex("""kennst\s+du\s+mein[en]?"""),
            Regex("""was\s+hab[e]?\s+ich\s+dir\s+(?:mal\s+)?erzählt"""),
            Regex("""hast\s+du\s+notiert\s+(?:dass|das)"""),
            Regex("""sag\s+mir\s+was\s+du\s+(?:über\s+mich\s+)?weißt"""),
        ),
        paramExtractor = { query ->
            val clean = query.replace(Regex("@memory|@erinnerung"), "").trim()
            mapOf("query" to clean.ifBlank { query }, "k" to 10)
        },
    )

    val setAlarm = ToolDefinition(
        id = "set_alarm",
        description = "Einen Alarm oder eine Benachrichtigung für einen späteren Zeitpunkt setzen",
        paramSchema = """{"type":"object","properties":{"text":{"type":"string"},"delay_minutes":{"type":"integer","default":30}},"required":["text"]}""",
        regexPatterns = listOf(
            Regex("""erinner(?:e|)\s+mich\s+(?:daran\s+)?(?:dass\s+|zu\s+)?"""),
            Regex("""stell(?:e|)\s+(?:mir\s+)?(?:einen?\s+)?(?:alarm|timer|wecker)"""),
            Regex("""benachrichtig(?:e|)\s+mich"""),
            Regex("""@alarm\b"""),
            Regex("""@reminder\b"""),
            Regex("""weck(?:e|)\s+mich"""),
            Regex("""weck\s+mich\s+(?:bitte\s+)?(?:in|um)"""),
            Regex("""timer\s+(?:für\s+)?\d"""),
            Regex("""nach\s+\d+\s+minuten?"""),
            Regex("""sag\s+mir\s+bescheid\s+in"""),
            Regex("""in\s+\d+\s+(?:minuten?|stunden?)\s+bescheid"""),
        ),
        paramExtractor = { query ->
            val lower = query.lowercase()
            // Map German number words to digits
            val wordToNum = mapOf(
                "eine" to 1, "einer" to 1, "einem" to 1, "ein" to 1,
                "zwei" to 2, "drei" to 3, "vier" to 4, "fünf" to 5,
                "sechs" to 6, "sieben" to 7, "acht" to 8, "neun" to 9,
                "zehn" to 10, "fünfzehn" to 15, "zwanzig" to 20,
                "dreißig" to 30, "sechzig" to 60, "neunzig" to 90,
            )
            // Try digit-based match first, then word-based
            val delayM = Regex("""in\s+(\d+)\s+minuten?""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: wordToNum.entries.firstOrNull { (word, _) ->
                    Regex("in\\s+$word\\s+minuten?").containsMatchIn(lower)
                }?.value
            val delayH = Regex("""in\s+(\d+)\s+stunden?""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { it * 60 }
                ?: wordToNum.entries.firstOrNull { (word, _) ->
                    Regex("in\\s+$word\\s+stunden?").containsMatchIn(lower)
                }?.value?.let { it * 60 }
            val delay = delayM ?: delayH ?: 30
            val text = query
                .replace(Regex("(?i)erinner(?:e|)\\s+mich\\s+"), "")
                .replace(Regex("(?i)stell(?:e|)\\s+(?:mir\\s+)?(?:einen?\\s+)?(?:alarm|timer|wecker)\\s+"), "")
                .replace(Regex("(?i)in\\s+(?:\\d+|eine[rm]?|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|fünfzehn|zwanzig|dreißig|sechzig|neunzig)\\s+(?:minuten?|stunden?)"), "")
                .replace(Regex("@alarm|@reminder"), "")
                .trim().ifBlank { query }
            mapOf("text" to text, "delay_minutes" to delay)
        },
    )

    val getExchangeRate = ToolDefinition(
        id = "get_exchange_rate",
        description = "Aktuellen Wechselkurs oder Währungsumrechnung abrufen",
        paramSchema = """{"type":"object","properties":{"from":{"type":"string"},"to":{"type":"string"},"amount":{"type":"number","default":1}},"required":["from","to"]}""",
        regexPatterns = listOf(
            Regex("""wechselkurs"""),
            Regex("""(?:umrechnen|konvertieren)"""),
            Regex("""(?:wie\s+viel|was)\s+(?:ist|sind|kostet|kosten)\s+\d"""),
            Regex("""(?:euro|dollar|eur|usd|gbp|chf|jpy|btc)\s+(?:zu|in|nach|umrechnen)"""),
            Regex("""(?:in|nach)\s+(?:euro|dollar|eur|usd|gbp|chf|jpy|btc)\s+umrechnen"""),
            Regex("""was\s+kostet\s+(?:ein|eine[rn]?)\s+(?:dollar|euro|pfund)"""),
            Regex("""wie\s+viel\s+(?:euro|dollar|pfund)\s+(?:ist|sind|bekomme\s+ich)"""),
            Regex("""tausche\s+\d"""),
            Regex("""währungsrechner"""),
        ),
        paramExtractor = { query ->
            val lower = query.lowercase()
            // Known currency codes and German names — avoid matching random 3-letter words
            val currencyMap = mapOf(
                "euro" to "EUR", "eur" to "EUR",
                "dollar" to "USD", "usd" to "USD",
                "pfund" to "GBP", "gbp" to "GBP",
                "franken" to "CHF", "chf" to "CHF",
                "yen" to "JPY", "jpy" to "JPY",
                "yuan" to "CNY", "cny" to "CNY",
                "bitcoin" to "BTC", "btc" to "BTC",
                "rubel" to "RUB", "rub" to "RUB",
                "kronen" to "SEK",
            )
            val foundCurrencies = currencyMap.entries
                .filter { (name, _) -> lower.contains(name) }
                .map { (_, code) -> code }
                .distinct()
            val amount = Regex("""(\d[\d.,]*)""").find(lower)?.groupValues?.getOrNull(1)
                ?.replace(",", ".")?.toDoubleOrNull() ?: 1.0
            // Determine from/to: amount comes before "from" currency, after comes "to"
            val fromCode = foundCurrencies.getOrNull(0) ?: "EUR"
            val toCode = foundCurrencies.getOrNull(1) ?: "USD"
            mapOf("from" to fromCode, "to" to toCode, "amount" to amount)
        },
    )

    val newsBriefing = ToolDefinition(
        id = "news_briefing",
        description = "Aktuelle Nachrichten und Headlines abrufen",
        paramSchema = """{"type":"object","properties":{"max_items":{"type":"integer","default":9}},"required":[]}""",
        regexPatterns = listOf(
            Regex("""(?:aktuelle[sn]?|neueste[sn]?|heutige[sn]?)\s+(?:nachrichten?|news|headlines?)"""),
            Regex("""was\s+(?:ist|passiert|gibt\s+es)\s+(?:heute|gerade|aktuell)"""),
            Regex("""@news\b"""),
            Regex("""nachrichtenüberblick"""),
        ),
        paramExtractor = { _ -> mapOf("max_items" to 9) },
    )

    val readCalendar = ToolDefinition(
        id = "read_calendar",
        description = "Kalendertermine für einen Zeitraum abrufen",
        paramSchema = """{"type":"object","properties":{"days":{"type":"integer","default":7},"range_start":{"type":"string"},"range_end":{"type":"string"}},"required":[]}""",
        regexPatterns = listOf(
            Regex("""(?:meine[sn]?|nächste[sn]?|heute[sn]?)\s+(?:termine?|kalender|verabredungen?)"""),
            Regex("""was\s+(?:steht|habe\s+ich|ist)\s+(?:heute|morgen|diese\s+woche|nächste\s+woche)"""),
            Regex("""@kalender\b"""),
            Regex("""zeig\s+(?:mir\s+)?(?:meine[sn]?\s+)?termine?"""),
            Regex("""wann\s+(?:habe\s+ich|ist)\s+(?:mein|der)\s+nächste[rn]?\s+termin"""),
            Regex("""habe\s+ich\s+(?:heute|morgen|diese\s+woche)\s+(?:noch\s+)?(?:was|etwas|einen?\s+termin)"""),
            Regex("""bin\s+ich\s+(?:heute|morgen)\s+(?:noch\s+)?beschäftigt"""),
            Regex("""was\s+habe\s+ich\s+(?:diese|nächste)\s+woche\s+vor"""),
            Regex("""stehen\s+(?:noch\s+)?termine\s+an"""),
            Regex("""was\s+ist\s+(?:diese\s+woche\s+)?geplant"""),
        ),
        paramExtractor = { query ->
            val lower = query.lowercase()
            val days = when {
                lower.contains("heute") -> 1
                lower.contains("morgen") -> 2
                lower.contains("diese woche") -> 7
                lower.contains("nächste woche") -> 14
                lower.contains("monat") -> 30
                else -> 7
            }
            mapOf("days" to days)
        },
    )

    val createEvent = ToolDefinition(
        id = "create_event",
        description = "Einen neuen Kalendertermin erstellen",
        paramSchema = """{"type":"object","properties":{"title":{"type":"string"},"start":{"type":"string"},"end":{"type":"string"},"location":{"type":"string"},"notes":{"type":"string"}},"required":["title","start"]}""",
        regexPatterns = listOf(
            Regex("""(?:trage[sn]?|erstell[e]?|leg[e]?|schreib[e]?)\s+(?:einen?\s+)?(?:termin|event|verabredung)"""),
            Regex("""@termin\b"""),
            Regex("""erinner[e]?\s+mich\s+am\s+\d"""),
            Regex("""termin\s+(?:für|am|morgen|heute|nächste[sn]?)"""),
        ),
        paramExtractor = { query ->
            // Basic extraction — LLM will fill details via tool hint
            val lower = query.lowercase()
            val title = query
                .replace(Regex("(?i)trage[sn]?\\s+(?:einen?\\s+)?(?:termin|event)\\s+(?:für\\s+)?"), "")
                .replace(Regex("(?i)erstell[e]?\\s+(?:einen?\\s+)?(?:termin|event)\\s+"), "")
                .replace(Regex("@termin"), "")
                .trim().ifBlank { "Neuer Termin" }
            mapOf("title" to title, "start" to "")
        },
    )

    val searchNotes = ToolDefinition(
        id = "search_notes",
        description = "In gespeicherten Notizen suchen oder alle Notizen anzeigen",
        paramSchema = """{"type":"object","properties":{"query":{"type":"string"},"k":{"type":"integer","default":10}},"required":[]}""",
        regexPatterns = listOf(
            Regex("""(?:zeig|such)\s+(?:mir\s+)?(?:meine[sn]?\s+)?notizen?"""),
            Regex("""@notizen?\b"""),
            Regex("""was\s+habe\s+ich\s+(?:notiert|aufgeschrieben)"""),
            Regex("""in\s+meinen?\s+notizen?"""),
            Regex("""zeig\s+(?:mir\s+)?(?:alle\s+)?(?:meine[sn]?\s+)?notizen?"""),
            Regex("""was\s+(?:steht|habe\s+ich)\s+(?:in\s+meinen?\s+)?notizen?"""),
        ),
        paramExtractor = { query ->
            val clean = query.replace(Regex("@notizen?|zeig mir meine notizen?|such in meinen notizen?"), "").trim()
            if (clean.isBlank()) emptyMap() else mapOf("query" to clean)
        },
    )

    val saveNote = ToolDefinition(
        id = "save_note",
        description = "Eine Notiz speichern",
        paramSchema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}""",
        regexPatterns = listOf(
            Regex("""(?:speicher[e]?|notier[e]?|schreib[e]?\s+auf|halt[e]?\s+fest)\s+(?:folgendes|das|mir)?\s*:?"""),
            Regex("""@notiz\b"""),
            Regex("""mach\s+(?:eine?\s+)?notiz"""),
            Regex("""das\s+möchte\s+ich\s+festhalten"""),
            Regex("""(?:als\s+)?notiz:\s*\w"""),
            Regex("""ich\s+notiere:\s*\w"""),
        ),
        paramExtractor = { query ->
            val clean = query
                .replace(Regex("(?i)speicher[e]?\\s+(?:folgendes|das|mir)?\\s*:?"), "")
                .replace(Regex("(?i)notier[e]?\\s+(?:folgendes|das|mir)?\\s*:?"), "")
                .replace(Regex("(?i)schreib[e]?\\s+auf\\s*:?"), "")
                .replace(Regex("(?i)halt[e]?\\s+fest\\s*:?"), "")
                .replace(Regex("(?i)mach\\s+(?:eine?\\s+)?notiz\\s*:?"), "")
                .replace(Regex("@notiz"), "")
                .trim()
            mapOf("text" to clean.ifBlank { query })
        },
    )

    val summarize = ToolDefinition(
        id = "summarize",
        description = "Einen Text zusammenfassen",
        paramSchema = """{"type":"object","properties":{"text":{"type":"string"},"max_words":{"type":"integer","default":100},"style":{"type":"string","enum":["neutral","bullets","short"]}},"required":["text"]}""",
        regexPatterns = listOf(
            Regex("""fass[e]?\s+(?:das|den|die|folgend[esr]?|diesen?)\s+(?:text|inhalt|artikel|zusammen)"""),
            Regex("""(?:kannst|könntest)\s+du\s+(?:das|diesen?|den)\s+(?:zusammen)?fass[etn]"""),
            Regex("""@zusammenfassung\b"""),
            Regex("""(?:kurz[e]?\s+)?zusammenfassung\s+von"""),
            Regex("""TL;DR|tl;dr|tldr"""),
            Regex("""fass[e]?\s+(?:das\s+)?kurz\s+zusammen"""),
            Regex("""(?:kurze\s+)?zusammenfassung\s+(?:von|des|der)"""),
            Regex("""in\s+kürze:"""),
            Regex("""(?:was\s+ist\s+die\s+)?hauptaussage\s+(?:von|des|der)"""),
            Regex("""worum\s+geht\s+es\s+(?:bei|in|dem)"""),
        ),
        paramExtractor = { query ->
            val lower = query.lowercase()
            val style = when {
                lower.contains("stichpunkt") || lower.contains("bullet") -> "bullets"
                lower.contains("ein satz") || lower.contains("kurz") -> "short"
                else -> "neutral"
            }
            mapOf("text" to query, "style" to style, "max_words" to 100)
        },
    )
}
