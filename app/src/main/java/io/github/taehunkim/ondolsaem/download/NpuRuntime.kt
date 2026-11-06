package io.github.taehunkim.ondolsaem.download

import android.content.Context
import android.util.Log
import io.github.taehunkim.ondolsaem.engine.NpuDevice
import io.github.taehunkim.ondolsaem.engine.NpuVendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * The folder of NPU libraries handed to LiteRT-LM (`Backend.NPU(nativeLibraryDir)`).
 *
 * Google Tensor and MediaTek: a folder with a copy of the vendor's LiteRT dispatch library from the APK
 * (`litert-npu-runtime-*`, which uses the vendor's libraries on the phone); a folder of its own so the runtime does
 * not try the other vendors' dispatch libraries.
 *
 * Qualcomm: the dispatch library needs Qualcomm's QNN runtime, which is not on the phone for apps and is not in the
 * APK (75 MB, Qualcomm's license). On first use the QNN runtime AAR is downloaded from Maven Central, only the
 * libraries for this chip's Hexagon version are kept (about 25 MB), and the dispatch library is copied next to them:
 * the dispatch library points the DSP (`ADSP_LIBRARY_PATH`) at its own folder.
 */
class NpuRuntime(
    private val context: Context,
    private val downloader: ModelDownloader,
    private val root: File = File(context.filesDir, "npu"),
) {
    /** True when [device] needs [install] before its NPU can be used. */
    fun needsInstall(device: NpuDevice): Boolean = device.vendor == NpuVendor.QUALCOMM && !qnnDir(device).let { File(it, DONE).exists() }

    /** The library folder for [device], or null while the Qualcomm runtime is not installed. */
    fun libDir(device: NpuDevice): String? = when (device.vendor) {
        NpuVendor.QUALCOMM -> qnnDir(device).takeIf { File(it, DONE).exists() }?.absolutePath
        else -> runCatching {
            val name = dispatchLibrary(device.vendor)
            val dir = File(root, device.vendor.name.lowercase()).apply { mkdirs() }
            val src = File(context.applicationInfo.nativeLibraryDir, name)
            val dst = File(dir, name)
            // refreshed after an app update (the APK's copy may change)
            if (!dst.exists() || dst.length() != src.length() || dst.lastModified() < src.lastModified()) src.copyTo(dst, overwrite = true)
            dir.absolutePath
        }.onFailure { Log.w(TAG, "no NPU dispatch library for ${device.vendor}", it) }.getOrNull()
    }

    /** Bytes kept on the phone for [device]'s runtime (0 when it needs none or it is not installed). */
    fun installedBytes(device: NpuDevice): Long =
        if (device.vendor != NpuVendor.QUALCOMM) 0 else qnnDir(device).listFiles().orEmpty().sumOf { it.length() }

    private fun qnnDir(device: NpuDevice) = File(root, "qnn-$QNN_VERSION-${device.hexagon}")

    /** Downloads the QNN runtime and unpacks this chip's libraries (no-op when installed or not needed). */
    suspend fun install(device: NpuDevice, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        if (!needsInstall(device)) return@withContext
        val hex = device.hexagon ?: error("no Hexagon version for ${device.soc}")
        val aar = File(context.cacheDir, "qnn-runtime-$QNN_VERSION.aar")
        downloader.download(QNN_URL, aar, null, onProgress)
        Checksums.verify(aar, QNN_SHA256)
        val dir = qnnDir(device)
        dir.deleteRecursively(); dir.mkdirs()
        try {
            ZipFile(aar).use { zip ->
                for (name in qnnLibraries(hex)) {
                    val e = zip.getEntry("jni/arm64-v8a/$name") ?: throw DownloadException(DownloadException.Kind.CORRUPT, "$name missing in the QNN runtime")
                    zip.getInputStream(e).use { i -> File(dir, name).outputStream().use { i.copyTo(it) } }
                }
            }
            File(context.applicationInfo.nativeLibraryDir, DISPATCH).copyTo(File(dir, DISPATCH), overwrite = true)
            File(dir, DONE).writeText(QNN_VERSION)
            Log.i(TAG, "QNN $QNN_VERSION ($hex) installed: ${dir.listFiles().orEmpty().map { it.name }}")
        } catch (t: Throwable) {
            dir.deleteRecursively()
            throw t
        } finally {
            aar.delete()
        }
        // older versions or other Hexagon builds left behind
        root.listFiles().orEmpty().filter { it != dir && it.name.startsWith("qnn-") }.forEach { it.deleteRecursively() }
    }

    fun delete() { root.deleteRecursively() }

    companion object {
        private const val TAG = "OndolSaem.Npu"
        const val QNN_VERSION = "2.51.0"
        const val QNN_URL = "https://repo1.maven.org/maven2/com/qualcomm/qti/qnn-runtime/$QNN_VERSION/qnn-runtime-$QNN_VERSION.aar"
        const val QNN_SHA256 = "6388face1d5aa5e8958d745e5a547e92b5bec6fc332dfb1b438b1e17ffa02861"
        /** Download size of the QNN runtime AAR. */
        const val QNN_DOWNLOAD_BYTES = 75_297_348L
        const val DISPATCH = "libLiteRtDispatch_Qualcomm.so"

        fun dispatchLibrary(vendor: NpuVendor) = when (vendor) {
            NpuVendor.GOOGLE_TENSOR -> "libLiteRtDispatch_GoogleTensor.so"
            NpuVendor.MEDIATEK -> "libLiteRtDispatch_MediaTek.so"
            NpuVendor.QUALCOMM -> DISPATCH
        }
        private const val DONE = ".installed"

        /** HTP backend, system library, and the CPU stub and DSP skeleton of one Hexagon version (no JIT compiler). */
        fun qnnLibraries(hexagon: String) = listOf("libQnnHtp.so", "libQnnSystem.so", "libQnnHtp${hexagon}Stub.so", "libQnnHtp${hexagon}Skel.so")
    }
}
