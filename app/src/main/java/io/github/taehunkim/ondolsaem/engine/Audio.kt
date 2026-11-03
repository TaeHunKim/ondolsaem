package io.github.taehunkim.ondolsaem.engine

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Raw PCM: 16 kHz, mono, 16-bit little endian (the format of IAsrService inputs). */
object Pcm {
    const val RATE = 16_000

    fun msToByte(ms: Long): Long = (ms * RATE / 1000) * 2

    fun toFloats(pcm: ByteArray): FloatArray {
        val sb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        return FloatArray(sb.remaining()) { sb.get(it) / 32768f }
    }
}

/** One stretch of time inside an audio input. */
data class Span(val startMs: Long, val endMs: Long)

/** One recognised utterance. */
data class Cue(val startMs: Long, val endMs: Long, val text: String)

object VectorMath {
    fun normalize(a: FloatArray): FloatArray {
        var s = 0f
        for (x in a) s += x * x
        val n = sqrt(s)
        if (n == 0f) return a
        return FloatArray(a.size) { a[it] / n }
    }
}
