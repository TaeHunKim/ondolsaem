package io.github.taehunkim.ondolsaem.runtime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Process
import io.github.taehunkim.ondolsaem.BuildConfig
import io.github.taehunkim.ondolsaem.catalog.ModelCatalog
import io.github.taehunkim.ondolsaem.download.ModelManager
import io.github.taehunkim.ondolsaem.download.NpuRuntime
import io.github.taehunkim.ondolsaem.engine.ChatEvent
import io.github.taehunkim.ondolsaem.engine.ChatTurn
import io.github.taehunkim.ondolsaem.engine.EmbeddingGemma2Embedder
import io.github.taehunkim.ondolsaem.engine.LiteRtChatModel
import io.github.taehunkim.ondolsaem.engine.NpuLog
import io.github.taehunkim.ondolsaem.engine.OnlyBackend
import io.github.taehunkim.ondolsaem.engine.TurnRole
import io.github.taehunkim.ondolsaem.sdk.OndolSaem.Categories
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sqrt

/**
 * The NPU self-check (Settings › NPU › "Check the NPU"): a plain-text report the user can copy, for phones that cannot
 * be connected to a computer. For each downloaded model with an NPU build it loads the CPU, GPU and NPU builds one at a
 * time (no fallback), embeds the same texts, queries and a picture, and compares each vector with the CPU one
 * (cosine; clients keep their index when it stays ≥ [MIN_COS]); a chat model's NPU build is loaded and asked a short
 * question. Errors are reported with their causes, followed by this process's LiteRT / QNN log lines.
 */
class NpuSelfCheck(private val context: Context, private val manager: ModelManager) {
    private val cache get() = File(context.cacheDir, "litertlm")

    suspend fun run(useNpu: Boolean, onLine: (String) -> Unit): String = withContext(Dispatchers.Default) {
        val out = StringBuilder()
        fun line(s: String = "") { out.append(s).append('\n'); onLine(out.toString()) }

        line("# OndolSaem NPU check")
        line("app ${BuildConfig.VERSION_NAME}, ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date())}")
        line("phone ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else "(Android 11 or older: not reported)"
        line("chip $soc -> ${manager.npuDevice ?: "no LiteRT NPU"}${manager.npuDevice?.let { if (it.usable) "" else " (Hexagon version unknown: not usable)" }.orEmpty()}")
        line("NPU use: ${if (useNpu) "on" else "off"}")
        val device = manager.npuDevice
        if (device == null || !device.usable) { line(); line("Nothing to check on this phone."); return@withContext out.toString() }

        val search = manager.npuCheck.value.result
        line("search: ${search?.let { "at ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(it.checkedAt))}, found ${it.builds.mapValues { b -> b.value.fileName }}" } ?: "not done"}")
        val libDir = manager.npuRuntime.libDir(device)
        line("libraries: ${libDir ?: "not installed"}")
        libDir?.let { d -> File(d).listFiles().orEmpty().sortedBy { it.name }.forEach { line("  ${it.name} ${it.length()}") } }
        if (device.vendor.name == "QUALCOMM") line("QNN runtime ${NpuRuntime.QNN_VERSION}, Hexagon ${device.hexagon}")

        val models = ModelCatalog.all.filter { manager.isReady(it) && manager.npuTarget(it) != null }
        if (models.isEmpty()) {
            line(); line("No model with a downloaded NPU build: download one in Models (\"Download NPU build\" under an installed model).")
        }
        for (spec in models) {
            line()
            val target = manager.npuTarget(spec)!!
            line("## ${spec.id}: ${target.file.name} (${target.file.length()} bytes)")
            when (spec.category) {
                Categories.EMBEDDING -> checkEmbedding(spec.id, manager.file(spec)!!, target, ::line)
                Categories.LLM -> checkChat(spec.vision, target, ::line)
            }
        }
        line()
        line("## log (LiteRT, QNN, OndolSaem; this process)")
        logLines().forEach { line(it) }
        out.toString()
    }

    internal suspend fun checkEmbedding(id: String, file: File, target: io.github.taehunkim.ondolsaem.engine.NpuTarget, line: (String) -> Unit) {
        fun input(name: String, f: suspend (EmbeddingGemma2Embedder) -> FloatArray) = name to f
        val inputs = TEXTS.map { t -> input("doc '${t.take(14)}'") { it.embedDocument(t, null) } } +
            TEXTS.take(2).map { t -> input("query '${t.take(14)}'") { it.embedQuery(t) } } +
            input("image 512") { it.embedImage(testImage()) } +
            input("long doc ${LONG.length} chars") { it.embedDocument(LONG, "long") }
        val vectors = HashMap<OnlyBackend, List<FloatArray?>>()
        for (b in OnlyBackend.entries) {
            val t0 = System.nanoTime()
            val e = try {
                EmbeddingGemma2Embedder.load(id, file, cache, npu = target, only = b)
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                line("$b: load failed: ${describe(t)}")
                continue
            }
            line("$b: loaded in ${ms(t0)} ms (${e.backend})" + if (b == OnlyBackend.NPU) ", NPU accelerator registered: ${NpuLog.registered(System.currentTimeMillis() - ms(t0) - 100) ?: "unknown"}" else "")
            try {
                vectors[b] = inputs.map { (name, f) ->
                    try {
                        val t1 = System.nanoTime(); val v = f(e); val first = ms(t1)
                        val t2 = System.nanoTime(); f(e); val next = ms(t2)
                        line("  $name: $first ms first, $next ms next")
                        v
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        line("  $name: failed: ${describe(t)}"); null
                    }
                }
            } finally { e.close() }
        }
        val cpu = vectors[OnlyBackend.CPU] ?: run { line("no CPU vectors to compare with"); return }
        val worst = HashMap<OnlyBackend, Double>()
        for (b in listOf(OnlyBackend.GPU, OnlyBackend.NPU)) {
            val v = vectors[b] ?: continue
            val cos = inputs.indices.mapNotNull { i -> val a = cpu[i]; val c = v[i]; if (a != null && c != null) inputs[i].first to cos(a, c) else null }
            if (cos.isEmpty()) continue
            line("cos(CPU, $b): " + cos.joinToString { "%s %.4f".format(it.first.substringBefore(' '), it.second) })
            worst[b] = cos.minOf { it.second }
        }
        val npu = worst[OnlyBackend.NPU] ?: run { line("NPU: no result for $id"); return }
        // the GPU already differs from the CPU a little (fp16); the NPU may differ as much without a re-index
        val limit = minOf(MIN_COS, (worst[OnlyBackend.GPU] ?: MIN_COS) - 0.01)
        line(
            "NPU verdict for %s: worst cos %.4f (GPU %s, limit %.4f) -> %s".format(
                id, npu, worst[OnlyBackend.GPU]?.let { "%.4f".format(it) } ?: "-", limit,
                when {
                    npu >= 0.99999 -> "IDENTICAL to the CPU: the NPU did not run (no NPU-compiled parts in the file?)"
                    npu >= limit -> "OK, same index"
                    else -> "DIFFERENT, clients would need to re-index"
                },
            ),
        )
    }

    private suspend fun checkChat(vision: Boolean, target: io.github.taehunkim.ondolsaem.engine.NpuTarget, line: (String) -> Unit) {
        val t0 = System.nanoTime()
        val m = try {
            LiteRtChatModel.load(target.file, cache, vision, 2048, target, npuOnly = true)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            line("NPU: load failed: ${describe(t)}"); return
        }
        line("NPU: loaded in ${ms(t0)} ms (${m.backend}), NPU accelerator registered: ${NpuLog.registered(System.currentTimeMillis() - ms(t0) - 100) ?: "unknown"}")
        try {
            val t1 = System.nanoTime()
            var first = 0L; var n = 0; val text = StringBuilder()
            m.reply("npu-check", "Answer briefly.", emptyList(), ChatTurn(TurnRole.USER, "대한민국의 수도는 어디인가요? 한 문장으로 답하세요."), emptyList())
                .takeWhile { n < 40 }
                .collect { ev -> if (ev is ChatEvent.Token) { if (n == 0) first = System.nanoTime(); n++; text.append(ev.text) } }
            val decode = if (n > 1) (n - 1) * 1e9 / (System.nanoTime() - first) else 0.0
            line("  first token ${(first - t1) / 1_000_000} ms, %.1f tokens/s, answer: %s".format(decode, text.toString().trim().take(120)))
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            line("  reply failed: ${describe(t)}")
        } finally { m.close() }
    }

    private fun ms(from: Long) = (System.nanoTime() - from) / 1_000_000

    private fun describe(t: Throwable): String = generateSequence(t) { it.cause }.take(5)
        .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message?.take(300)}" }

    private fun cos(a: FloatArray, b: FloatArray): Double {
        var d = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { d += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return d / sqrt(na * nb)
    }

    private fun testImage(): ByteArray {
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply {
            drawColor(Color.rgb(250, 240, 220))
            drawCircle(256f, 256f, 160f, Paint().apply { color = Color.rgb(220, 60, 40) })
            drawText("EXIT 12", 180f, 470f, Paint().apply { color = Color.BLACK; textSize = 48f })
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    /** This process's log lines from LiteRT, QNN and OndolSaem (an app may read its own log). */
    private fun logLines(): List<String> = runCatching {
        val p = ProcessBuilder("logcat", "-d", "-v", "brief", "--pid=${Process.myPid()}").redirectErrorStream(true).start()
        p.inputStream.bufferedReader().readLines()
            .filter { l -> KEYS.any { l.contains(it, ignoreCase = true) } }
            .takeLast(80)
    }.getOrElse { listOf("(log not readable: ${it.message})") }

    private companion object {
        const val MIN_COS = 0.98
        val KEYS = listOf("litert", "tflite", "qnn", "dispatch", "npu", "OndolSaem.EG2", "OndolSaem.Chat", "OndolSaem.Npu", "adsp", "fastrpc")
        val TEXTS = listOf(
            "오늘 점심값 12,000원 카드 영수증. 김밥천국 강남점.",
            "다음 주 제주도 3박 4일 일정: 첫날 성산일출봉, 둘째 날 한라산 등반.",
            "Quarterly sales report draft due Friday.",
            "고양이가 소파 위에서 자고 있다",
        )
        val LONG = (1..40).joinToString(" ") { "회의록 $it: 다음 분기 출시 일정과 예산, 담당자 배정을 논의했다." }
    }
}
