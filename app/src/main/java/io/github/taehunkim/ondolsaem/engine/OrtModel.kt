package io.github.taehunkim.ondolsaem.engine

import java.io.File

/**
 * One ONNX model (float32 in, or several int64 inputs; float32 out) on the ONNX Runtime bundled with sherpa-onnx, through the small JNI
 * bridge in `src/main/cpp` (a second ONNX Runtime cannot be added to the APK: both would ship libonnxruntime.so with
 * per-release symbol versions). Not thread-safe; callers serialize (the host's PriorityGate does).
 */
class OrtModel(model: File, threads: Int = 2, optimization: Int = OPT_ALL) : AutoCloseable {
    private var handle: Long = nativeOpen(model.absolutePath, threads, optimization)

    /** Runs the model's first input → first output. */
    fun run(input: FloatArray, shape: LongArray): FloatArray {
        check(handle != 0L) { "model closed" }
        return nativeRun(handle, input, shape) ?: error("ONNX Runtime returned no output")
    }

    /** Runs the model on several int64 inputs of one [shape] ([inputs] by input name); returns the first output. */
    fun runLong(inputs: Map<String, LongArray>, shape: LongArray): FloatArray {
        check(handle != 0L) { "model closed" }
        val names = inputs.keys.toTypedArray()
        val data = LongArray(inputs.values.sumOf { it.size }).also { out ->
            var at = 0
            for (n in names) { val v = inputs.getValue(n); v.copyInto(out, at); at += v.size }
        }
        return nativeRunLong(handle, names, data, shape) ?: error("ONNX Runtime returned no output")
    }

    override fun close() { if (handle != 0L) { nativeClose(handle); handle = 0L } }

    companion object {
        init {
            System.loadLibrary("onnxruntime") // sherpa-onnx's copy; the bridge finds it with dlopen
            System.loadLibrary("ortbridge")
        }
        /** ONNX Runtime graph optimization levels (GraphOptimizationLevel). */
        const val OPT_NONE = 0
        const val OPT_BASIC = 1
        const val OPT_EXTENDED = 2
        const val OPT_ALL = 99

        @JvmStatic private external fun nativeOpen(path: String, threads: Int, optLevel: Int): Long
        @JvmStatic private external fun nativeRun(handle: Long, input: FloatArray, shape: LongArray): FloatArray?
        @JvmStatic private external fun nativeRunLong(handle: Long, names: Array<String>, data: LongArray, shape: LongArray): FloatArray?
        @JvmStatic private external fun nativeClose(handle: Long)
    }
}
