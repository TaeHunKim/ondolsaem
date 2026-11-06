package io.github.taehunkim.ondolsaem

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.taehunkim.ondolsaem.engine.NliDecider
import io.github.taehunkim.ondolsaem.sdk.Decisions
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The real decision model (download it in OndolSaem first) on obvious Korean and English cases. Logs every
 * probability and the time per call (tag OndolSaem.NliTest). Skipped without the model.
 */
@RunWith(AndroidJUnit4::class)
class NliDeciderTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val models = File(ctx.filesDir, "models")

    private val cases = listOf(
        Triple("오늘 점심값 12,000원 카드 영수증. 김밥천국 강남점.", "영수증", true),
        Triple("오늘 점심값 12,000원 카드 영수증. 김밥천국 강남점.", "여행", false),
        Triple("다음 주 제주도 3박 4일 일정: 첫날 성산일출봉, 둘째 날 한라산 등반.", "여행", true),
        Triple("다음 주 제주도 3박 4일 일정: 첫날 성산일출봉, 둘째 날 한라산 등반.", "요리", false),
        Triple("김치찌개 레시피: 돼지고기를 볶고 김치를 넣어 20분 끓인다.", "요리", true),
        Triple("분기 매출 보고서 초안을 금요일까지 팀장님께 제출해야 함.", "업무", true),
        Triple("분기 매출 보고서 초안을 금요일까지 팀장님께 제출해야 함.", "건강", false),
        Triple("혈압약 아침 저녁으로 복용, 다음 진료는 12월 3일.", "건강", true),
        Triple("Invoice #4411: total due $320, payment by Nov 30.", "a receipt or invoice", true),
        Triple("Invoice #4411: total due $320, payment by Nov 30.", "travel", false),
    )

    @Test fun int8ConvertedOnThePhone() = check("nli-mdeberta-v3-w8", "nli-w8/model.onnx")

    @Test fun q4() = check("nli-mdeberta-v3-q4", "onnx/model_q4.onnx")

    private fun check(id: String, path: String) {
        val dir = File(models, id)
        val model = File(dir, path)
        assumeTrue("no decision model at $model", model.exists())
        Log.i(TAG, "== $id")
        NliDecider(id, model, File(dir, "tokenizer.json")).use { d ->
            var right = 0
            for ((premise, topic, expected) in cases) {
                val t = System.currentTimeMillis()
                val p = d.entail(premise, listOf(Decisions.hypothesis(topic)))[0]
                Log.i(TAG, "%.3f in %d ms: %s / %s".format(p, System.currentTimeMillis() - t, topic, premise.take(30)))
                if ((p >= 0.5f) == expected) right++
            }
            // one premise against many topics, as a note is checked against all tags
            val t = System.currentTimeMillis()
            val many = d.entail(cases[2].first, listOf("여행", "요리", "업무", "건강", "공부", "돈", "아이디어", "할 일", "운동", "음악").map { Decisions.hypothesis(it) })
            Log.i(TAG, "10 topics in ${System.currentTimeMillis() - t} ms: ${many.joinToString { "%.2f".format(it) }}")
            assertTrue("$right of ${cases.size} right", right >= cases.size - 1)
            assertTrue(many[0] > 0.7f)
        }
    }

    private companion object { const val TAG = "OndolSaem.NliTest" }
}
