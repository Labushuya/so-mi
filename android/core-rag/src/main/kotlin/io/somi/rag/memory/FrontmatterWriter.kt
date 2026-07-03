package io.somi.rag.memory

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val TAG = "FrontmatterWriter"

// ---------------------------------------------------------------------------
// Data classes
// ---------------------------------------------------------------------------

data class OkfMeta(
    val id: String,
    val type: String = "Fakt",
    val title: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val relations: List<OkfRelation> = emptyList(),
    val created: String,
    val supersededBy: String? = null,
)

// from: source entity (e.g. "Ich"), target: destination entity or file path, rel: relation label
data class OkfRelation(
    val from: String = "",
    val target: String,
    val rel: String,
)

// ---------------------------------------------------------------------------
// FrontmatterWriter
// ---------------------------------------------------------------------------

object FrontmatterWriter {

    fun hasFrontmatter(file: File): Boolean {
        return try {
            file.bufferedReader().use { it.readLine() }?.trimEnd() == "---"
        } catch (e: Exception) {
            Log.w(TAG, "hasFrontmatter: cannot read ${file.path}", e)
            false
        }
    }

    fun parseOptionalFrontmatter(file: File): OkfMeta? {
        if (!hasFrontmatter(file)) return null
        return try {
            val lines = file.readLines()
            val blockLines = extractFrontmatterLines(lines) ?: return null
            parseMeta(blockLines)
        } catch (e: Exception) {
            Log.e(TAG, "parseOptionalFrontmatter: failed for ${file.path}", e)
            null
        }
    }

    /** Convenience: reads only the id field without full parse. Generates a UUID and prepends
     *  frontmatter if the file has none, so the caller always gets a stable id. */
    fun readId(file: File): String {
        val existing = parseOptionalFrontmatter(file)
        if (existing != null) return existing.id
        // File has no frontmatter yet — generate an id and prepend a minimal header.
        val newId = UUID.randomUUID().toString()
        val ts = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN).format(Date())
        val title = file.readLines().firstOrNull { it.trimStart().startsWith("- ") }
            ?.trimStart()?.removePrefix("- ")
            ?.replace(Regex("""\s+_\(gespeichert:.*?\)_\s*$"""), "")
            ?.trim() ?: file.nameWithoutExtension
        val meta = OkfMeta(
            id = newId,
            title = escapeYamlString(title),
            category = file.nameWithoutExtension,
            created = ts,
        )
        prependFrontmatter(file, meta)
        return newId
    }

    fun prependFrontmatter(file: File, meta: OkfMeta) {
        try {
            if (hasFrontmatter(file)) return
            val existingContent = if (file.exists()) file.readText() else ""
            file.writeText(buildYaml(meta) + existingContent)
        } catch (e: Exception) {
            Log.e(TAG, "prependFrontmatter: failed for ${file.path}", e)
        }
    }

    fun updateRelations(file: File, relations: List<OkfRelation>) {
        try {
            val lines = file.readLines().toMutableList()
            val range = frontmatterLineRange(lines) ?: run {
                Log.w(TAG, "updateRelations: no front-matter in ${file.path}")
                return
            }
            val blockLines = lines.subList(range.first, range.last + 1).toMutableList()
            val relationsYaml = buildRelationsBlock(relations)

            val relStart = blockLines.indexOfFirst { it.trimStart().startsWith("relations:") }
            if (relStart >= 0) {
                var relEnd = relStart + 1
                while (relEnd < blockLines.size && (blockLines[relEnd].startsWith("  ") || blockLines[relEnd].startsWith("\t"))) {
                    relEnd++
                }
                repeat(relEnd - relStart) { blockLines.removeAt(relStart) }
                blockLines.addAll(relStart, relationsYaml)
            } else {
                val closingIdx = blockLines.indexOfLast { it.trim() == "---" }
                val insertAt = if (closingIdx >= 0) closingIdx else blockLines.size
                blockLines.addAll(insertAt, relationsYaml)
            }

            val rebuilt = (lines.subList(0, range.first) + blockLines + lines.subList(range.last + 1, lines.size))
                .joinToString("\n")
            file.writeText(if (rebuilt.endsWith("\n")) rebuilt else "$rebuilt\n")
        } catch (e: Exception) {
            Log.e(TAG, "updateRelations: failed for ${file.path}", e)
        }
    }

    fun updateSupersededBy(file: File, newFactId: String) {
        try {
            val lines = file.readLines().toMutableList()
            val range = frontmatterLineRange(lines) ?: run {
                Log.w(TAG, "updateSupersededBy: no front-matter in ${file.path}")
                return
            }
            val blockLines = lines.subList(range.first, range.last + 1).toMutableList()
            val newLine = """superseded_by: "${escapeYamlString(newFactId)}""""
            val idx = blockLines.indexOfFirst { it.trimStart().startsWith("superseded_by:") }
            if (idx >= 0) {
                blockLines[idx] = newLine
            } else {
                val closingIdx = blockLines.indexOfLast { it.trim() == "---" }
                blockLines.add(if (closingIdx >= 0) closingIdx else blockLines.size, newLine)
            }
            val rebuilt = (lines.subList(0, range.first) + blockLines + lines.subList(range.last + 1, lines.size))
                .joinToString("\n")
            file.writeText(if (rebuilt.endsWith("\n")) rebuilt else "$rebuilt\n")
        } catch (e: Exception) {
            Log.e(TAG, "updateSupersededBy: failed for ${file.path}", e)
        }
    }

    // -----------------------------------------------------------------------
    // YAML building
    // -----------------------------------------------------------------------

    private fun buildYaml(meta: OkfMeta): String = buildString {
        appendLine("---")
        appendLine("""id: "${escapeYamlString(meta.id)}"""")
        appendLine("type: ${meta.type}")
        appendLine("""title: "${escapeYamlString(meta.title)}"""")
        appendLine("category: ${meta.category}")
        appendLine(buildTagsLine(meta.tags))
        buildRelationsBlock(meta.relations).forEach { appendLine(it) }
        appendLine("created: ${meta.created}")
        if (meta.supersededBy != null) {
            appendLine("""superseded_by: "${escapeYamlString(meta.supersededBy)}"""")
        } else {
            appendLine("superseded_by: null")
        }
        appendLine("---")
        appendLine()
    }

    private fun buildRelationsBlock(relations: List<OkfRelation>): List<String> {
        if (relations.isEmpty()) return listOf("relations: []")
        val lines = mutableListOf("relations:")
        relations.forEach { rel ->
            lines.add("""  - from: "${escapeYamlString(rel.from)}"""")
            lines.add("""    target: "${escapeYamlString(rel.target)}"""")
            lines.add("""    rel: "${escapeYamlString(rel.rel)}"""")
        }
        return lines
    }

    private fun buildTagsLine(tags: List<String>): String =
        if (tags.isEmpty()) "tags: []"
        else "tags: [${tags.joinToString(", ") { "\"${escapeYamlString(it.trim())}\"" }}]"

    private fun escapeYamlString(value: String): String = value.replace("\"", "\\\"")

    // -----------------------------------------------------------------------
    // YAML parsing
    // -----------------------------------------------------------------------

    private fun extractFrontmatterLines(lines: List<String>): List<String>? {
        if (lines.isEmpty() || lines[0].trimEnd() != "---") return null
        val closingIdx = lines.drop(1).indexOfFirst { it.trimEnd() == "---" }
        if (closingIdx < 0) return null
        return lines.subList(1, closingIdx + 1)
    }

    private fun frontmatterLineRange(lines: List<String>): IntRange? {
        if (lines.isEmpty() || lines[0].trimEnd() != "---") return null
        val closingIdx = lines.drop(1).indexOfFirst { it.trimEnd() == "---" }
        if (closingIdx < 0) return null
        return 0..(closingIdx + 1)
    }

    private fun parseMeta(blockLines: List<String>): OkfMeta? {
        val kvMap = mutableMapOf<String, String>()
        val relations = mutableListOf<OkfRelation>()
        var inRelations = false
        var currentFrom = ""
        var currentTarget: String? = null

        for (rawLine in blockLines) {
            val line = rawLine.trimEnd()
            if (inRelations) {
                when {
                    line.trimStart().startsWith("- from:") -> {
                        currentFrom = line.trimStart().removePrefix("- from:").trim().unquote()
                        currentTarget = null
                    }
                    line.trimStart().startsWith("- target:") -> {
                        // from-less format (older files)
                        currentFrom = ""
                        currentTarget = line.trimStart().removePrefix("- target:").trim().unquote()
                    }
                    line.trimStart().startsWith("target:") -> {
                        currentTarget = line.trimStart().removePrefix("target:").trim().unquote()
                    }
                    line.trimStart().startsWith("rel:") -> {
                        val rel = line.trimStart().removePrefix("rel:").trim().unquote()
                        currentTarget?.let { relations.add(OkfRelation(from = currentFrom, target = it, rel = rel)) }
                        currentFrom = ""; currentTarget = null
                    }
                    !line.startsWith(" ") && !line.startsWith("\t") && line.isNotBlank() -> {
                        inRelations = false
                        parseKvLine(line)?.let { (k, v) -> kvMap[k] = v }
                    }
                }
                continue
            }
            if (line.startsWith("relations:")) {
                inRelations = true
                val inline = line.removePrefix("relations:").trim()
                if (inline == "[]") inRelations = false
                continue
            }
            parseKvLine(line)?.let { (k, v) -> kvMap[k] = v }
        }

        val id = kvMap["id"]?.unquote() ?: return null
        val title = kvMap["title"]?.unquote() ?: return null
        val category = kvMap["category"] ?: return null
        val created = kvMap["created"] ?: return null
        return OkfMeta(
            id = id,
            type = kvMap["type"] ?: "Fakt",
            title = title,
            category = category,
            tags = kvMap["tags"]?.let { parseInlineTags(it) } ?: emptyList(),
            relations = relations,
            created = created,
            supersededBy = kvMap["superseded_by"]?.unquote()?.takeIf { it != "null" },
        )
    }

    private fun parseKvLine(line: String): Pair<String, String>? {
        val colonIdx = line.indexOf(':')
        if (colonIdx < 0) return null
        return line.substring(0, colonIdx).trim() to line.substring(colonIdx + 1).trim()
    }

    private fun parseInlineTags(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed == "[]" || trimmed.isEmpty()) return emptyList()
        return if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed.removeSurrounding("[", "]").split(",")
                .map { it.trim().unquote() }.filter { it.isNotEmpty() }
        } else listOf(trimmed.unquote())
    }

    private fun String.unquote(): String {
        val t = trim()
        return if (t.startsWith("\"") && t.endsWith("\""))
            t.removeSurrounding("\"").replace("\\\"", "\"")
        else t
    }
}
