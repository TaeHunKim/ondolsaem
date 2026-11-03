package io.github.taehunkim.ondolsaem.sdk

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import kotlin.concurrent.thread

/** Helpers to pass data to the host as file descriptors (Binder transactions are limited to ~1 MB). */
object Pfds {
    /** A pipe whose write end is filled with [bytes] on a background thread. */
    fun ofBytes(bytes: ByteArray): ParcelFileDescriptor {
        val (read, write) = ParcelFileDescriptor.createReliablePipe()
        thread(name = "ondolsaem-pipe", isDaemon = true) {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(bytes) }
            } catch (e: IOException) {
                // reader went away (call failed or was cancelled)
                runCatching { write.closeWithError(e.message) }
            }
        }
        return read
    }

    fun ofFile(file: File): ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    /** Reads a descriptor to the end and closes it (host side). */
    fun readAll(pfd: ParcelFileDescriptor): ByteArray =
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }

    /** Copies a descriptor into [target] and closes it (host side, for inputs that need random access). */
    fun copyTo(pfd: ParcelFileDescriptor, target: File) {
        ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input -> target.outputStream().use { input.copyTo(it, 1 shl 16) } }
    }

    /** Opens a plain stream on the descriptor (host side). */
    fun stream(pfd: ParcelFileDescriptor): FileInputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
}
