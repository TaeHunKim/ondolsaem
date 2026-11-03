package io.github.taehunkim.ondolsaem

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.taehunkim.ondolsaem.sdk.OndolSaem
import io.github.taehunkim.ondolsaem.sdk.OndolSaem.Categories
import io.github.taehunkim.ondolsaem.sdk.OndolSaemClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

/**
 * Measures the real embedding model shared by several clients: load time, backend (GPU/CPU), memory, latency of one
 * client, two clients at once, and an interactive client next to a background one. Prints `OndolSaem.Bench` log
 * lines. Skipped in the normal test run (needs the argument `bench=1`): run it with `scripts/bench-device.sh`, which keeps the downloaded models
 * (`connectedDebugAndroidTest` would uninstall the app). Skipped when the active embedding model is not downloaded.
 */
@RunWith(AndroidJUnit4::class)
class SharedModelBench {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val container get() = (ctx.applicationContext as OndolSaemApp).container
    private val runtime get() = container.runtime
    private val args = InstrumentationRegistry.getArguments()
    private val n = args.getString("benchN")?.toIntOrNull() ?: 30

    private fun out(line: String) = Log.i(TAG, line)

    private fun stats(name: String, ms: List<Long>) {
        val s = ms.sorted()
        fun pct(p: Double) = s[((s.size - 1) * p).toInt()]
        out("%-28s n=%d p50=%dms p95=%dms max=%dms".format(name, s.size, pct(0.5), pct(0.95), s.last()))
    }

    private suspend fun timed(block: suspend () -> Unit): Long {
        val t = SystemClock.elapsedRealtime(); block(); return SystemClock.elapsedRealtime() - t
    }

    private fun jpeg(seed: Int): ByteArray {
        val b = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawColor(Color.rgb(seed * 37 % 255, seed * 91 % 255, seed * 53 % 255))
        val p = Paint().apply { color = Color.WHITE; textSize = 64f }
        c.drawText("bench $seed", 40f, 260f, p)
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }

    @Test fun embeddingSharedByTwoClients() = runBlocking<Unit> {
        assumeTrue("run with scripts/bench-device.sh (argument bench=1)", args.getString("bench") == "1")
        val info = runtime.modelInfo(Categories.EMBEDDING)
        assumeTrue("embedding model ${info.id} is not downloaded", info.ready)
        out("device=${Build.MANUFACTURER} ${Build.MODEL} soc=${if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else "?"} sdk=${Build.VERSION.SDK_INT} model=${info.id} n=$n")

        runtime.unload(Categories.EMBEDDING)
        val pssBefore = Debug.getPss()
        val a = OndolSaemClient(ctx, idleUnbindMs = 60_000)
        val b = OndolSaemClient(ctx, idleUnbindMs = 60_000)
        try {
            val load = timed { a.embedQuery("warm up") }
            val st = runtime.embedding.state.first()
            out("load_ms=$load backend=${st?.backend ?: "?"} model_kb=${st?.loadedKb ?: "?"} pss_kb_before=$pssBefore pss_kb_after=${Debug.getPss()}")

            stats("one client, query", (0 until n).map { i -> timed { a.embedQuery("query number $i about photos") } })
            stats("one client, image", (0 until n / 3).map { i -> val j = jpeg(i); timed { a.embedImage(j) } })

            val both = SystemClock.elapsedRealtime()
            val lat = listOf(a, b).map { c ->
                async(kotlinx.coroutines.Dispatchers.IO) {
                    (0 until n).map { i -> timed { c.embedDocument("document $i", null, OndolSaem.PRIORITY_BACKGROUND) } }
                }
            }.awaitAll()
            val wall = SystemClock.elapsedRealtime() - both
            stats("two clients, client A", lat[0]); stats("two clients, client B", lat[1])
            out("two clients: ${2 * n} requests in ${wall}ms (%.1f/s)".format(2000.0 * n / wall))

            val bg = async(kotlinx.coroutines.Dispatchers.IO) { (0 until n / 2).forEach { i -> a.embedImage(jpeg(100 + i)) } }
            val fg = (0 until n / 3).map { i -> timed { b.embedQuery("interactive $i", OndolSaem.PRIORITY_INTERACTIVE) } }
            bg.await()
            stats("interactive next to images", fg)
            out("pss_kb_end=${Debug.getPss()}")
        } finally {
            a.disconnect(); b.disconnect()
        }
    }

    companion object { const val TAG = "OndolSaem.Bench" }
}
