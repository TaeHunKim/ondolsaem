package io.github.taehunkim.ondolsaem.service

import android.content.Context
import android.content.Intent
import android.os.RemoteCallbackList
import android.util.Log
import io.github.taehunkim.ondolsaem.IClientCallback
import io.github.taehunkim.ondolsaem.ModelInfo
import io.github.taehunkim.ondolsaem.sdk.OndolSaem
import io.github.taehunkim.ondolsaem.settings.HostSettings
import kotlinx.coroutines.flow.first

/**
 * Tells client apps that a category's model changed: connected clients through their callback, every known client
 * package through an explicit broadcast (reaches apps that are not running; they re-check on receipt).
 */
class ModelChangeNotifier(private val context: Context, private val settings: HostSettings) {
    val callbacks = RemoteCallbackList<IClientCallback>()

    suspend fun notify(info: ModelInfo) {
        val n = callbacks.beginBroadcast()
        try {
            for (i in 0 until n) runCatching { callbacks.getBroadcastItem(i).onModelChanged(info) }
        } finally { callbacks.finishBroadcast() }
        val clients = settings.clients.first().keys
        for (pkg in clients) {
            val intent = Intent(OndolSaem.ACTION_MODEL_CHANGED).setPackage(pkg)
                .putExtra(OndolSaem.EXTRA_CATEGORY, info.category)
                .putExtra(OndolSaem.EXTRA_MODEL_ID, info.id)
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            runCatching { context.sendBroadcast(intent) }.onFailure { Log.w(TAG, "broadcast to $pkg failed", it) }
        }
        Log.i(TAG, "model change ${info.category} -> ${info.id} (ready=${info.ready}): $n connected, ${clients.size} known")
    }

    private companion object { const val TAG = "OndolSaem.Notify" }
}
