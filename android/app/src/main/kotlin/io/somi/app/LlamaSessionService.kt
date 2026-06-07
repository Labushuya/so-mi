package io.somi.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint

/**
 * Phase-2.10 Foreground Service that pins the so-mi process at
 * FOREGROUND_SERVICE oom_adj (~125) for as long as a model is loaded.
 *
 * Why we need this on Magic V2:
 * MagicOS / iaware aggressively kills cached processes that hold large
 * native heaps (>2 GB). After the user sent and received their first
 * message in v0.9.0, backgrounding the app for any reason (notification
 * shade, recents, screen off) triggered MagicOS to reap the process —
 * which dropped the loaded 4.4 GB GGUF and forced a cold restart. The
 * FGS prevents that: a process holding an FGS is essentially never
 * killed by either MagicOS or stock Android oom_adj.
 *
 * Lifecycle:
 *  - MainActivity.onCreate() calls ContextCompat.startForegroundService(intent)
 *    to ensure the service is running before binding.
 *  - onStartCommand() must call startForeground() within 5 s on Android 14
 *    or the service is killed with ANR. We post the notification first thing.
 *  - START_STICKY so the system re-creates us if we ever do get killed
 *    despite the FGS.
 *  - onDestroy() does not free the engine — that's still the
 *    ChatViewModel's job. The service is purely a process-pinning
 *    mechanism in this phase. Phase-3 may move engine ownership here.
 *
 * The notification is low-importance, ongoing, and tappable to bring
 * the user back to MainActivity. It's the cost of staying alive.
 */
@AndroidEntryPoint
internal class LlamaSessionService : Service() {

    inner class LocalBinder : Binder() {
        @Suppress("unused")
        fun getService(): LlamaSessionService = this@LlamaSessionService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand startId=$startId")
        startForegroundCompat()
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(io.somi.data.R.drawable.ic_notification)
            .setContentTitle("So-Mi")
            .setContentText("Modell läuft im Hintergrund.")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "So-Mi Engine",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "So-Mi hält das Sprachmodell im Speicher."
                setShowBadge(false)
            },
        )
    }

    internal companion object {
        const val TAG = "LlamaSessionService"
        const val CHANNEL_ID = "somi_engine"
        const val NOTIFICATION_ID = 0x5301

        /** Convenience entry point used by MainActivity. */
        fun start(context: Context) {
            val intent = Intent(context, LlamaSessionService::class.java)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }
    }
}
