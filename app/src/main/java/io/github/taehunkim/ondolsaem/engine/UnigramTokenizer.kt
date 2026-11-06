package io.github.taehunkim.ondolsaem.engine

import java.io.File
import java.text.Normalizer

/**
 * SentencePiece unigram tokenizer read from a Hugging Face `tokenizer.json` (as used by mDeBERTa-v3): NFKC-like
 * normalization, Metaspace ("▁") pre-tokenization, Viterbi over piece scores, unknown runs fused into one [unkId],
 * and the pair template `[CLS] A [SEP] B [SEP]`.
 *
 * The file's "precompiled" normalizer is SentencePiece's nmt_nfkc table; [normalize] reproduces it with NFKC plus the
 * few differences measured against the reference (`scripts/make-tokenizer-fixtures.py`): control characters are
 * dropped, zero-width, bidi and line/paragraph separator characters become spaces, and whitespace runs become one
 * space (leading and trailing whitespace is removed).
 */
class UnigramTokenizer(pieces: List<Pair<String, Float>>, private val unkId: Int, val clsId: Int = 1, val sepId: Int = 2) {
    private val ids = HashMap<String, Int>(pieces.size * 2)
    private val scores = FloatArray(pieces.size)
    private val maxLen: Int
    private val unkScore: Float

    init {
        var longest = 1
        var min = Float.MAX_VALUE
        pieces.forEachIndexed { i, (p, s) ->
            // special tokens ([CLS], [PAD], ...) are never produced from text
            if (!(p.startsWith("[") && p.endsWith("]") && p.length > 2) && !(p.startsWith("<0x") && p.length == 6)) {
                ids.putIfAbsent(p, i)
                if (p.length > longest) longest = p.length
            }
            scores[i] = s
            if (s < min) min = s
        }
        maxLen = longest
        unkScore = min - 10f
    }

    /** Token ids of [text] without special tokens. */
    fun encode(text: String): IntArray {
        val out = ArrayList<Int>()
        for (word in preTokenize(normalize(text))) viterbi(word, out)
        return out.toIntArray()
    }

    /**
     * `[CLS] a [SEP] b [SEP]`, cutting [a] (then [b] if still needed) so the result has at most [maxTokens] ids.
     */
    fun encodePair(a: String, b: String, maxTokens: Int): IntArray {
        var x = encode(a)
        var y = encode(b)
        val room = maxTokens - 3
        if (x.size + y.size > room) {
            x = x.copyOf(maxOf(0, room - y.size))
            if (x.size + y.size > room) y = y.copyOf(maxOf(0, room - x.size))
        }
        return IntArray(x.size + y.size + 3).also { r ->
            r[0] = clsId
            x.copyInto(r, 1)
            r[x.size + 1] = sepId
            y.copyInto(r, x.size + 2)
            r[r.size - 1] = sepId
        }
    }

    private fun viterbi(word: String, out: MutableList<Int>) {
        val n = word.length
        val best = FloatArray(n + 1) { Float.NEGATIVE_INFINITY }
        val from = IntArray(n + 1) { -1 }
        val tok = IntArray(n + 1) { -1 } // -1: unknown
        best[0] = 0f
        var i = 0
        while (i < n) {
            if (best[i] == Float.NEGATIVE_INFINITY) { i++; continue }
            val cp = Character.charCount(word.codePointAt(i))
            var single = false
            val limit = minOf(n, i + maxLen)
            for (j in i + 1..limit) {
                if (j < n && Character.isLowSurrogate(word[j]) && Character.isHighSurrogate(word[j - 1])) continue
                val id = ids[word.substring(i, j)] ?: continue
                if (j - i == cp) single = true
                val s = best[i] + scores[id]
                if (s > best[j]) { best[j] = s; from[j] = i; tok[j] = id }
            }
            if (!single) {
                val j = i + cp
                val s = best[i] + unkScore
                if (s > best[j]) { best[j] = s; from[j] = i; tok[j] = -1 }
            }
            i += cp
        }
        val path = ArrayList<Int>()
        var j = n
        while (j > 0) { path.add(tok[j]); j = from[j] }
        var lastUnk = false
        for (k in path.indices.reversed()) {
            val t = path[k]
            if (t == -1) { if (!lastUnk) out.add(unkId); lastUnk = true } else { out.add(t); lastUnk = false }
        }
    }

    companion object {
        private const val SPACE = '\u2581'

        fun normalize(text: String): String {
            val sb = StringBuilder(text.length)
            fun space() { if (sb.isNotEmpty() && sb[sb.length - 1] != ' ') sb.append(' ') }
            for (ch in nfkc(text)) {
                when {
                    ch == '\t' || ch == '\n' || ch == '\r' || Character.isSpaceChar(ch) -> space()
                    ch < ' ' || ch in '\u007f'..'\u009f' -> Unit
                    ch in '\u200b'..'\u200f' || ch == '\ufeff' || ch == '\ufffd' || ch == SPACE -> space()
                    else -> sb.append(ch)
                }
            }
            return sb.toString().trimEnd()
        }

        /**
         * Code points the model's table leaves alone although NFKC maps them (newer Unicode than the table, and the
         * fullwidth tilde, common in Korean text), measured with `scripts/make-tokenizer-fixtures.py`.
         */
        private val KEEP = arrayOf(
            0x32FF..0x32FF, 0xA7F2..0xA7F4, 0xAB69..0xAB69, 0xFF5E..0xFF5E, 0x10781..0x10785, 0x10787..0x107B0,
            0x107B2..0x107BA, 0x1E030..0x1E06D, 0x1F16C..0x1F16C, 0x1F23B..0x1F23B, 0x1FBF0..0x1FBF9,
        )

        private fun nfkc(text: String): String {
            val out = StringBuilder(text.length)
            var from = 0
            var i = 0
            while (i < text.length) {
                val cp = text.codePointAt(i)
                val n = Character.charCount(cp)
                if (KEEP.any { cp in it }) {
                    out.append(Normalizer.normalize(text.substring(from, i), Normalizer.Form.NFKC)).appendCodePoint(cp)
                    from = i + n
                }
                i += n
            }
            return out.append(Normalizer.normalize(text.substring(from), Normalizer.Form.NFKC)).toString()
        }

        /** Metaspace, prepend "always", split: every "▁" starts a new word. */
        fun preTokenize(text: String): List<String> {
            if (text.isEmpty()) return emptyList()
            val s = SPACE + text.replace(' ', SPACE)
            val words = ArrayList<String>()
            var start = 0
            for (k in 1 until s.length) if (s[k] == SPACE) { words.add(s.substring(start, k)); start = k }
            words.add(s.substring(start))
            return words
        }

        /** Reads `model.vocab` (and `model.unk_id`) from a Hugging Face tokenizer.json of type Unigram. */
        fun load(file: File): UnigramTokenizer {
            val json = file.readText()
            val model = json.indexOf("\"model\"").takeIf { it >= 0 } ?: error("no model in ${file.name}")
            val unk = Regex("\"unk_id\"\\s*:\\s*(\\d+)").find(json, model)?.groupValues?.get(1)?.toInt() ?: 3
            val v = json.indexOf("\"vocab\"", model).takeIf { it >= 0 } ?: error("no vocab in ${file.name}")
            return UnigramTokenizer(VocabParser(json, json.indexOf('[', v)).parse(), unk)
        }
    }

    /** Minimal parser for `[["piece", score], ...]`. */
    private class VocabParser(private val s: String, private var i: Int) {
        fun parse(): List<Pair<String, Float>> {
            val out = ArrayList<Pair<String, Float>>(260_000)
            expect('[')
            while (true) {
                ws()
                if (s[i] == ']') { i++; break }
                expect('[')
                ws(); val piece = string(); ws(); expect(','); ws()
                val start = i
                while (s[i] != ']' && s[i] != ' ' && s[i] != '\n') i++
                val score = s.substring(start, i).toFloat()
                ws(); expect(']'); ws()
                out.add(piece to score)
                if (s[i] == ',') i++
            }
            return out
        }

        private fun ws() { while (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t') i++ }
        private fun expect(c: Char) { check(s[i] == c) { "expected '$c' at $i" }; i++ }

        private fun string(): String {
            expect('"')
            val sb = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> sb.append(e)
                    }
                    else -> sb.append(c)
                }
            }
        }
    }
}
