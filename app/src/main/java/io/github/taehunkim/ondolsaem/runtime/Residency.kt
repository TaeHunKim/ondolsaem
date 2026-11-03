package io.github.taehunkim.ondolsaem.runtime

import androidx.annotation.StringRes
import io.github.taehunkim.ondolsaem.R

/** How long a category's model stays in memory once no request or lease uses it. null = keep loaded. */
enum class Residency(val idleMillis: Long?, @StringRes val labelRes: Int) {
    KEEP(null, R.string.residency_keep),
    IMMEDIATE(0, R.string.residency_immediate),
    IDLE_1M(60_000, R.string.residency_idle_1m),
    IDLE_2M(120_000, R.string.residency_idle_2m),
    IDLE_5M(300_000, R.string.residency_idle_5m),
    IDLE_15M(900_000, R.string.residency_idle_15m),
}
