package io.somi.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification plumbing for the model-download foreground service.
 *
 * One channel, low-importance (silent ongoing). Created lazily on first
 * worker run. Idempotent — re-creating an existing channel with the same
 * id is a no-op on the platform side.
 */
object DownloadNotifications {

    /** Channel id; user-visible in Settings → Notifications → so-mi. */
    const val CHANNEL_ID = "model_downloads"

    /** Stable notification id. WorkManager re-uses it across status updates. */
    const val NOTIF_ID = 0x501

    /**
     * Create the channel if it isn't already there. Safe to call from
     * any thread. Pre-26 (API 26 == Oreo) is a no-op since channels
     * don't exist; we still target API 30+ so the legacy branch is
     * unreachable but harmless.
     */
    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Modell-Downloads",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "So-Mi lädt Sprachmodelle herunter."
                setShowBadge(false)
            },
        )
    }
}
