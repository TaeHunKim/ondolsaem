package io.github.taehunkim.ondolsaem

import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.taehunkim.ondolsaem.engine.SFaceEngine
import io.github.taehunkim.ondolsaem.engine.SherpaSpeech
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The real face engine (ML Kit + SFace) on photos of a few public figures (Nunsori's
 * `scripts/fetch-face-fixtures.sh`, pushed to the app's `files/faces` via `run-as`). Logs the cosine similarity
 * of every pair (tag OndolSaem.FaceTest) and checks that the same person scores above the threshold and different
 * people below it. Skipped without the model file or the photos.
 */
@RunWith(AndroidJUnit4::class)
class FaceEngineTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val model = File(ctx.filesDir, "models/face-sface/face_recognition_sface_2021dec.onnx")
    private val photos = File(ctx.filesDir, "faces")

    private fun cos(a: FloatArray, b: FloatArray) = a.indices.sumOf { (a[it] * b[it]).toDouble() }

    @Test fun samePersonScoresHigherThanDifferentPeople() = runBlocking<Unit> {
        assumeTrue("no SFace model at $model", model.exists())
        val files = photos.listFiles()?.filter { it.extension in setOf("jpg", "png") }?.sortedBy { it.name }.orEmpty()
        assumeTrue("no photos in $photos", files.isNotEmpty())
        SFaceEngine("face-sface", model).use { engine ->
            // Portraits (one person per file): name prefix before '_' is the person.
            val single = mutableListOf<Pair<String, FloatArray>>()
            for (f in files) {
                val bmp = BitmapFactory.decodeFile(f.path) ?: continue
                val t = System.currentTimeMillis()
                val faces = engine.detect(bmp)
                Log.i(TAG, "${f.name}: ${faces.size} face(s) in ${System.currentTimeMillis() - t} ms, quality ${faces.map { "%.2f".format(it.score) }}")
                if (f.name.substringBefore('_').let { p -> files.count { it.name.startsWith("${p}_") } } > 1 &&
                    !f.name.contains("family") && !f.name.contains("merkel_macron")) {
                    faces.maxByOrNull { (it.right - it.left) * (it.bottom - it.top) }?.let { single += f.name.substringBefore('_') to it.vector }
                }
            }
            val same = mutableListOf<Double>(); val diff = mutableListOf<Double>()
            for (i in single.indices) for (j in i + 1 until single.size) {
                val c = cos(single[i].second, single[j].second)
                (if (single[i].first == single[j].first) same else diff) += c
            }
            Log.i(TAG, "same person: n=${same.size} min=%.3f mean=%.3f".format(same.min(), same.average()))
            Log.i(TAG, "different:   n=${diff.size} max=%.3f mean=%.3f".format(diff.max(), diff.average()))
            assertTrue("same-person pairs ${same.min()} should beat different ${diff.max()}", same.min() > diff.max())
        }
    }

    /** The face model runs on sherpa-onnx's ONNX Runtime (JNI bridge): speech must keep working in the same process. */
    @Test fun speechStillWorksNextToTheFaceRuntime() = runBlocking<Unit> {
        assumeTrue("no SFace model at $model", model.exists())
        SFaceEngine("face-sface", model).use { engine ->
            assertEquals(0, engine.detect(android.graphics.Bitmap.createBitmap(200, 200, android.graphics.Bitmap.Config.ARGB_8888)).size)
            val pcm = File(ctx.cacheDir, "silence.pcm").apply { writeBytes(ByteArray(16_000 * 2)) }
            val spans = SherpaSpeech(ctx, null, 1).speechSpans(pcm)
            assertTrue("silence has no speech: $spans", spans.isEmpty())
        }
    }

    companion object { const val TAG = "OndolSaem.FaceTest" }
}
