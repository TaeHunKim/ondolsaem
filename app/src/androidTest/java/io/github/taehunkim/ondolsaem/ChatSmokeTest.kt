package io.github.taehunkim.ondolsaem

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.taehunkim.ondolsaem.catalog.ModelCatalog
import io.github.taehunkim.ondolsaem.engine.ChatEvent
import io.github.taehunkim.ondolsaem.engine.ChatTurn
import io.github.taehunkim.ondolsaem.engine.LiteRtChatModel
import io.github.taehunkim.ondolsaem.engine.TurnRole
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** The real chat model (Gemma 4 E2B, downloaded in the app) loads on this LiteRT-LM version and answers. */
@RunWith(AndroidJUnit4::class)
class ChatSmokeTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun gemma4e2bAnswers() = runBlocking {
        val manager = (ctx.applicationContext as OndolSaemApp).container.modelManager
        val spec = ModelCatalog.byId("chat-gemma4-e2b")!!
        assumeTrue("chat-gemma4-e2b not downloaded", manager.isReady(spec))
        val t0 = System.currentTimeMillis()
        LiteRtChatModel.load(manager.file(spec)!!, File(ctx.cacheDir, "litertlm"), spec.vision, 2048).use { m ->
            val t1 = System.currentTimeMillis()
            val text = StringBuilder(); var n = 0
            m.reply("smoke", "Answer briefly.", emptyList(), ChatTurn(TurnRole.USER, "대한민국의 수도는 어디인가요? 한 단어로 답하세요."), emptyList())
                .takeWhile { n < 20 }
                .collect { if (it is ChatEvent.Token) { n++; text.append(it.text) } }
            Log.i("OndolSaem.ChatSmoke", "load ${t1 - t0} ms on ${m.backend}, answer in ${System.currentTimeMillis() - t1} ms: $text")
            assertTrue(text.toString(), text.contains("서울") || text.contains("Seoul"))
        }
    }
}
