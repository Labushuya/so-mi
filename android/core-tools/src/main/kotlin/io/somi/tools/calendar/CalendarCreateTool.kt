package io.somi.tools.calendar

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import dagger.hilt.android.qualifiers.ApplicationContext
import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CalendarCreateTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {
    override val toolId = "create_event"

    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMAN)

    override suspend fun execute(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val title = call.params["title"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return@withContext ToolResult(toolId, "", error = "Titel fehlt")
        val startStr = call.params["start"]?.toString()
            ?: return@withContext ToolResult(toolId, "", error = "Startzeit fehlt")
        val endStr = call.params["end"]?.toString()

        val startMs = runCatching { fmt.parse(startStr)?.time }
            .getOrNull()
            ?: return@withContext ToolResult(toolId, "", error = "Startzeit ungültig: $startStr (Format: YYYY-MM-DD HH:MM)")
        val endMs = if (endStr != null) runCatching { fmt.parse(endStr)?.time }.getOrNull()
                    else startMs + 3600_000L // default 1h
        val location = call.params["location"]?.toString()
        val notes = call.params["notes"]?.toString()

        // Find primary calendar
        val calendarId = findPrimaryCalendarId()
            ?: return@withContext ToolResult(toolId, "", error = "Kein Kalender gefunden. Bitte prüfe die Kalender-Berechtigungen in den Einstellungen.")

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            if (location != null) put(CalendarContract.Events.EVENT_LOCATION, location)
            if (notes != null) put(CalendarContract.Events.DESCRIPTION, notes)
        }

        val uri = runCatching { context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) }
            .getOrNull()
            ?: return@withContext ToolResult(toolId, "", error = "Termin konnte nicht erstellt werden.")

        val displayFmt = java.text.SimpleDateFormat("EEE dd.MM. HH:mm", Locale.GERMAN)
        val block = "[Termin erstellt]\n\"$title\" am ${displayFmt.format(java.util.Date(startMs))}"
        ToolResult(toolId, block, displayHint = "Termin: $title")
    }

    private fun findPrimaryCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection,
            "${CalendarContract.Calendars.VISIBLE} = 1", null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC"
        ) ?: return null
        return cursor.use { c ->
            if (c.moveToFirst()) c.getLong(c.getColumnIndex(CalendarContract.Calendars._ID))
            else null
        }
    }
}
