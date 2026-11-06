package io.github.taehunkim.ondolsaem.engine

import android.os.Build
import java.io.File

/** NPU vendors LiteRT-LM can dispatch to on Android (the dispatch libraries come from `litert-npu-runtime-*`). */
enum class NpuVendor { GOOGLE_TENSOR, QUALCOMM, MEDIATEK }

/**
 * A phone chip with an NPU that published `.litertlm` files are compiled for. [soc] is the catalog key
 * ([io.github.taehunkim.ondolsaem.catalog.ModelSpec.npu]); [hexagon] the Hexagon version of a Qualcomm chip, which
 * picks the DSP libraries of the QNN runtime.
 */
data class NpuDevice(val soc: String, val vendor: NpuVendor, val hexagon: String? = null)

/** Where the runtime finds an NPU build of a model: the compiled file and the folder of the NPU libraries. */
data class NpuTarget(val file: File, val libDir: String)

object Npu {
    /** Qualcomm chips with published NPU files, and their Hexagon (HTP) version. */
    private val QUALCOMM = mapOf("SM8550" to "V73", "SM8650" to "V75", "SM8750" to "V79", "SM8850" to "V81")
    private val MEDIATEK = setOf("MT6991", "MT6993")
    private val TENSOR = mapOf("TENSORG5" to "TENSOR_G5", "TENSORG6" to "TENSOR_G6")

    /** This phone's chip, when an NPU build can exist for it (Android 12+, which reports the chip). */
    val device: NpuDevice? by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) null else parse(Build.SOC_MODEL)
    }

    /**
     * Maps `Build.SOC_MODEL` to a catalog key: "SM8750" (also "SM8750-3-AB" and the like), "MT6991",
     * "Tensor G5" → `TENSOR_G5`; null for other chips.
     */
    fun parse(model: String?): NpuDevice? {
        val m = model.orEmpty().uppercase().replace(" ", "").replace("_", "")
        QUALCOMM.entries.firstOrNull { m.startsWith(it.key) }?.let { return NpuDevice(it.key, NpuVendor.QUALCOMM, it.value) }
        MEDIATEK.firstOrNull { m.startsWith(it) }?.let { return NpuDevice(it, NpuVendor.MEDIATEK) }
        TENSOR.entries.firstOrNull { m.contains(it.key) }?.let { return NpuDevice(it.value, NpuVendor.GOOGLE_TENSOR) }
        return null
    }
}
