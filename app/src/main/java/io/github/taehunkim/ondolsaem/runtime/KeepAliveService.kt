package io.github.taehunkim.ondolsaem.runtime

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.taehunkim.ondolsaem.Notifications

/**
 * Foreground service that keeps the host process (and the models in it) alive while a model of a category set to
 * "keep loaded" is in memory. Without it Android may kill the process as soon as no client is bound.
 */
class KeepAliveService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT).orEmpty()
        val n = Notifications.resident(this, text)
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(Notifications.ID_RESIDENT, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            else startForeground(Notifications.ID_RESIDENT, n)
        } catch (e: Exception) {
            Log.w(TAG, "cannot run in the foreground", e)
            (application as? io.github.taehunkim.ondolsaem.OndolSaemApp)?.container?.setKeepAliveBlocked(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "OndolSaem.KeepAlive"
        private const val EXTRA_TEXT = "text"

        /** Starts or updates the service. Fails quietly when Android does not allow it right now (app in background). */
        fun start(ctx: Context, text: String): Boolean = try {
            ContextCompat.startForegroundService(ctx, Intent(ctx, KeepAliveService::class.java).putExtra(EXTRA_TEXT, text))
            true
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= 31 && e is ForegroundServiceStartNotAllowedException) Log.w(TAG, "start not allowed now")
            else Log.w(TAG, "start failed", e)
            false
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, KeepAliveService::class.java)) }
    }
}
