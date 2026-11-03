package io.github.taehunkim.ondolsaem

import android.util.Log
import io.github.taehunkim.ondolsaem.sdk.OndolSaem.Categories
import io.github.taehunkim.ondolsaem.settings.HostSettings
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * Runs once per app update, before anything reads the settings. Add steps as `if (from < <versionCode>) { ... }`;
 * versionCode = major*10000 + minor*100 + patch. A fresh install has `from == 0`.
 */
object HostUpgrade {
    private const val TAG = "OndolSaem.Upgrade"

    /** What an update to [current] has to do. */
    data class Steps(val keepOldEmbeddingDefault: Boolean, val skipOnboarding: Boolean)

    /**
     * 0.4.0 made EmbeddingGemma 740M (with audio) the default. An install that was already in use keeps 440M as long
     * as nobody picked a model, otherwise every client would re-index, and it does not see the first-run screens.
     */
    fun steps(from: Int, existingInstall: Boolean): Steps {
        val old = from in 1 until 400 || (from == 0 && existingInstall) // versions before 0.4.0 stored no version
        return Steps(keepOldEmbeddingDefault = old, skipOnboarding = old)
    }

    suspend fun run(settings: HostSettings, filesDir: File, current: Int) {
        val from = settings.lastVersionCode.first()
        if (from >= current) return
        val existing = File(filesDir, "models").list().orEmpty().isNotEmpty() ||
            settings.clients.first().isNotEmpty() || settings.hasStoredChoices()
        val s = steps(from, existing)
        Log.i(TAG, "upgrading from $from to $current (existing install: $existing): $s")
        if (s.keepOldEmbeddingDefault && !settings.hasActiveModel(Categories.EMBEDDING)) settings.setActiveModel(Categories.EMBEDDING, OLD_EMBEDDING_DEFAULT)
        if (s.skipOnboarding) settings.setOnboardingDone(true)
        settings.setLastVersionCode(current)
    }

    /** The embedding default before 0.4.0. */
    const val OLD_EMBEDDING_DEFAULT = "eg2-440m"
}
