package io.github.taehunkim.ondolsaem.download

import java.io.File
import java.security.MessageDigest

object Checksums {
    /** Lower-case hex SHA-256 of [file], read in 1 MB blocks. */
    fun sha256(file: File, onProgress: (Long) -> Unit = {}): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(1 shl 20)
        var done = 0L
        file.inputStream().use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                done += n
                onProgress(done)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Throws [DownloadException] (CORRUPT) and deletes [file] when its hash is not [expected]. */
    fun verify(file: File, expected: String?) {
        if (expected.isNullOrBlank()) return
        val start = System.nanoTime()
        val actual = sha256(file)
        val ms = (System.nanoTime() - start) / 1_000_000
        if (!actual.equals(expected, ignoreCase = true)) {
            runCatching { android.util.Log.w(TAG, "${file.name}: sha256 mismatch ($actual), deleted") }
            file.delete()
            throw DownloadException(DownloadException.Kind.CORRUPT, "${file.name}: sha256 $actual, expected $expected")
        }
        runCatching { android.util.Log.i(TAG, "${file.name}: sha256 ok (${ms} ms)") }
    }

    private const val TAG = "OndolSaem.Download"
}
