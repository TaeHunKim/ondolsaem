package io.github.taehunkim.ondolsaem.engine

import android.os.Build
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class EmbeddingGemma2Embedder private constructor(
    override val modelId: String,
    private var engine: EmbeddingEngine,
    backend: String = "",
    /** Loads the GPU/CPU engine when the NPU fails on an input (null: not on the NPU). */
    private var fallback: (suspend () -> Pair<EmbeddingEngine, String>)? = null,
) : Embedder {
    override var backend: String = backend
        private set
    private val mutex = Mutex()
    private val options = EmbeddingOptions(normalize = true)
    /** Several frames in one input must fit the model's 1024-token limit: 70 vision tokens per frame. */
    private val clipOptions = EmbeddingOptions(normalize = true, visionTokensPerImage = 70)

    override suspend fun embedImage(jpeg: ByteArray): FloatArray =
        compute(InputData.Image(jpeg))

    override suspend fun embedDocument(text: String, title: String?): FloatArray =
        retryShorter(text) { t ->
            compute(InputData.Text("title: ${title?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_TITLE_CHARS) ?: "none"} | text: $t"))
        }

    override suspend fun embedQuery(text: String): FloatArray =
        retryShorter(text.take(MAX_QUERY_CHARS)) { t -> compute(InputData.Text(QUERY_PREFIX + t)) }

    override val supportsAudio: Boolean get() = modelId == "eg2-740m"

    override suspend fun embedAudio(wav: ByteArray): FloatArray = compute(InputData.Audio(wav))

    override suspend fun embedImages(jpegs: List<ByteArray>): FloatArray =
        computeAll(jpegs.map { InputData.Image(it) }, clipOptions)

    private suspend fun compute(input: InputData): FloatArray = computeAll(listOf(input))

    private suspend fun computeAll(inputs: List<InputData>, opts: EmbeddingOptions = options): FloatArray = mutex.withLock {
        withContext(Dispatchers.Default) {
            val v = try {
                engine.computeEmbedding(inputs, opts).embedding
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                // an NPU build is compiled for fixed shapes and may refuse an input: move to the GPU/CPU for good
                val fb = fallback ?: throw t
                fallback = null
                Log.w(TAG, "$modelId failed on $backend, moving to the GPU/CPU build", t)
                val (e, name) = fb()
                runCatching { engine.close() }
                engine = e; backend = name
                engine.computeEmbedding(inputs, opts).embedding
            }
            VectorMath.normalize(v)
        }
    }

    override fun close() = engine.close()

    companion object {
        private const val TAG = "OndolSaem.EG2"
        const val QUERY_PREFIX = "task: search result | query: "
        /** Low-power indexing: a couple of CPU threads instead of the GPU (slower, cooler, easier on the battery). */
        const val LOW_POWER_THREADS = 2
        private const val MAX_TITLE_CHARS = 200
        private const val MAX_QUERY_CHARS = 1000

        private val isEmulator: Boolean
            get() = Build.FINGERPRINT.contains("generic") || Build.HARDWARE.contains("ranchu") ||
                Build.HARDWARE.contains("goldfish")

        /**
         * Loads the model (can take ~10s; call off the main thread). With [npu], its NPU build first (vision and, on
         * the 740M, audio on the NPU too, else audio on the CPU); then the GPU on real devices, the CPU otherwise (or
         * 2 CPU threads when [lowPower], which skips the NPU).
         */
        suspend fun load(
            modelId: String, modelFile: File, cacheDir: File, lowPower: Boolean = false, npu: NpuTarget? = null,
        ): EmbeddingGemma2Embedder = withContext(Dispatchers.Default) {
            cacheDir.mkdirs()
            val audio = modelId == "eg2-740m" // only the 740M build has an audio encoder
            val npuTries = if (npu == null || lowPower) emptyList() else {
                val b = { Backend.NPU(nativeLibraryDir = npu.libDir) }
                listOfNotNull(Attempt(npu.file, b, b, if (audio) b else null), if (audio) Attempt(npu.file, b, b, { Backend.CPU() }) else null)
            }
            val plain = (if (lowPower) listOf({ Backend.CPU(threadCount = LOW_POWER_THREADS) })
                else if (isEmulator) listOf({ Backend.CPU() }) else listOf({ Backend.GPU() }, { Backend.CPU() }))
                .map { b -> Attempt(modelFile, b, b, if (audio) b else null) }
            val loadPlain: suspend () -> Pair<EmbeddingEngine, String> = { open(modelId, plain, cacheDir) }
            if (npuTries.isNotEmpty()) {
                runCatching { open(modelId, npuTries, cacheDir) }.getOrNull()?.let { (engine, name) ->
                    return@withContext EmbeddingGemma2Embedder(modelId, engine, name, loadPlain)
                }
            }
            val (engine, name) = loadPlain()
            EmbeddingGemma2Embedder(modelId, engine, name)
        }

        private class Attempt(val file: File, val main: () -> Backend, val vision: () -> Backend, val audio: (() -> Backend)?)

        /** The first [attempts] that initializes, with its backend name. */
        private fun open(modelId: String, attempts: List<Attempt>, cacheDir: File): Pair<EmbeddingEngine, String> {
            var last: Throwable? = null
            for (a in attempts) {
                val backend = a.main()
                val engine = EmbeddingEngine(
                    EmbeddingEngineConfig(
                        modelPath = a.file.absolutePath,
                        backend = backend,
                        visionBackend = a.vision(),
                        audioBackend = a.audio?.invoke(),
                        cacheDir = cacheDir.absolutePath,
                    ),
                )
                try {
                    engine.initialize()
                    Log.i(TAG, "loaded $modelId (${a.file.name}) on ${backend.name}")
                    return engine to backend.name
                } catch (t: Throwable) {
                    Log.w(TAG, "init of ${a.file.name} failed on ${backend.name}", t)
                    runCatching { engine.close() }
                    last = t
                }
            }
            throw IllegalStateException("Could not load embedding model", last)
        }
    }
}
