package io.github.taehunkim.ondolsaem.engine

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs

/**
 * Turns the fp32 decision model into weight-only int8 on the phone (the published int8 builds give wrong answers on
 * the ONNX Runtime bundled with sherpa-onnx, see `scripts/make-nli-w8-template.py`).
 *
 * The converted graph ships in the APK (`assets/nli-w8/model.onnx`, every initializer external in `weights.bin`);
 * `recipe.tsv` says how to make each initializer from the fp32 file's tensors. This class reads the fp32 ONNX file
 * (protobuf, only the initializers' raw data), applies the recipe and writes `weights.bin`:
 * - `copy`: the fp32 tensor as is;
 * - `qcol` / `scol`: a MatMul weight [K, N] to int8 with one scale per column (max |w| / 127), and the scales;
 * - `qrow` / `srow`: the embedding table [V, D] to int8 with one scale per row, and the scales [V, 1].
 * Rounding is half-to-even like numpy, so the result matches the script's desktop check.
 */
object NliW8Converter {
    data class Step(val name: String, val op: String, val source: String, val offset: Long, val length: Long)

    fun parseRecipe(text: String): List<Step> = text.lines().filter { it.isNotBlank() }.map { l ->
        val p = l.split('\t')
        Step(p[0], p[1], p[2], p[3].toLong(), p[4].toLong())
    }

    /** Writes [weights] from [fp32] following [recipe]; [onProgress] gets bytes written and the total. */
    fun convert(fp32: File, recipe: List<Step>, weights: File, onProgress: (Long, Long) -> Unit = { _, _ -> }) {
        val tensors = OnnxTensors.index(fp32)
        val total = recipe.sumOf { it.length }
        val part = File(weights.path + ".part")
        RandomAccessFile(fp32, "r").use { raf ->
            val ch = raf.channel
            BufferedOutputStream(FileOutputStream(part), 1 shl 20).use { out ->
                var written = 0L
                val scales = HashMap<String, FloatArray>() // computed with the int8 values, written by scol/srow
                for (s in recipe) {
                    check(s.offset == written) { "recipe out of order at ${s.name}" }
                    val t = tensors[s.source] ?: error("no tensor ${s.source} in ${fp32.name}")
                    when (s.op) {
                        "copy" -> copy(ch, t, out)
                        "qcol" -> scales[s.source] = quantizeColumns(ch, t, out)
                        "qrow" -> scales[s.source] = quantizeRows(ch, t, out)
                        "scol", "srow" -> writeFloats(scales.remove(s.source) ?: error("scales of ${s.source} before its values"), out)
                        else -> error("unknown op ${s.op}")
                    }
                    written += s.length
                    onProgress(written, total)
                }
            }
        }
        check(part.length() == total) { "wrote ${part.length()} bytes, expected $total" }
        if (!part.renameTo(weights)) error("cannot rename ${part.name}")
    }

    private fun read(ch: FileChannel, at: Long, n: Int): ByteBuffer {
        val b = ByteBuffer.allocate(n).order(ByteOrder.LITTLE_ENDIAN)
        var pos = at
        while (b.hasRemaining()) { val r = ch.read(b, pos); check(r > 0) { "unexpected end of file" }; pos += r }
        b.flip()
        return b
    }

    private fun copy(ch: FileChannel, t: OnnxTensors.Tensor, out: BufferedOutputStream) {
        var done = 0L
        while (done < t.length) {
            val n = minOf(CHUNK.toLong(), t.length - done).toInt()
            val b = read(ch, t.offset + done, n)
            out.write(b.array(), 0, n)
            done += n
        }
    }

    private fun quantizeColumns(ch: FileChannel, t: OnnxTensors.Tensor, out: BufferedOutputStream): FloatArray {
        val (k, n) = t.dims.map { it.toInt() }
        val w = FloatArray(k * n).also { read(ch, t.offset, k * n * 4).asFloatBuffer().get(it) }
        val s = FloatArray(n)
        for (r in 0 until k) for (c in 0 until n) s[c] = maxOf(s[c], abs(w[r * n + c]))
        for (c in 0 until n) s[c] = if (s[c] == 0f) 1e-8f else s[c] / 127f
        val q = ByteArray(k * n)
        for (r in 0 until k) for (c in 0 until n) q[r * n + c] = q8(w[r * n + c] / s[c])
        out.write(q)
        return s
    }

    private fun quantizeRows(ch: FileChannel, t: OnnxTensors.Tensor, out: BufferedOutputStream): FloatArray {
        val (v, d) = t.dims.map { it.toInt() }
        val s = FloatArray(v)
        val rowsPerRead = maxOf(1, CHUNK / (d * 4))
        val row = FloatArray(d)
        val q = ByteArray(rowsPerRead * d)
        var r0 = 0
        while (r0 < v) {
            val rows = minOf(rowsPerRead, v - r0)
            val fb = read(ch, t.offset + r0.toLong() * d * 4, rows * d * 4).asFloatBuffer()
            for (i in 0 until rows) {
                fb.get(row)
                var m = 0f
                for (x in row) m = maxOf(m, abs(x))
                val sc = if (m == 0f) 1e-8f else m / 127f
                s[r0 + i] = sc
                for (j in 0 until d) q[i * d + j] = q8(row[j] / sc)
            }
            out.write(q, 0, rows * d)
            r0 += rows
        }
        return s
    }

    private fun q8(x: Float): Byte = Math.rint(x.toDouble()).coerceIn(-127.0, 127.0).toInt().toByte()

    private fun writeFloats(f: FloatArray, out: BufferedOutputStream) {
        val b = ByteBuffer.allocate(f.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        b.asFloatBuffer().put(f)
        out.write(b.array())
    }

    private const val CHUNK = 4 shl 20
}

/** Finds the initializers of an ONNX file (ModelProto.graph.initializer) and where their raw data lies. */
object OnnxTensors {
    data class Tensor(val name: String, val dataType: Int, val dims: List<Long>, val offset: Long, val length: Long)

    fun index(file: File): Map<String, Tensor> = RandomAccessFile(file, "r").use { raf ->
        val r = Reader(raf)
        val out = HashMap<String, Tensor>()
        r.fields(0, raf.length()) { field, wire, start, len ->
            if (field == 7 && wire == 2) { // graph
                r.fields(start, start + len) { gf, gw, gs, gl ->
                    if (gf == 5 && gw == 2) tensor(r, gs, gl)?.let { out[it.name] = it } // initializer
                }
            }
        }
        out
    }

    private fun tensor(r: Reader, start: Long, len: Long): Tensor? {
        var name = ""; var type = 0; val dims = ArrayList<Long>(); var raw = -1L; var rawLen = 0L
        r.fields(start, start + len) { f, w, s, l ->
            when {
                f == 1 && w == 0 -> dims.add(s) // varint value passed as start
                f == 1 && w == 2 -> { var p = s; while (p < s + l) { val (v, n) = r.varint(p); dims.add(v); p += n } }
                f == 2 && w == 0 -> type = s.toInt()
                f == 8 && w == 2 -> name = String(r.bytes(s, l.toInt()), Charsets.UTF_8)
                f == 9 && w == 2 -> { raw = s; rawLen = l }
            }
        }
        return if (raw < 0) null else Tensor(name, type, dims, raw, rawLen)
    }

    /** Minimal protobuf reader over a file: wire types 0 (varint), 1, 2 (length-delimited) and 5. */
    class Reader(private val raf: RandomAccessFile) {
        private val buf = ByteArray(16)

        fun varint(at: Long): Pair<Long, Int> {
            raf.seek(at)
            val n = raf.read(buf, 0, 10)
            var v = 0L; var shift = 0
            for (i in 0 until n) {
                val b = buf[i].toInt() and 0xff
                v = v or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return v to i + 1
                shift += 7
            }
            error("bad varint at $at")
        }

        fun bytes(at: Long, n: Int): ByteArray = ByteArray(n).also { raf.seek(at); raf.readFully(it) }

        /**
         * Calls [onField] for each field between [from] and [to]: for wire type 0 the value is passed as `start`
         * (length 0); for wire type 2 `start` / `len` locate the payload.
         */
        fun fields(from: Long, to: Long, onField: (field: Int, wire: Int, start: Long, len: Long) -> Unit) {
            var p = from
            while (p < to) {
                val (key, kn) = varint(p); p += kn
                val field = (key ushr 3).toInt(); val wire = (key and 7).toInt()
                when (wire) {
                    0 -> { val (v, n) = varint(p); p += n; onField(field, 0, v, 0) }
                    1 -> p += 8
                    2 -> { val (l, n) = varint(p); p += n; onField(field, 2, p, l); p += l }
                    5 -> p += 4
                    else -> error("unsupported wire type $wire at $p")
                }
            }
        }
    }
}
