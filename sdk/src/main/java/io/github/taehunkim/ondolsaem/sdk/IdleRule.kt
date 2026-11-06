package io.github.taehunkim.ondolsaem.sdk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager

/**
 * How eager "when idle" background work (indexing, embedding) is: the phone must be charging and its screen off for
 * at least [minutesOff] for its battery level. A fuller battery waits less.
 */
enum class IdlePreset(
    /** Minutes the screen must have been off at ≥ 90 %, 70–89 % and below 70 % battery. */
    val minutesOff: Triple<Int, Int, Int>,
) {
    FAST(Triple(5, 15, 30)),
    NORMAL(Triple(10, 30, 60)),
    RELAXED(Triple(30, 60, 120)),
    ;

    companion object {
        val DEFAULT = NORMAL
        fun of(name: String?): IdlePreset = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** The idle decision, without Android: shared by the apps so "when idle" means the same everywhere. */
object IdleRule {
    const val HIGH = 90
    const val MID = 70

    fun requiredOffMinutes(preset: IdlePreset, batteryPct: Int): Int = when {
        batteryPct >= HIGH -> preset.minutesOff.first
        batteryPct >= MID -> preset.minutesOff.second
        else -> preset.minutesOff.third
    }

    /**
     * True when charging and the screen has been off since [screenOffSinceMs] for the preset's time. Null
     * [screenOffSinceMs] means the screen is on (or its off time is unknown): not idle.
     */
    fun isIdle(preset: IdlePreset, charging: Boolean, batteryPct: Int, screenOffSinceMs: Long?, nowMs: Long): Boolean {
        if (!charging || screenOffSinceMs == null) return false
        return nowMs - screenOffSinceMs >= requiredOffMinutes(preset, batteryPct) * 60_000L
    }
}

/**
 * Reads the phone's idle state for [IdleRule]: battery level and charging (sticky battery broadcast), screen on/off
 * ([PowerManager.isInteractive]) and since when the screen is off.
 *
 * Screen-off times come from a receiver registered at run time ([watchScreen], as SCREEN_ON/OFF cannot be declared
 * in a manifest) while the app process lives; otherwise the first [snapshot] that sees the screen off records "now",
 * so the time is late by at most the period of the caller's checks. A stored time is trusted only if the receiver
 * was watching when it was recorded, or a check has seen the screen off within [MAX_GAP_MS]: otherwise the screen may
 * have been on in between unseen (the process was gone), and the off time starts again now. Kept in SharedPreferences.
 */
class IdleState(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ondolsaem_idle", Context.MODE_PRIVATE)

    data class Snapshot(val charging: Boolean, val batteryPct: Int, val screenOn: Boolean, val screenOffSinceMs: Long?)

    fun snapshot(nowMs: Long = System.currentTimeMillis()): Snapshot {
        val battery = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        val charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val screenOn = app.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val since = if (screenOn) {
            prefs.edit().remove(KEY_OFF_SINCE).remove(KEY_CONFIRMED).apply(); null
        } else {
            val stored = prefs.getLong(KEY_OFF_SINCE, 0L)
            val confirmed = prefs.getLong(KEY_CONFIRMED, 0L)
            val trusted = stored > 0 && ((watchStart in 1..stored) || nowMs - confirmed <= MAX_GAP_MS)
            val s = if (trusted) stored else nowMs
            prefs.edit().putLong(KEY_OFF_SINCE, s).putLong(KEY_CONFIRMED, nowMs).apply()
            s
        }
        return Snapshot(charging, pct, screenOn, since)
    }

    /** True when [preset]'s idle rule holds now. */
    fun isIdle(preset: IdlePreset, nowMs: Long = System.currentTimeMillis()): Boolean =
        snapshot(nowMs).let { IdleRule.isIdle(preset, it.charging, it.batteryPct, it.screenOffSinceMs, nowMs) }

    /** True while the screen is on (the user is back): idle work should stop. */
    fun screenOn(): Boolean = app.getSystemService(PowerManager::class.java)?.isInteractive ?: true

    /** Records exact screen-off times while the process lives; call once from `Application.onCreate`. */
    fun watchScreen() {
        if (watchStart > 0) return
        watchStart = System.currentTimeMillis()
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_SCREEN_OFF -> System.currentTimeMillis().let { prefs.edit().putLong(KEY_OFF_SINCE, it).putLong(KEY_CONFIRMED, it).apply() }
                    Intent.ACTION_SCREEN_ON -> prefs.edit().remove(KEY_OFF_SINCE).remove(KEY_CONFIRMED).apply()
                }
            }
        }
        // sent by the system only; NOT_EXPORTED still receives system broadcasts (API 33+ requires a flag)
        val filter = IntentFilter().apply { addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON) }
        if (android.os.Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, filter, Context.RECEIVER_NOT_EXPORTED) else app.registerReceiver(r, filter)
    }

    /** Test hook: pretend the screen went off at [ms]. */
    fun setScreenOffSince(ms: Long?) {
        if (ms == null) prefs.edit().remove(KEY_OFF_SINCE).remove(KEY_CONFIRMED).apply()
        else prefs.edit().putLong(KEY_OFF_SINCE, ms).putLong(KEY_CONFIRMED, System.currentTimeMillis()).apply()
    }

    companion object {
        /** A stored screen-off time older than the last check by more than this is not trusted (see the class doc). */
        const val MAX_GAP_MS = 25 * 60_000L
        private const val KEY_OFF_SINCE = "screen_off_since"
        private const val KEY_CONFIRMED = "screen_off_confirmed"
        /** When this process started watching the screen (0: not watching). */
        @Volatile private var watchStart = 0L
    }
}
