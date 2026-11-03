package io.github.taehunkim.ondolsaem

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.taehunkim.ondolsaem.service.Approval
import io.github.taehunkim.ondolsaem.service.Approvals
import io.github.taehunkim.ondolsaem.settings.HostSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The approval store: requests, decisions, re-signed apps and persistence (the stored JSON). */
@RunWith(AndroidJUnit4::class)
class ApprovalsTest {
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val settings = HostSettings(ctx)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var saved: String

    @Before fun setUp() = runBlocking { saved = settings.approvalsJson.first(); settings.setApprovalsJson("") }
    @After fun tearDown() = runBlocking { settings.setApprovalsJson(saved); scope.cancel() }

    @Test fun requestsDecisionsAndANewKey() {
        val a = Approvals(settings, scope)
        var changes = 0
        a.onChange = { changes++ }
        assertTrue("first request is new", a.request("com.example", "k1", now = 5))
        assertFalse("asking again is not", a.request("com.example", "k1", now = 6))
        assertEquals(Approval.State.PENDING, a.get("com.example")!!.state)
        a.decide("com.example", allow = true)
        assertEquals(Approval.State.ALLOWED, a.get("com.example")!!.state)
        assertTrue("re-signed app asks again", a.request("com.example", "k2", now = 7))
        assertEquals(Approval(("com.example"), "k2", Approval.State.PENDING, 7), a.get("com.example"))
        a.forget("com.example")
        assertNull(a.get("com.example"))
        assertEquals(4, changes)
    }

    @Test fun decisionsSurviveARestart() = runBlocking {
        val a = Approvals(settings, scope)
        a.request("com.example", "k1", now = 1)
        a.decide("com.example", allow = false)
        withTimeout(5_000) { while (settings.approvalsJson.first().isEmpty() || !settings.approvalsJson.first().contains("DENIED")) delay(20) }
        val again = Approvals(settings, scope)
        assertEquals(Approval("com.example", "k1", Approval.State.DENIED, 1), again.get("com.example"))
    }

    @Test fun brokenJsonIsIgnored() {
        assertTrue(Approvals.decode("{not json").isEmpty())
        assertTrue(Approvals.decode("""{"a":{"cert":"k","state":"MAYBE","at":1}}""").isEmpty())
    }
}
