package io.github.taehunkim.ondolsaem.engine

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class UnigramTokenizerTest {
    private val tiny = UnigramTokenizer(
        listOf("[PAD]" to 0f, "[CLS]" to 0f, "[SEP]" to 0f, "[UNK]" to 0f, "▁" to -2f, "▁a" to -1f, "b" to -3f, "ab" to -2.5f, "▁ab" to -1.5f, "c" to -4f),
        unkId = 3,
    )

    @Test fun viterbiPicksTheBestSplit() {
        assertArrayEquals(intArrayOf(8), tiny.encode("ab")) // ▁ab (-1.5) beats ▁a + b (-4)
        assertArrayEquals(intArrayOf(8, 9), tiny.encode("abc"))
    }

    @Test fun unknownRunsFuseIntoOneId() {
        assertArrayEquals(intArrayOf(5, 3, 9), tiny.encode("a한글c"))
    }

    @Test fun pairTemplateCutsTheFirstTextFirst() {
        val ids = tiny.encodePair("ab ab ab ab", "c", maxTokens = 6)
        assertArrayEquals(intArrayOf(1, 8, 2, 4, 9, 2), ids) // "c" is ▁ + c
    }

    @Test fun whitespaceRunsBecomeOneWordBreak() {
        assertEquals(listOf("▁a", "▁b"), UnigramTokenizer.preTokenize(UnigramTokenizer.normalize("  a \t\n b ")))
    }

    /** Parity with Hugging Face tokenizers on the real vocabulary (NLI_TOKENIZER=path/to/tokenizer.json). */
    @Test fun matchesReferenceTokenizer() {
        val path = System.getenv("NLI_TOKENIZER")
        assumeTrue("NLI_TOKENIZER not set", path != null && File(path).exists())
        val tok = UnigramTokenizer.load(File(path!!))
        val lines = javaClass.getResource("/tokenizer-fixtures.tsv")!!.readText().lines().filter { it.isNotBlank() }
        for (line in lines) {
            val (kind, a, b, max, ids) = line.split('\t')
            val expected = if (ids.isEmpty()) IntArray(0) else ids.split(',').map { it.toInt() }.toIntArray()
            val actual = if (kind == "single") tok.encode(hex(a)) else tok.encodePair(hex(a), hex(b), max.toInt())
            assertArrayEquals("for ${hex(a).take(40)}", expected, actual)
        }
    }

    private fun hex(s: String) = String(s.chunked(2).map { it.toInt(16).toByte() }.toByteArray(), Charsets.UTF_8)
}
