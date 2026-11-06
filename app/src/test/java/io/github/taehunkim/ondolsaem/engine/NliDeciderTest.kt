package io.github.taehunkim.ondolsaem.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NliDeciderTest {
    @Test fun yesIsSoftmaxOverEntailmentAndContradiction() {
        assertEquals(0.5f, Nli.yes(1f, 1f), 1e-6f)
        assertEquals(0.8808f, Nli.yes(2f, 0f), 1e-3f)
        assertEquals(0f, Nli.yes(-100f, 100f), 1e-6f)
    }
}
