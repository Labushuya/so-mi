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

        val events = runCatching { queryEvents(startMs, endMs ?: (startMs + days * 86400_000L)) }
            .getOrElse { e -> return@withContext ToolResult(toolId, "", error = "Kalender nicht verfügbar: ${e.message}") }

        if (events.isEmpty()) {
            return@withContext ToolResult(toolId, "[Kalender]\nKeine Termine im gewählten Zeitraum.", displayHint = "Kalender leer")
        }

        val block = buildString {
            append("[Kalender — nächste ${events.size} Termine]\n")
            events.forEach { e -> append("• ${displayFmt.format(Date(e.startMs))}: ${e.title}\n") }
        }
        ToolResult(toolId, block, displayHint = "${events.size} Termine")
    }

    private fun queryEvents(startMs: Long, endMs: Long): List<CalEvent> {
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
        )
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DELETED} = 0"
        val selArgs = arrayOf(startMs.toString(), endMs.toString())
        val sortOrder = "${CalendarContract.Events.DTSTART} ASC LIMIT 20"

        val cursor = context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, selArgs, sortOrder
        ) ?: return emptyList()

        return cursor.use { c ->
            val list = mutableListOf<CalEvent>()
            val titleIdx = c.getColumnIndex(CalendarContract.Events.TITLE)
            val startIdx = c.getColumnIndex(CalendarContract.Events.DTSTART)
            while (c.moveToNext()) {
                list += CalEvent(
                    title = c.getString(titleIdx) ?: "Unbekannter Termin",
                    startMs = c.getLong(startIdx),
                )
            }
            list
        }
    }

    data class CalEvent(val title: String, val startMs: Long)
}
