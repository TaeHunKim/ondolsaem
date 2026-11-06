package io.github.taehunkim.ondolsaem

import android.app.Application
import android.content.ComponentCallbacks2
import io.github.taehunkim.ondolsaem.catalog.ModelCatalog
import io.github.taehunkim.ondolsaem.download.ModelManager
import io.github.taehunkim.ondolsaem.runtime.KeepAliveService
import io.github.taehunkim.ondolsaem.runtime.ModelRuntime
import io.github.taehunkim.ondolsaem.service.CallerCheck
import io.github.taehunkim.ondolsaem.service.ClientRegistry
import io.github.taehunkim.ondolsaem.service.ModelChangeNotifier
import io.github.taehunkim.ondolsaem.settings.HostSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HostContainer(val app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = HostSettings(app)
    val modelManager = ModelManager(app, settings)
    val runtime = ModelRuntime(app, modelManager, settings, scope)
    val approvals = io.github.taehunkim.ondolsaem.service.Approvals(settings, scope)
    val caller = CallerCheck(app, approvals).also { c ->
        c.onRequest = { pkg -> Notifications.notify(app, Notifications.approvalId(pkg), Notifications.approvalRequest(app, registryLabel(pkg))) }
    }
    val registry = ClientRegistry(app, settings, scope)
    val notifier = ModelChangeNotifier(app, settings)

    private fun registryLabel(pkg: String) = registry.label(pkg)

    /** The user decided about a third-party app (approval list or notification). */
    fun decideApproval(pkg: String, allow: Boolean) {
        approvals.decide(pkg, allow)
        Notifications.cancel(app, Notifications.approvalId(pkg))
        if (allow) scope.launch { settings.recordClientUse(pkg, null) }
    }

    /**
     * API level this host reports (normally the SDK's [io.github.taehunkim.ondolsaem.sdk.OndolSaem.API_VERSION];
     * tests lower it to act as an older host).
     */
    @Volatile var apiVersion: Int = io.github.taehunkim.ondolsaem.sdk.OndolSaem.API_VERSION

    /**
     * Oldest client API level still served. Raise it only when an old contract can no longer be honoured; those
     * apps then ask their users to update.
     */
    @Volatile var minClientApi: Int = MIN_CLIENT_API

    companion object {
        const val MIN_CLIENT_API = io.github.taehunkim.ondolsaem.sdk.OndolSaem.Api.V1
    }

    /** The user picked another model for [category]. */
    suspend fun selectModel(category: String, id: String) {
        if (settings.activeModelId(category).first() == id) return
        settings.setActiveModel(category, id)
        runtime.unload(category)
        notifier.notify(runtime.modelInfo(category))
    }

    /** A download finished: if it is the active model of its category, clients can use it now. */
    suspend fun onModelInstalled(id: String) {
        modelManager.refresh()
        val spec = ModelCatalog.byId(id) ?: return
        if (settings.activeModelId(spec.category).first() == id) notifier.notify(runtime.modelInfo(spec.category))
    }

    /** An NPU build arrived: an active model loaded on the GPU/CPU reloads on the NPU at its next use. */
    suspend fun onNpuInstalled(id: String) {
        modelManager.refresh()
        val spec = ModelCatalog.byId(id) ?: return
        if (settings.useNpu.first() && settings.activeModelId(spec.category).first() == id) runtime.resources[spec.category]?.trim()
    }

    suspend fun deleteNpu(id: String) {
        val spec = ModelCatalog.byId(id) ?: return
        if (settings.activeModelId(spec.category).first() == id) runtime.unload(spec.category)
        modelManager.deleteNpu(id)
    }

    /** NPU on/off: loaded models move at their next use (those in use finish on the old backend). */
    suspend fun setUseNpu(on: Boolean) {
        settings.setUseNpu(on)
        runtime.resources.values.forEach { it.trim() }
    }

    suspend fun deleteModel(id: String) {
        val spec = ModelCatalog.byId(id) ?: return
        val active = settings.activeModelId(spec.category).first() == id
        if (active) runtime.unload(spec.category)
        modelManager.delete(id)
        if (active) notifier.notify(runtime.modelInfo(spec.category))
    }

    /** Notification text of the keep-alive service while a "keep loaded" model is in memory, else null. */
    @Volatile private var keepAliveText: String? = null
    private val _keepAliveBlocked = MutableStateFlow(false)

    /**
     * True while a "keep loaded" model is in memory but Android refused to start the keep-alive foreground service
     * (the host was in the background). The model stays loaded, but the process may be killed when no client is
     * bound. Retried by [retryKeepAlive].
     */
    val keepAliveBlocked: StateFlow<Boolean> = _keepAliveBlocked.asStateFlow()

    @Volatile private var lastKeepAliveTry = 0L

    /**
     * Starts the keep-alive service again if it was refused; cheap no-op otherwise. Requests from clients call it
     * with [force] false (at most one try per 30 s); the app's own screens force it.
     */
    fun retryKeepAlive(force: Boolean = false) {
        if (!_keepAliveBlocked.value) return
        val now = android.os.SystemClock.elapsedRealtime()
        if (!force && now - lastKeepAliveTry < 30_000) return
        lastKeepAliveTry = now
        val text = keepAliveText ?: run { setKeepAliveBlocked(false); return }
        if (KeepAliveService.start(app, text)) setKeepAliveBlocked(false)
    }

    /** Also called by the service when `startForeground` itself is refused. */
    fun setKeepAliveBlocked(blocked: Boolean) {
        if (_keepAliveBlocked.value == blocked) return
        _keepAliveBlocked.value = blocked
        if (blocked) Notifications.notify(app, Notifications.ID_KEEP_BLOCKED, Notifications.keepAliveBlocked(app))
        else Notifications.cancel(app, Notifications.ID_KEEP_BLOCKED)
    }

    /** Runs the keep-alive foreground service while a "keep loaded" model is in memory. */
    fun watchKeepAlive() {
        scope.launch {
            val states = combine(runtime.resources.values.map { it.state }) { it.toList() }
            combine(runtime.keptModelLoaded, states) { kept, loaded ->
                if (!kept) null else {
                    val present = loaded.filterNotNull()
                    app.getString(R.string.notif_resident_text, present.size, present.sumOf { it.loadedKb ?: 0 } / 1024)
                }
            }.distinctUntilChanged().collect { text ->
                keepAliveText = text
                if (text != null) setKeepAliveBlocked(!KeepAliveService.start(app, text))
                else { KeepAliveService.stop(app); setKeepAliveBlocked(false) }
            }
        }
    }
}

class OndolSaemApp : Application() {
    lateinit var container: HostContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // before anything reads the settings (the default embedding model depends on it)
        kotlinx.coroutines.runBlocking { HostUpgrade.run(HostSettings(this@OndolSaemApp), filesDir, BuildConfig.VERSION_CODE) }
        container = HostContainer(this)
        Notifications.ensureChannels(this)
        container.watchKeepAlive()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // models of categories set to "keep loaded" stay; the others go when the system needs memory
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            container.scope.launch { container.runtime.trim(includeKept = false) }
        }
    }
}
