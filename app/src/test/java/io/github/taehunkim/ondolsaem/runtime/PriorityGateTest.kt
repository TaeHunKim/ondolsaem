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
}
