package io.github.taehunkim.ondolsaem.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/** One face: box in 0..1 image coordinates, a quality score (0..1, frontal and sharp-enough faces score higher) and its embedding. */
data class DetectedFace(
    val left: Float, val top: Float, val right: Float, val bottom: Float,
    val score: Float,
    /** L2-normalized identity vector; faces of the same person have a high cosine similarity. */
    val vector: FloatArray,
)

interface FaceEngine : AutoCloseable {
    val modelId: String
    /** Faces large enough to recognize, with their embeddings. */
    suspend fun detect(bitmap: Bitmap): List<DetectedFace>
    override fun close() {}
}

/**
 * ML Kit face detection (bundled, five landmarks) + SFace (OpenCV Zoo, MobileFaceNet, 128-d) on sherpa-onnx's ONNX
 * Runtime ([OrtModel]).
 * Each face is warped to SFace's 112x112 template with a similarity transform fitted to the eyes, nose and mouth
 * corners, like OpenCV's `FaceRecognizerSF.alignCrop`.
 */
class SFaceEngine(override val modelId: String, model: File) : FaceEngine {
    private val sface = OrtModel(model, threads = 2)
    private val detector: FaceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setMinFaceSize(MIN_FACE_FRACTION)
            .build(),
    )

    override suspend fun detect(bitmap: Bitmap): List<DetectedFace> {
        val faces = detector.process(InputImage.fromBitmap(bitmap, 0)).await()
        val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
        return faces.mapNotNull { f ->
            val b = f.boundingBox
            if (minOf(b.width(), b.height()) < MIN_FACE_PX) return@mapNotNull null
            val pts = landmarks(f) ?: return@mapNotNull null
            val aligned = align(bitmap, pts)
            val v = embed(aligned)
            aligned.recycle()
            DetectedFace(
                (b.left / w).coerceIn(0f, 1f), (b.top / h).coerceIn(0f, 1f),
                (b.right / w).coerceIn(0f, 1f), (b.bottom / h).coerceIn(0f, 1f),
                quality(f), v,
            )
        }
    }

    /** Eyes, nose, mouth corners ordered as in the template (image-left first); null without all five. */
    private fun landmarks(f: Face): List<PointF>? {
        fun p(t: Int) = f.getLandmark(t)?.position
        val eyes = listOf(p(FaceLandmark.LEFT_EYE) ?: return null, p(FaceLandmark.RIGHT_EYE) ?: return null).sortedBy { it.x }
        val mouth = listOf(p(FaceLandmark.MOUTH_LEFT) ?: return null, p(FaceLandmark.MOUTH_RIGHT) ?: return null).sortedBy { it.x }
        val nose = p(FaceLandmark.NOSE_BASE) ?: return null
        return listOf(eyes[0], eyes[1], nose, mouth[0], mouth[1])
    }

    private fun quality(f: Face): Float {
        val turn = (abs(f.headEulerAngleY) + abs(f.headEulerAngleX)) / 90f
        return (1f - turn).coerceIn(0f, 1f)
    }

    private fun align(src: Bitmap, pts: List<PointF>): Bitmap {
        val out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, similarity(pts, TEMPLATE), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun embed(face: Bitmap): FloatArray {
        val px = IntArray(SIZE * SIZE).also { face.getPixels(it, 0, SIZE, 0, 0, SIZE, SIZE) }
        // NCHW, RGB, 0..255 (OpenCV blobFromImage with swapRB, no scaling or mean)
        val area = SIZE * SIZE
        val input = FloatArray(3 * area)
        for (i in px.indices) {
            input[i] = ((px[i] shr 16) and 0xFF).toFloat()
            input[area + i] = ((px[i] shr 8) and 0xFF).toFloat()
            input[2 * area + i] = (px[i] and 0xFF).toFloat()
        }
        return normalize(sface.run(input, longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())))
    }

    override fun close() { detector.close(); sface.close() }

    companion object {
        const val SIZE = 112
        const val MIN_FACE_PX = 40
        const val MIN_FACE_FRACTION = 0.05f

        /** SFace / ArcFace 112x112 reference points: eyes, nose tip, mouth corners. */
        val TEMPLATE = listOf(
            PointF(38.2946f, 51.6963f), PointF(73.5318f, 51.5014f), PointF(56.0252f, 71.7366f),
            PointF(41.5493f, 92.3655f), PointF(70.7299f, 92.2041f),
        )

        fun normalize(v: FloatArray): FloatArray {
            val n = sqrt(v.fold(0.0) { a, x -> a + x * x }).toFloat().takeIf { it > 0f } ?: return v
            return FloatArray(v.size) { v[it] / n }
        }

        /** Least-squares similarity transform (scale, rotation, translation) mapping [src] onto [dst] (Umeyama). */
        fun similarity(src: List<PointF>, dst: List<PointF>): Matrix {
            val n = src.size
            val sx = src.sumOf { it.x.toDouble() } / n; val sy = src.sumOf { it.y.toDouble() } / n
            val dx = dst.sumOf { it.x.toDouble() } / n; val dy = dst.sumOf { it.y.toDouble() } / n
            var a = 0.0; var b = 0.0; var norm = 0.0
            for (i in 0 until n) {
                val px = src[i].x - sx; val py = src[i].y - sy
                val qx = dst[i].x - dx; val qy = dst[i].y - dy
                a += px * qx + py * qy
                b += px * qy - py * qx
                norm += px * px + py * py
            }
            val c = a / norm; val s = b / norm // scale*cos, scale*sin
            val tx = dx - (c * sx - s * sy); val ty = dy - (s * sx + c * sy)
            return Matrix().apply {
                setValues(floatArrayOf(c.toFloat(), (-s).toFloat(), tx.toFloat(), s.toFloat(), c.toFloat(), ty.toFloat(), 0f, 0f, 1f))
            }
        }
    }
}
