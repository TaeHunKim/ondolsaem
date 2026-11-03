package io.github.taehunkim.ondolsaem.runtime

import io.github.taehunkim.ondolsaem.sdk.OndolSaem

/**
 * Runs one request at a time per model. A waiting interactive request (chat, search query) always goes before
 * waiting background requests (indexing). Blocking: requests arrive on Binder threads.
 */
class PriorityGate {
    private val lock = Object()
    private var busy = false
    private var waitingInteractive = 0

    fun <T> run(priority: Int, block: () -> T): T {
        acquire(priority)
        try { return block() } finally { release() }
    }

    /** Blocks (interruptibly) until this request may run; pair with [release]. */
    fun acquire(priority: Int) {
        val interactive = priority == OndolSaem.PRIORITY_INTERACTIVE
        synchronized(lock) {
            if (interactive) waitingInteractive++
            try {
                while (busy || (!interactive && waitingInteractive > 0)) lock.wait()
            } finally {
                if (interactive) waitingInteractive--
            }
            busy = true
        }
    }

    fun release() = synchronized(lock) { busy = false; lock.notifyAll() }
}
