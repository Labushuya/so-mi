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
            val m = Regex("""wetter\s+(?:in\s+|für\s+|von\s+)?(\w[\w\s]{1,25})""").find(query.lowercase())
            val loc = m?.groupValues?.getOrNull(1)?.trim() ?: query
            mapOf("location" to loc, "days" to 1)
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
