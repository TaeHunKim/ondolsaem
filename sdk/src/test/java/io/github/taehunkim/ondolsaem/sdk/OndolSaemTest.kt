package io.github.taehunkim.ondolsaem.sdk

import org.junit.Assert.assertTrue
import org.junit.Test

class OndolSaemTest {
    @Test fun bindActionIsNamespacedByHostPackage() {
        assertTrue(OndolSaem.ACTION_BIND.startsWith(OndolSaem.PACKAGE + "."))
    }
}
