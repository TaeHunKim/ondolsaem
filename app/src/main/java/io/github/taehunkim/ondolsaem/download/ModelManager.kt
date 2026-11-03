package io.github.taehunkim.ondolsaem.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.taehunkim.ondolsaem.catalog.ModelCatalog
import io.github.taehunkim.ondolsaem.catalog.ModelSource
import io.github.taehunkim.ondolsaem.catalog.ModelSpec
import io.github.taehunkim.ondolsaem.settings.HostSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import java.io.File

sealed interface ModelState {
    data object NotInstalled : ModelState
    /** Queued until an unmetered network (Wi-Fi) is available. */
    data object WaitingForWifi : ModelState
    data class Downloading(val downloaded: Long, val total: Long) : ModelState
    /** Downloaded; checking the SHA-256 (and unpacking an archive). */
    data object Verifying : ModelState
    data object Installed : ModelState
    data class Failed(val kind: DownloadException.Kind?, val message: String?) : ModelState
    data object Unavailable : ModelState
}

class ModelManager(
    private val context: Context,
    private val settings: HostSettings,
    private val downloader: ModelDownloader = ModelDownloader(),
    private val modelsDir: File = File(context.filesDir, "models"),
) {
    private val _states = MutableStateFlow<Map<String, ModelState>>(emptyMap())
    val states: StateFlow<Map<String, ModelState>> = _states.asStateFlow()

    init { refresh() }

    fun file(spec: ModelSpec): File? = when (val s = spec.source) {
        is ModelSource.HuggingFace -> File(modelsDir, "${spec.id}/${s.fileName}")
        else -> null
    }

    /** Folder holding the unpacked files of an archive model. */
    fun dir(spec: ModelSpec): File = File(modelsDir, spec.id)

    fun isReady(spec: ModelSpec): Boolean = when (val s = spec.source) {
        is ModelSource.Bundled -> true
        is ModelSource.HuggingFace -> file(spec)?.exists() == true
        is ModelSource.Archive -> s.files.values.all { File(dir(spec), it).exists() }
        is ModelSource.Planned -> false
    }

    fun refresh() {
        _states.update { old ->
            ModelCatalog.all.associate { spec ->
                spec.id to when {
                    spec.source is ModelSource.Planned -> ModelState.Unavailable
                    (old[spec.id] is ModelState.Downloading || old[spec.id] is ModelState.WaitingForWifi || old[spec.id] is ModelState.Verifying) &&
                        !isReady(spec) -> old[spec.id]!!
                    isReady(spec) -> ModelState.Installed
                    else -> old[spec.id]?.takeIf { it is ModelState.Failed } ?: ModelState.NotInstalled
                }
            }
        }
    }

    private fun set(id: String, state: ModelState) = _states.update { it + (id to state) }

    /** [wifiOnly]: wait for an unmetered network (the user declined downloading over a metered one). */
    fun startDownload(id: String, wifiOnly: Boolean = false) {
        val spec = ModelCatalog.byId(id) ?: return
        if (spec.source !is ModelSource.HuggingFace && spec.source !is ModelSource.Archive || isReady(spec)) return
        set(id, if (wifiOnly) ModelState.WaitingForWifi else ModelState.Downloading(0, spec.sizeBytes ?: 0))
        val network = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val req = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .build()
        // REPLACE: a "wait for Wi-Fi" request may be turned into "download now" and the other way round
        WorkManager.getInstance(context).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelDownload(id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        // partial file is kept so the download can be resumed
        set(id, ModelState.NotInstalled)
    }

    fun delete(id: String) {
        val spec = ModelCatalog.byId(id) ?: return
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
        file(spec)?.let { f ->
            f.delete()
            File(f.path + ".part").delete()
        }
        if (spec.source is ModelSource.Archive) dir(spec).deleteRecursively()
        set(id, ModelState.NotInstalled)
    }

    /** Called from [ModelDownloadWorker]. */
    suspend fun runDownload(spec: ModelSpec, onProgress: (Long, Long) -> Unit) {
        val (url, target, token) = when (val src = spec.source) {
            is ModelSource.HuggingFace -> Triple(src.url, file(spec) ?: return, settings.hfToken.first())
            // the token belongs to Hugging Face only
            is ModelSource.Archive -> Triple(src.url, File(dir(spec), ARCHIVE_NAME), "")
            else -> return
        }
        set(spec.id, ModelState.Downloading(0, spec.sizeBytes ?: 0))
        try {
            downloader.download(url, target, token) { d, t ->
                set(spec.id, ModelState.Downloading(d, t))
                onProgress(d, t)
            }
            set(spec.id, ModelState.Verifying)
            withContext(Dispatchers.IO) {
                when (val src = spec.source) {
                    is ModelSource.HuggingFace -> Checksums.verify(target, src.sha256)
                    is ModelSource.Archive -> {
                        ArchiveExtractor.extract(target, dir(spec), src.files)
                        target.delete()
                        // a corrupt archive usually fails to unpack; this also catches a wrong but readable one
                        try {
                            src.sha256.forEach { (name, sha) -> Checksums.verify(File(dir(spec), name), sha) }
                        } catch (e: DownloadException) {
                            dir(spec).deleteRecursively()
                            throw e
                        }
                    }
                    else -> Unit
                }
            }
            set(spec.id, ModelState.Installed)
        } catch (e: CancellationException) {
            set(spec.id, ModelState.NotInstalled)
            throw e
        } catch (e: DownloadException) {
            set(spec.id, ModelState.Failed(e.kind, e.message))
            throw e
        } catch (e: Exception) {
            set(spec.id, ModelState.Failed(null, e.message))
            throw e
        }
    }

    private fun workName(id: String) = "model-download-$id"

    private companion object { const val ARCHIVE_NAME = "download.tar.bz2" }
}
