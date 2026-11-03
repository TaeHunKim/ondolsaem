package io.github.taehunkim.ondolsaem.sdk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Declared by the SDK's manifest, so every client app receives [OndolSaem.ACTION_MODEL_CHANGED] even when it is
 * not running. The app process starts, `Application.onCreate` sets [handler], then the broadcast is passed on.
 * The extras are only a hint: anyone can send this action, so the handler must re-check the host.
 */
class ModelChangedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != OndolSaem.ACTION_MODEL_CHANGED) return
        handler?.invoke(intent.getStringExtra(OndolSaem.EXTRA_CATEGORY))
    }

    companion object {
        /** Called on the main thread with the category hint (may be null). Keep it short: schedule work. */
        @Volatile var handler: ((category: String?) -> Unit)? = null
    }
}
