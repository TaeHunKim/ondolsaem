package io.github.taehunkim.ondolsaem.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ChecksumsTest {
    private fun temp(bytes: ByteArray) = File(Files.createTempDirectory("sum").toFile(), "f").apply { writeBytes(bytes) }

    @Test fun knownDigest() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Checksums.sha256(temp("abc".toByteArray())))
    }

    @Test fun largerThanOneBlock() {
        val f = temp(ByteArray(3 * (1 shl 20) + 17) { (it % 251).toByte() })
        val expected = java.security.MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
        assertEquals(expected, Checksums.sha256(f))
    }

    @Test fun matchingFileIsKept() {
        val f = temp("abc".toByteArray())
        Checksums.verify(f, "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD")
        assertTrue(f.exists())
    }

    @Test fun mismatchDeletesTheFile() {
        val f = temp("abd".toByteArray())
        try {
            Checksums.verify(f, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"); fail()
        } catch (e: DownloadException) {
            assertEquals(DownloadException.Kind.CORRUPT, e.kind)
        }
        assertFalse(f.exists())
    }

    @Test fun noExpectedHashSkipsTheCheck() {
        val f = temp("x".toByteArray())
        Checksums.verify(f, null)
        assertTrue(f.exists())
    }
}
