package io.github.taehunkim.ondolsaem.engine

import java.io.File

/**
 * One ONNX model (float32 in, float32 out) on the ONNX Runtime bundled with sherpa-onnx, through the small JNI
 * bridge in `src/main/cpp` (a second ONNX Runtime cannot be added to the APK: both would ship libonnxruntime.so with
 * per-release symbol versions). Not thread-safe; callers serialize (the host's PriorityGate does).
 */
class OrtModel(model: File, threads: Int = 2) : AutoCloseable {
    private var handle: Long = nativeOpen(model.absolutePath, threads)

    /** Runs the model's first input → first output. */
    fun run(input: FloatArray, shape: LongArray): FloatArray {
        check(handle != 0L) { "model closed" }
        return nativeRun(handle, input, shape) ?: error("ONNX Runtime returned no output")
    }

    override fun close() { if (handle != 0L) { nativeClose(handle); handle = 0L } }

    companion object {
        init {
            System.loadLibrary("onnxruntime") // sherpa-onnx's copy; the bridge finds it with dlopen
            System.loadLibrary("ortbridge")
        }
        @JvmStatic private external fun nativeOpen(path: String, threads: Int): Long
        @JvmStatic private external fun nativeRun(handle: Long, input: FloatArray, shape: LongArray): FloatArray?
        @JvmStatic private external fun nativeClose(handle: Long)
    }
}
