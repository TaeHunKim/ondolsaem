package io.github.taehunkim.ondolsaem.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostErrorsTest {
    @Test fun roundTrip() {
        val e = HostErrors.decode(HostErrors.encode(OndolSaem.Errors.MODEL_NOT_READY, "no model: x"))!!
        assertEquals(OndolSaem.Errors.MODEL_NOT_READY, e.code)
        assertEquals("no model: x", e.message)
    }

    @Test fun emptyMessageBecomesNull() {
        assertNull(HostErrors.decode(HostErrors.encode(OndolSaem.Errors.CANCELLED, null))!!.message)
    }

    @Test fun otherExceptionsAreNotHostErrors() {
        assertNull(HostErrors.decode(IllegalStateException("something else")))
    }
}
