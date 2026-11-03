package io.github.taehunkim.ondolsaem.service

import android.content.Context
import io.github.taehunkim.ondolsaem.settings.HostSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Usage of one category by one client app since the host process started. */
data class CategoryUsage(val requests: Int = 0, val totalMs: Long = 0, val lastUse: Long = 0, val failures: Int = 0) {
    val averageMs: Long get() = if (requests == 0) 0 else totalMs / requests
}

/**
 * Who uses the host: the packages and categories are stored (to warn about model changes and to notify those apps
 * later), request counts and timings are kept in memory for the dashboard.
 */
class ClientRegistry(private val context: Context, private val settings: HostSettings, private val scope: CoroutineScope) {
    private val _usage = MutableStateFlow<Map<String, Map<String, CategoryUsage>>>(emptyMap())
    val usage: StateFlow<Map<String, Map<String, CategoryUsage>>> = _usage.asStateFlow()

    private val recorded = ConcurrentHashMap.newKeySet<String>()

    /** Remembers that [pkg] uses [category] (stored once per pair and process). */
    fun touch(pkg: String, category: String?) {
        if (pkg == context.packageName) return
        if (recorded.add("$pkg|${category.orEmpty()}")) scope.launch { settings.recordClientUse(pkg, category) }
    }

    fun record(pkg: String, category: String, ms: Long, ok: Boolean) {
        if (pkg == context.packageName) return
        touch(pkg, category)
        _usage.update { all ->
            val forPkg = all[pkg].orEmpty()
            val u = forPkg[category] ?: CategoryUsage()
            all + (pkg to forPkg + (category to u.copy(
                requests = u.requests + 1, totalMs = u.totalMs + ms, lastUse = System.currentTimeMillis(),
                failures = u.failures + if (ok) 0 else 1,
            )))
        }
    }

    fun label(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}
