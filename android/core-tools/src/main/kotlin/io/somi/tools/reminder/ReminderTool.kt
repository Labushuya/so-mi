package io.somi.tools.reminder

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import io.somi.tools.executor.ToolExecutor
import io.somi.tools.model.ToolCall
import io.somi.tools.model.ToolResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : ToolExecutor {
    override val toolId = "set_alarm"

    override suspend fun execute(call: ToolCall): ToolResult {
        val text = call.params["text"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return ToolResult(toolId, "", error = "Erinnerungstext fehlt")
        val delayMinutes = (call.params["delay_minutes"] as? Int)?.coerceIn(1, 10080) ?: 30
        return runCatching {
            ensureChannel()
            val triggerMs = System.currentTimeMillis() + delayMinutes * 60_000L
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("text", text)
            }
            val pi = PendingIntent.getBroadcast(
                context, text.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs, pi)
            }
            val timeDesc = if (delayMinutes < 60) "in $delayMinutes Minuten"
                          else "in ${delayMinutes / 60} Stunden"
            val accuracyNote = if (!canExact) "\n(Ungefähre Zeit — für präzise Alarme: Einstellungen → Apps → So-Mi → Alarme & Erinnerungen aktivieren)" else ""
            ToolResult(
                toolId,
                "[Alarm gesetzt]\n\"$text\" $timeDesc.$accuracyNote",
                displayHint = "Alarm $timeDesc",
            )
        }.getOrElse { ToolResult(toolId, "", error = "Alarm konnte nicht gesetzt werden: ${it.message}") }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel("reminders") == null) {
                nm.createNotificationChannel(
                    NotificationChannel("reminders", "Erinnerungen", NotificationManager.IMPORTANCE_HIGH)
                )
            }
        }
    }
}
