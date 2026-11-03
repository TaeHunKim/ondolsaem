package io.github.taehunkim.ondolsaem.service

import io.github.taehunkim.ondolsaem.service.CallerCheck.Decision
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallerCheckTest {
    @Test fun parsesHexDigestWithOrWithoutColons() {
        val plain = "de738c433f930769727fe392e0d35a67dcf9a6365f063d22d35371663b9e4cd4"
        val colons = plain.chunked(2).joinToString(":").uppercase()
        assertArrayEquals(CallerCheck.hex(plain), CallerCheck.hex(colons))
        assertArrayEquals(byteArrayOf(0xde.toByte(), 0x73), CallerCheck.hex(plain)!!.copyOf(2))
    }

    @Test fun rejectsWrongLength() { assertNull(CallerCheck.hex("abcd")) }

    private fun stored(state: Approval.State, cert: String = "k1") = Approval("com.example", cert, state, 0)

    @Test fun anUnknownAppIsAsked() { assertEquals(Decision.ASK, CallerCheck.decide(null, "k1")) }

    @Test fun storedDecisionsHoldForTheSameKey() {
        assertEquals(Decision.ALLOW, CallerCheck.decide(stored(Approval.State.ALLOWED), "k1"))
        assertEquals(Decision.DENY, CallerCheck.decide(stored(Approval.State.DENIED), "k1"))
        assertEquals(Decision.WAIT, CallerCheck.decide(stored(Approval.State.PENDING), "k1"))
    }

    @Test fun aReSignedAppIsAskedAgain() {
        Approval.State.entries.forEach { assertEquals(Decision.ASK, CallerCheck.decide(stored(it), "k2")) }
    }

    @Test fun toHexIsLowerCaseAndPadded() { assertEquals("00ff0a", CallerCheck.toHex(byteArrayOf(0, -1, 10))) }
}
