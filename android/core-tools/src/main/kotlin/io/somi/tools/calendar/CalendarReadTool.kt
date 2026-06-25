package io.somi.tools.calendar

import android.content.Context
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarReadTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {
    override val toolId = "read_calendar"

    private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.GERMAN)
    private val displayFmt = SimpleDateFormat("EEE dd.MM. HH:mm", Locale.GERMAN)

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val rangeStart = call.params["range_start"]?.toString()
        val rangeEnd = call.params["range_end"]?.toString()
        val days = (call.params["days"] as? Int) ?: 7

        val now = System.currentTimeMillis()
        val startMs = if (rangeStart != null) runCatching { fmt.parse(rangeStart)?.time }.getOrNull() ?: now
                      else now
        val endMs = if (rangeEnd != null) runCatching { fmt.parse(rangeEnd)?.time }.getOrNull()
                    else {
                        val cal = Calendar.getInstance().apply { timeInMillis = startMs; add(Calendar.DAY_OF_YEAR, days) }
                        cal.timeInMillis
                    }

        // Find all available calendars, prefer Google Calendar accounts
        val calendars = runCatching { queryCalendars() }.getOrElse { emptyList() }
        if (calendars.isEmpty()) {
            return@withContext ToolResult(toolId, "", error = "Kein Kalender gefunden. Bitte Kalender-Berechtigung prüfen.")
        }

        val events = runCatching { queryEvents(startMs, endMs ?: (startMs + days * 86400_000L), calendars.map { it.id }) }
            .getOrElse { e -> return@withContext ToolResult(toolId, "", error = "Kalender nicht verfügbar: ${e.message}") }

        val calendarNames = calendars.joinToString(", ") { it.displayName }

        if (events.isEmpty()) {
            return@withContext ToolResult(
                toolId,
                "[Kalender: $calendarNames]\nKeine Termine im gewählten Zeitraum.",
                displayHint = "Kalender leer"
            )
        }

        val block = buildString {
            append("[Kalender: $calendarNames — ${events.size} Termine]\n")
            events.forEach { e ->
                append("• ${displayFmt.format(Date(e.startMs))}: ${e.title}")
                if (e.calendarName.isNotBlank()) append(" (${e.calendarName})")
                append("\n")
            }
        }
        ToolResult(toolId, block, displayHint = "${events.size} Termine ($calendarNames)")
    }

    private fun queryCalendars(): List<CalInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection,
            "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1",
            null,
            // Google Calendar accounts first, then primary, then rest
            "${CalendarContract.Calendars.ACCOUNT_TYPE} DESC, ${CalendarContract.Calendars.IS_PRIMARY} DESC"
        ) ?: return emptyList()

        return cursor.use { c ->
            val list = mutableListOf<CalInfo>()
            val idIdx = c.getColumnIndex(CalendarContract.Calendars._ID)
            val nameIdx = c.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountIdx = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeIdx = c.getColumnIndex(CalendarContract.Calendars.ACCOUNT_TYPE)
            while (c.moveToNext()) {
                val accountType = c.getString(typeIdx) ?: ""
                val displayName = c.getString(nameIdx) ?: ""
                val accountName = c.getString(accountIdx) ?: ""
                // Build a human-readable name: prefer "Google Kalender (account@gmail.com)"
                val humanName = when {
                    accountType.contains("google", ignoreCase = true) ->
                        if (displayName.contains("@")) displayName
                        else "Google Kalender ($accountName)"
                    displayName.isNotBlank() -> displayName
                    else -> accountName
                }
                list += CalInfo(id = c.getLong(idIdx), displayName = humanName)
            }
            // Deduplicate by display name, keep first occurrence
            list.distinctBy { it.displayName }.take(5)
        }
    }

    private fun queryEvents(startMs: Long, endMs: Long, calendarIds: List<Long>): List<CalEvent> {
        if (calendarIds.isEmpty()) return emptyList()
        val idList = calendarIds.joinToString(",")
        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.CALENDAR_ID,
        )
        val calSelection = if (calendarIds.size == 1)
            "${CalendarContract.Events.CALENDAR_ID} = ${calendarIds[0]}"
        else
            "${CalendarContract.Events.CALENDAR_ID} IN ($idList)"
        val selection = "$calSelection AND ${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DELETED} = 0"
        val selArgs = arrayOf(startMs.toString(), endMs.toString())

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, selArgs,
            "${CalendarContract.Events.DTSTART} ASC LIMIT 20"
        ) ?: return emptyList()

        // Build calId → name map
        val calNameMap = mutableMapOf<Long, String>()
        queryCalendars().forEach { calNameMap[it.id] = it.displayName }

        return cursor.use { c ->
            val list = mutableListOf<CalEvent>()
            val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
            val calIdIdx = c.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
            while (c.moveToNext()) {
                val calId = if (calIdIdx >= 0) c.getLong(calIdIdx) else -1L
                list += CalEvent(
                    title = c.getString(titleIdx) ?: "Unbekannter Termin",
                    startMs = c.getLong(startIdx),
                    calendarName = calNameMap[calId] ?: "",
                )
            }
            list
        }
    }

    data class CalInfo(val id: Long, val displayName: String)
    data class CalEvent(val title: String, val startMs: Long, val calendarName: String)
}
// v0.47.1 Google Calendar fix
