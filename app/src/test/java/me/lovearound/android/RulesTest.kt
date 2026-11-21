package me.lovearound.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RulesTest {
    private fun at(utc: String) = Instant.parse("2026-11-21T${utc}:00Z")

    @Test fun quietHoursOverMidnight() {
        val q = QuietHours("23:00", "08:00", "UTC")
        assertTrue(q.contains(at("23:00")))
        assertTrue(q.contains(at("03:30")))
        assertFalse(q.contains(at("08:00")))
        assertFalse(q.contains(at("12:00")))
    }

    @Test fun quietHoursInTheDayAndInTheirZone() {
        val q = QuietHours("13:00", "14:00", "Europe/Lisbon")
        assertTrue(q.contains(at("13:30")))
        assertFalse(q.contains(at("14:00")))
        // Moscow is UTC+3: 13:30 there is 10:30 UTC.
        assertTrue(QuietHours("13:00", "14:00", "Europe/Moscow").contains(at("10:30")))
    }

    @Test fun quietHoursThatAreNone() {
        assertFalse(QuietHours("08:00", "08:00", "UTC").contains(at("08:00")))
        assertFalse(QuietHours("25:00", "08:00", "UTC").contains(at("03:00")))
        assertNull(QuietHours.minutes("7"))
        assertEquals(7 * 60 + 5, QuietHours.minutes("07:05"))
    }

    @Test fun inviteCodesFromLinksAndMessages() {
        assertEquals("K7MXR-2QHP9", AppModel.inviteId("https://lovearound.me/i/K7MXR-2QHP9"))
        assertEquals("K7MXR-2QHP9", AppModel.inviteId("Мой Love ID: https://lovearound.me/i/K7MXR-2QHP9."))
        assertEquals("k7mxr2qhp9", AppModel.inviteId("lovearound://add/k7mxr2qhp9"))
        assertEquals("K7MXR-2QHP9", AppModel.inviteId("https://lovearound.me/es/i/K7MXR-2QHP9?x=1"))
        // 0, 1, I, L, O, U are never in a code: a typo.
        assertNull(AppModel.inviteId("https://lovearound.me/i/K7MXR-2QHP0"))
        assertNull(AppModel.inviteId("K7MXR-2QHP"))
        assertNull(AppModel.inviteId(""))
    }

    @Test fun paletteWrapsAround() {
        assertEquals(12, Palette.all.size)
        assertEquals(0, Palette.index(null))
        assertEquals(1, Palette.index(13))
        assertEquals(11, Palette.index(-1))
    }

    @Test fun theWaveRisesAndFalls() {
        repeat(20) {
            val wave = Emoji.wave()
            assertTrue(wave.startsWith("✨") && wave.endsWith("✨"))
            val inner = wave.removePrefix("✨").removeSuffix("✨")
            val hearts = Emoji.pink.filter { inner.contains(it) }
            assertEquals(3, hearts.size)
            assertTrue(inner.startsWith(hearts.first { inner.startsWith(it) }))
            val three = Emoji.three()
            assertTrue(Emoji.pink.any { three.startsWith(it) && three.endsWith(it) })
        }
    }
}
