package io.github.taehunkim.ondolsaem.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleRuleTest {
    @Test fun tiersByBatteryLevel() {
        val p = IdlePreset.NORMAL
        assertEquals(10, IdleRule.requiredOffMinutes(p, 100))
        assertEquals(10, IdleRule.requiredOffMinutes(p, 90))
        assertEquals(30, IdleRule.requiredOffMinutes(p, 89))
        assertEquals(30, IdleRule.requiredOffMinutes(p, 70))
        assertEquals(60, IdleRule.requiredOffMinutes(p, 69))
        assertEquals(60, IdleRule.requiredOffMinutes(p, 0))
    }

    @Test fun presets() {
        assertEquals(listOf(5, 15, 30), listOf(95, 80, 50).map { IdleRule.requiredOffMinutes(IdlePreset.FAST, it) })
        assertEquals(listOf(30, 60, 120), listOf(95, 80, 50).map { IdleRule.requiredOffMinutes(IdlePreset.RELAXED, it) })
        assertEquals(IdlePreset.NORMAL, IdlePreset.DEFAULT)
        assertEquals(IdlePreset.NORMAL, IdlePreset.of("nonsense"))
        assertEquals(IdlePreset.FAST, IdlePreset.of("FAST"))
    }

    @Test fun idleNeedsChargingAndEnoughTimeOff() {
        val now = 10_000_000L
        val min = 60_000L
        assertTrue(IdleRule.isIdle(IdlePreset.NORMAL, true, 95, now - 10 * min, now))
        assertFalse(IdleRule.isIdle(IdlePreset.NORMAL, true, 95, now - 9 * min, now))
        assertFalse(IdleRule.isIdle(IdlePreset.NORMAL, true, 60, now - 59 * min, now))
        assertTrue(IdleRule.isIdle(IdlePreset.NORMAL, true, 60, now - 60 * min, now))
        assertFalse("not charging", IdleRule.isIdle(IdlePreset.NORMAL, false, 100, now - 600 * min, now))
        assertFalse("screen on", IdleRule.isIdle(IdlePreset.NORMAL, true, 100, null, now))
    }
}
