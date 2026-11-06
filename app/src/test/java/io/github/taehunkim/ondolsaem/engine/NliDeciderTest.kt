package io.github.taehunkim.ondolsaem.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NliDeciderTest {
    @Test fun yesIsTheEntailmentShareOfAllThreeClasses() {
        assertEquals(1f / 3f, Nli.yes(1f, 1f, 1f), 1e-6f)
        assertEquals(0.7870f, Nli.yes(2f, 0f, 0f), 1e-3f)
        assertEquals("a neutral answer is not a yes", 0f, Nli.yes(0f, 100f, -100f), 1e-6f)
    }
}
