package io.github.taehunkim.ondolsaem

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import java.util.UUID

object Notifications {
    const val CH_DOWNLOAD = "download"
    const val CH_RESIDENT = "resident"
    const val CH_ALERTS = "alerts"
    const val ID_DOWNLOAD = 1001
    const val ID_RESIDENT = 1002
    const val ID_KEEP_BLOCKED = 1003

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_DOWNLOAD, ctx.getString(R.string.channel_download), NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(CH_RESIDENT, ctx.getString(R.string.channel_resident), NotificationManager.IMPORTANCE_MIN))
        nm.createNotificationChannel(NotificationChannel(CH_ALERTS, ctx.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_DEFAULT))
    }

    fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(ctx: Context, workId: UUID, title: String, text: String?, done: Int, total: Int, actionLabel: String): Notification {
        ensureChannels(ctx)
        val cancel = WorkManager.getInstance(ctx).createCancelPendingIntent(workId)
        return NotificationCompat.Builder(ctx, CH_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_ondolsaem)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(openApp(ctx))
            .setProgress(total, done, total == 0)
            .addAction(0, actionLabel, cancel)
            .build()
    }

    fun downloadForegroundInfo(n: Notification) =
        ForegroundInfo(ID_DOWNLOAD, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

    /** Shown while a "keep loaded" model is in memory (keeps the host process alive). */
    fun resident(ctx: Context, text: String): Notification {
        ensureChannels(ctx)
        return NotificationCompat.Builder(ctx, CH_RESIDENT)
            .setSmallIcon(R.drawable.ic_stat_ondolsaem)
            .setContentTitle(ctx.getString(R.string.notif_resident_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .build()
    }

    /** Android refused the keep-alive service; tapping opens the app, which starts it again. */
    fun keepAliveBlocked(ctx: Context): Notification {
        ensureChannels(ctx)
        return NotificationCompat.Builder(ctx, CH_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_ondolsaem)
            .setContentTitle(ctx.getString(R.string.notif_keep_blocked_title))
            .setContentText(ctx.getString(R.string.notif_keep_blocked_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(ctx.getString(R.string.notif_keep_blocked_text)))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
            .build()
    }

    fun cancel(ctx: Context, id: Int) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(id) }
    }

    fun notify(ctx: Context, id: Int, n: Notification) {
        runCatching { ctx.getSystemService(NotificationManager::class.java).notify(id, n) }
    }
}
