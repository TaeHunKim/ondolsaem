package io.github.taehunkim.ondolsaem.runtime

import io.github.taehunkim.ondolsaem.sdk.OndolSaem

/**
 * Runs one request at a time per model. Waiting interactive requests (chat, search query) always go first, in
 * arrival order. Waiting background requests (indexing) take turns by app: each app has its own queue and the apps
 * are served round-robin, so one app indexing thousands of files cannot hold off another app's indexing.
 * Blocking: requests arrive on Binder threads.
 */
class PriorityGate {
    private class Waiter(val owner: String) { var granted = false }

    private val lock = Object()
    private var busy = false
    private val interactive = ArrayDeque<Waiter>()
    private val background = HashMap<String, ArrayDeque<Waiter>>()
    /** Apps with waiting background requests, next in turn first. */
    private val turns = ArrayDeque<String>()

    fun <T> run(priority: Int, owner: String = "", block: () -> T): T {
        acquire(priority, owner)
        try { return block() } finally { release() }
    }

    /** Blocks (interruptibly) until this request may run; pair with [release]. [owner]: the calling app. */
    fun acquire(priority: Int, owner: String = "") {
        synchronized(lock) {
            if (!busy && interactive.isEmpty() && turns.isEmpty()) { busy = true; return }
            val w = Waiter(owner)
            if (priority == OndolSaem.PRIORITY_INTERACTIVE) interactive.addLast(w)
            else {
                background.getOrPut(owner) { ArrayDeque() }.addLast(w)
                if (owner !in turns) turns.addLast(owner)
            }
            try {
                while (!w.granted) lock.wait()
            } catch (e: InterruptedException) {
                if (w.granted) { busy = false; grantNextLocked() } else remove(w)
                throw e
            }
        }
    }

    fun release() = synchronized(lock) {
        busy = false
        grantNextLocked()
    }

    private fun grantNextLocked() {
        val next = interactive.removeFirstOrNull() ?: nextBackground() ?: return
        next.granted = true
        busy = true
        lock.notifyAll()
    }

    private fun nextBackground(): Waiter? {
        val owner = turns.removeFirstOrNull() ?: return null
        val q = background.getValue(owner)
        val w = q.removeFirst()
        if (q.isEmpty()) background.remove(owner) else turns.addLast(owner)
        return w
    }

    private fun remove(w: Waiter) {
        if (interactive.remove(w)) return
        val q = background[w.owner] ?: return
        q.remove(w)
        if (q.isEmpty()) { background.remove(w.owner); turns.remove(w.owner) }
    }
}
