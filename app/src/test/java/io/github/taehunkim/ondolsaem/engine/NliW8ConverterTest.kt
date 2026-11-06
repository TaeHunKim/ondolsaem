package io.github.taehunkim.ondolsaem.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

class NliW8ConverterTest {
    @get:Rule val tmp = TemporaryFolder()

    /** A ModelProto with graph.initializer = one fp32 tensor "w" [2, 3] in raw_data. */
    private fun tinyModel(values: FloatArray): File {
        fun varint(v: Long) = buildList { var x = v; while (x >= 0x80) { add(((x and 0x7f) or 0x80).toByte()); x = x ushr 7 }; add(x.toByte()) }
        fun field(f: Int, wire: Int) = varint(((f shl 3) or wire).toLong())
        fun len(f: Int, payload: List<Byte>) = field(f, 2) + varint(payload.size.toLong()) + payload
        val raw = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply { asFloatBuffer().put(values) }.array().toList()
        val tensor = field(1, 0) + varint(2) + field(1, 0) + varint(3) + field(2, 0) + varint(1) + len(8, "w".toByteArray().toList()) + len(9, raw)
        val graph = len(1, listOf(1, 2)) /* a node, skipped */ + len(5, tensor)
        val model = field(1, 0) + varint(8) + len(7, graph)
        return tmp.newFile("m.onnx").apply { writeBytes(model.toByteArray()) }
    }

    @Test fun findsInitializersAndQuantizesColumnsAndRows() {
        val w = floatArrayOf(1f, -2f, 0f, 0.5f, 4f, 0f)
        val f = tinyModel(w)
        val t = OnnxTensors.index(f).getValue("w")
        assertEquals(listOf(2L, 3L), t.dims)
        assertEquals(24L, t.length)
        val recipe = NliW8Converter.parseRecipe("w_q\tqcol\tw\t0\t6\nw_s\tscol\tw\t6\t12\nw_r\tqrow\tw\t18\t6\nw_t\tsrow\tw\t24\t8\nw\tcopy\tw\t32\t24\n")
        val out = tmp.root.resolve("weights.bin")
        NliW8Converter.convert(f, recipe, out)
        val b = ByteBuffer.wrap(out.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        // columns: max |w| = 1, 4, 0 → scales 1/127, 4/127, 1e-8
        assertEquals(listOf<Byte>(127, -64, 0, 64, 127, 0), (0 until 6).map { b.get(it) })
        assertEquals(4f / 127f, b.getFloat(10), 1e-7f)
        // rows: max |w| = 2, 4
        assertEquals(listOf<Byte>(64, -127, 0, 16, 127, 0), (18 until 24).map { b.get(it) })
        assertEquals(-2f, b.getFloat(32 + 4), 0f)
        assertEquals(56L, out.length())
    }

    /** The real model, compared with the desktop conversion (scripts/make-nli-w8-template.py --check). */
    @Test fun matchesDesktopConversion() {
        val fp32 = System.getenv("NLI_FP32"); val check = System.getenv("NLI_W8_CHECK")
        assumeTrue("NLI_FP32 / NLI_W8_CHECK not set", fp32 != null && check != null && File(fp32).exists())
        val recipe = NliW8Converter.parseRecipe(File("src/main/assets/nli-w8/recipe.tsv").readText())
        val out = tmp.root.resolve("weights.bin")
        NliW8Converter.convert(File(fp32!!), recipe, out)
        assertTrue(out.length() == File(check!!).length())
        assertEquals(sha(File(check)), sha(out))
    }

    private fun sha(f: File) = MessageDigest.getInstance("SHA-256").let { md ->
        f.inputStream().use { i -> val buf = ByteArray(1 shl 20); while (true) { val n = i.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}
