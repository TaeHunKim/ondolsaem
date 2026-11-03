package io.github.taehunkim.ondolsaem.service

import org.junit.Assert.assertArrayEquals
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
}
