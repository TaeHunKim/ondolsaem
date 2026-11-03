package io.github.taehunkim.ondolsaem.runtime

import io.github.taehunkim.ondolsaem.sdk.OndolSaem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class PriorityGateTest {
    @Test fun interactiveRequestsOvertakeWaitingBackgroundOnes() {
        val gate = PriorityGate()
        val order = Collections.synchronizedList(ArrayList<String>())
        val running = CountDownLatch(1)
        val finish = CountDownLatch(1)
        val first = thread { gate.run(OndolSaem.PRIORITY_BACKGROUND) { running.countDown(); finish.await(); order += "bg0" } }
        running.await()
        val bg = thread { gate.run(OndolSaem.PRIORITY_BACKGROUND) { order += "bg1" } }
        Thread.sleep(100) // bg1 is waiting
        val fg = thread { gate.run(OndolSaem.PRIORITY_INTERACTIVE) { order += "fg" } }
        Thread.sleep(100) // fg is waiting too
        finish.countDown()
        listOf(first, bg, fg).forEach { it.join(5_000) }
        assertEquals(listOf("bg0", "fg", "bg1"), order)
    }

    @Test fun oneRequestAtATime() {
        val gate = PriorityGate()
        var inside = 0
        var maxInside = 0
        val done = CountDownLatch(8)
        repeat(8) { i ->
            thread {
                gate.run(i % 2) {
                    synchronized(this) { inside++; maxInside = maxOf(maxInside, inside) }
                    Thread.sleep(10)
                    synchronized(this) { inside-- }
                }
                done.countDown()
            }
        }
        done.await(5, TimeUnit.SECONDS)
        assertEquals(1, maxInside)
    }

    @Test fun releasedAfterFailure() {
        val gate = PriorityGate()
        runCatching { gate.run(OndolSaem.PRIORITY_INTERACTIVE) { error("boom") } }
        assertEquals("ok", gate.run(OndolSaem.PRIORITY_BACKGROUND) { "ok" })
    }

    /** Starts [n] waiting background requests from [owner] while the gate is held; each appends its label. */
    private fun queue(gate: PriorityGate, owner: String, n: Int, order: MutableList<String>, priority: Int = OndolSaem.PRIORITY_BACKGROUND) =
        (0 until n).map { i ->
            thread { gate.run(priority, owner) { order += "$owner$i" } }.also { Thread.sleep(30) } // keeps arrival order
        }

    @Test fun backgroundRequestsTakeTurnsByApp() {
        val gate = PriorityGate()
        val order = Collections.synchronizedList(ArrayList<String>())
        gate.acquire(OndolSaem.PRIORITY_BACKGROUND, "hold")
        val threads = queue(gate, "a", 4, order) + queue(gate, "b", 2, order) + queue(gate, "c", 1, order)
        gate.release()
        threads.forEach { it.join(5_000) }
        assertEquals(listOf("a0", "b0", "c0", "a1", "b1", "a2", "a3"), order)
    }

    @Test fun interactiveStillGoesBeforeEveryBackgroundQueue() {
        val gate = PriorityGate()
        val order = Collections.synchronizedList(ArrayList<String>())
        gate.acquire(OndolSaem.PRIORITY_BACKGROUND, "hold")
        val threads = queue(gate, "a", 2, order) + queue(gate, "b", 1, order) +
            queue(gate, "chat", 2, order, OndolSaem.PRIORITY_INTERACTIVE)
        gate.release()
        threads.forEach { it.join(5_000) }
        assertEquals(listOf("chat0", "chat1", "a0", "b0", "a1"), order)
    }

    @Test fun anInterruptedWaiterLeavesTheQueue() {
        val gate = PriorityGate()
        val order = Collections.synchronizedList(ArrayList<String>())
        gate.acquire(OndolSaem.PRIORITY_BACKGROUND, "hold")
        val gone = thread { runCatching { gate.run(OndolSaem.PRIORITY_BACKGROUND, "a") { order += "a0" } } }
        Thread.sleep(50)
        val rest = queue(gate, "b", 1, order)
        gone.interrupt(); gone.join(5_000)
        gate.release()
        rest.forEach { it.join(5_000) }
        assertEquals(listOf("b0"), order)
        assertEquals("free", gate.run(OndolSaem.PRIORITY_BACKGROUND, "a") { "free" })
    }
}
