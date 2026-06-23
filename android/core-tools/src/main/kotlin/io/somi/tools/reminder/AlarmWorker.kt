package io.somi.tools.reminder

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class AlarmWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val text = inputData.getString("text") ?: "Alarm"
        val notification = NotificationCompat.Builder(applicationContext, "reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("So-Mi Alarm läuft…")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        return ForegroundInfo(text.hashCode() + 1, notification)
    }

    override suspend fun doWork(): Result {
        val text = inputData.getString("text") ?: return Result.failure()

        // Run as foreground service so system won't kill us before we fire
        setForeground(getForegroundInfo())

        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Play ringtone directly — bypasses notification sound restrictions on OEM ROMs
        var ringtone: Ringtone? = null
        try {
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)
            ringtone?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    it.isLooping = false
                }
                (applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                    .setStreamVolume(AudioManager.STREAM_ALARM,
                        (applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
                            .getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
                it.play()
            }
        } catch (e: Exception) { /* ignore ringtone errors */ }

        // Vibrate immediately
        val pattern = longArrayOf(0, 500, 200, 500, 200, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = applicationContext.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            (applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                ?.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }

        // Show persistent alarm notification
        val notification = NotificationCompat.Builder(applicationContext, "reminders")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("So-Mi Alarm")
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 300, 200, 300, 200, 300))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()
        nm.notify(text.hashCode(), notification)

        // Keep ringtone playing briefly, then stop
        delay(4000)
        try { ringtone?.stop() } catch (e: Exception) { /* ignore */ }

        return Result.success()
    }
}
