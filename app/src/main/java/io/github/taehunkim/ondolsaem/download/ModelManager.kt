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
import io.github.taehunkim.ondolsaem.engine.NliW8Converter
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
    /** Downloaded and verified; being converted on the phone ([ModelSource.HuggingFace.convert]). */
    data class Converting(val done: Long, val total: Long) : ModelState
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

    /** The file the runtime opens: the converted model when the source is converted, else [file]. */
    fun modelFile(spec: ModelSpec): File? = when (val s = spec.source) {
        is ModelSource.HuggingFace -> if (s.convert != null) File(dir(spec), "${s.convert}/model.onnx") else file(spec)
        else -> null
    }

    /** An extra file of a Hugging Face model ([ModelSource.HuggingFace.extras]), stored by its name. */
    fun extra(spec: ModelSpec, path: String): File = File(dir(spec), path.substringAfterLast('/'))

    /** Folder holding the unpacked files of an archive model. */
    fun dir(spec: ModelSpec): File = File(modelsDir, spec.id)

    fun isReady(spec: ModelSpec): Boolean = when (val s = spec.source) {
        is ModelSource.Bundled -> true
        is ModelSource.HuggingFace -> s.extras.keys.all { extra(spec, it).exists() } &&
            (if (s.convert != null) File(dir(spec), "${s.convert}/weights.bin").exists() && modelFile(spec)!!.exists() else file(spec)?.exists() == true)
        is ModelSource.Archive -> s.files.values.all { File(dir(spec), it).exists() }
        is ModelSource.Planned -> false
    }

    fun refresh() {
        _states.update { old ->
            ModelCatalog.all.associate { spec ->
                spec.id to when {
                    spec.source is ModelSource.Planned -> ModelState.Unavailable
                    (old[spec.id] is ModelState.Downloading || old[spec.id] is ModelState.WaitingForWifi || old[spec.id] is ModelState.Verifying ||
                        old[spec.id] is ModelState.Converting) &&
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
        val hf = spec.source as? ModelSource.HuggingFace
        if (spec.source is ModelSource.Archive || hf?.extras?.isNotEmpty() == true || hf?.convert != null) {
            dir(spec).deleteRecursively()
        }
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
            val src0 = spec.source
            if (src0 is ModelSource.HuggingFace) {
                // small companion files (tokenizer, config) after the main file; verified at once
                for ((path, sha) in src0.extras) {
                    val f = extra(spec, path)
                    if (f.exists()) continue
                    downloader.download(src0.url(path), f, token) { _, _ -> }
                    withContext(Dispatchers.IO) { Checksums.verify(f, sha) }
                }
            }
            set(spec.id, ModelState.Verifying)
            withContext(Dispatchers.IO) {
                when (val src = spec.source) {
                    is ModelSource.HuggingFace -> {
                        Checksums.verify(target, src.sha256)
                        src.convert?.let { conversion ->
                            convert(conversion, spec, target)
                            target.delete() // the converted model replaces the download
                            target.parentFile?.takeIf { it != dir(spec) }?.delete() // its folder, if now empty
                        }
                    }
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

    /** Converts [source] into `dir/<conversion>/` (template from the APK assets, weights computed here). */
    private fun convert(conversion: String, spec: ModelSpec, source: File) {
        val out = File(dir(spec), conversion).apply { mkdirs() }
        val recipe = context.assets.open("$conversion/recipe.tsv").bufferedReader().use { it.readText() }
        context.assets.open("$conversion/model.onnx").use { i -> File(out, "model.onnx").outputStream().use { i.copyTo(it) } }
        when (conversion) {
            ModelSource.Conversions.NLI_W8 -> NliW8Converter.convert(source, NliW8Converter.parseRecipe(recipe), File(out, "weights.bin")) { d, t ->
                set(spec.id, ModelState.Converting(d, t))
            }
            else -> error("unknown conversion $conversion")
        }
    }

    private fun workName(id: String) = "model-download-$id"

    private companion object { const val ARCHIVE_NAME = "download.tar.bz2" }
}
