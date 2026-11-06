package io.github.taehunkim.ondolsaem.engine

import android.os.Build
import java.io.File

/** NPU vendors LiteRT-LM can dispatch to on Android (the dispatch libraries come from `litert-npu-runtime-*`). */
enum class NpuVendor { GOOGLE_TENSOR, QUALCOMM, MEDIATEK }

/**
 * This phone's chip as NPU builds name it: [soc] is "SM8750", "MT6991" or "TENSOR_G5"; [hexagon] the Hexagon
 * version of a Qualcomm chip, which picks the DSP libraries of the QNN runtime (null when unknown).
 */
data class NpuDevice(val soc: String, val vendor: NpuVendor, val hexagon: String? = null) {
    /** False for a Qualcomm chip whose Hexagon version is unknown: its QNN libraries cannot be picked. */
    val usable: Boolean get() = vendor != NpuVendor.QUALCOMM || hexagon != null

    /** [soc] in upper-case letters and digits only, compared with the end of NPU build file names. */
    val key: String get() = soc.uppercase().filter { it.isLetterOrDigit() }
}

/** One backend only, no fallback: the NPU self-check loads each backend on its own to compare them. */
enum class OnlyBackend { CPU, GPU, NPU }

/** Where the runtime finds an NPU build of a model: the compiled file and the folder of the NPU libraries. */
data class NpuTarget(val file: File, val libDir: String)

object Npu {
    /**
     * Hexagon (HTP) version of Qualcomm chips, for the QNN runtime's stub and skeleton libraries. Add chips as NPU
     * builds appear for them; a chip missing here is shown as not usable.
     */
    private val HEXAGON = mapOf(
        "SM8450" to "V69", "SM8475" to "V69", "SM8550" to "V73", "SM8650" to "V75", "SM8750" to "V79",
        "SM8850" to "V81", "QCS8275" to "V75",
    )
    private val QUALCOMM = Regex("^(SM|QCS|QCM)\\d{4}")
    private val MEDIATEK = Regex("^MT\\d{4}")
    private val TENSOR = Regex("TENSORG(\\d+)")

    /** This phone's chip (Android 12+ reports it), whether or not NPU builds exist for it. */
    val device: NpuDevice? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null else parse(Build.SOC_MODEL)
    }

    /**
     * Maps `Build.SOC_MODEL` to a chip: "SM8750" (also "SM8750-3-AB" and the like), "MT6991", "Tensor G5" →
     * `TENSOR_G5`; null for chips without a LiteRT NPU (Exynos, emulators, ...).
     */
    fun parse(model: String?): NpuDevice? {
        val m = model.orEmpty().uppercase().replace(" ", "").replace("_", "")
        QUALCOMM.find(m)?.let { return NpuDevice(it.value, NpuVendor.QUALCOMM, HEXAGON[it.value]) }
        MEDIATEK.find(m)?.let { return NpuDevice(it.value, NpuVendor.MEDIATEK) }
        TENSOR.find(m)?.let { return NpuDevice("TENSOR_G${it.groupValues[1]}", NpuVendor.GOOGLE_TENSOR) }
        return null
    }
}

/**
 * Whether LiteRT registered its NPU accelerator. `Backend.NPU` does not fail when the accelerator cannot be loaded:
 * LiteRT logs "NPU accelerator could not be loaded and registered" and runs what it can on the CPU, so such a load is
 * treated as failed. A registered accelerator is not proof either: a file without NPU-compiled parts still runs on the
 * CPU (seen on the emulator: the same vectors as the CPU, slower), which the self-check flags. Reads this process's
 * own log since [sinceMillis].
 */
object NpuLog {
    /** True: registered; false: not registered (CPU in disguise); null: the log said neither or was unreadable. */
    fun registered(sinceMillis: Long): Boolean? = runCatching {
        val since = "%d.%03d".format(sinceMillis / 1000, sinceMillis % 1000)
        val p = ProcessBuilder("logcat", "-d", "-t", since, "-v", "brief", "--pid=${android.os.Process.myPid()}", "litert:*", "*:S")
            .redirectErrorStream(true).start()
        val lines = p.inputStream.bufferedReader().readLines()
        when {
            lines.any { it.contains("NPU accelerator could not be loaded") } -> false
            lines.any { it.contains("NPU accelerator registered") } -> true
            else -> null
        }
    }.getOrNull()
}
