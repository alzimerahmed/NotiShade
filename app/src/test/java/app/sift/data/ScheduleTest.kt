package app.sift.data

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val newYork = ZoneId.of("America/New_York")

    /** Epoch millis for a wall-clock time in [zone]. */
    private fun at(zone: ZoneId, year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun active(s: Schedule, zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        s.activeAt(at(zone, y, mo, d, h, mi), zone)

    @Test fun activeInsideSimpleWindow() {
        val s = Schedule(10 * 60, 12 * 60)
        assertTrue(active(s, paris, 2026, 3, 10, 11, 0))
        assertTrue(active(s, paris, 2026, 3, 10, 10, 0)) // start inclusive
        assertFalse(active(s, paris, 2026, 3, 10, 12, 0)) // end exclusive
        assertFalse(active(s, paris, 2026, 3, 10, 9, 59))
        assertFalse(active(s, paris, 2026, 3, 10, 13, 0))
    }

    @Test fun windowWrappingMidnight() {
        val s = Schedule(22 * 60, 7 * 60)
        assertTrue(active(s, paris, 2026, 3, 10, 22, 0))
        assertTrue(active(s, paris, 2026, 3, 10, 23, 30))
        assertTrue(active(s, paris, 2026, 3, 11, 6, 59))
        assertFalse(active(s, paris, 2026, 3, 11, 7, 0))
        assertFalse(active(s, paris, 2026, 3, 10, 21, 59))
        assertFalse(active(s, paris, 2026, 3, 10, 12, 0))
    }

    @Test fun startEqualsEndIsNeverActive() {
        val s = Schedule(8 * 60, 8 * 60)
        assertFalse(active(s, paris, 2026, 3, 10, 8, 0))
        assertFalse(active(s, paris, 2026, 3, 10, 8, 1))
    }

    @Test fun disabledIsNeverActive() {
        val s = Schedule(0, 24 * 60 - 1, enabled = false)
        assertFalse(active(s, paris, 2026, 3, 10, 12, 0))
    }

    @Test fun daysOfWeekFilter() {
        // 2026-03-09 is a Monday, 2026-03-10 a Tuesday.
        val s = Schedule(10 * 60, 12 * 60, days = setOf(1))
        assertTrue(active(s, paris, 2026, 3, 9, 11, 0))
        assertFalse(active(s, paris, 2026, 3, 10, 11, 0))
    }

    @Test fun emptyDaysMeansEveryDay() {
        val s = Schedule(10 * 60, 12 * 60)
        assertTrue(active(s, paris, 2026, 3, 9, 11, 0))
        assertTrue(active(s, paris, 2026, 3, 15, 11, 0)) // Sunday
    }

    @Test fun wrappedWindowMorningBelongsToPreviousDay() {
        // 22:00–07:00 on Fridays only: Friday night runs into Saturday morning.
        val s = Schedule(22 * 60, 7 * 60, days = setOf(5))
        assertTrue(active(s, paris, 2026, 3, 13, 23, 0)) // Friday evening
        assertTrue(active(s, paris, 2026, 3, 14, 2, 0)) // Saturday small hours = Friday's window
        assertFalse(active(s, paris, 2026, 3, 14, 23, 0)) // Saturday evening
        assertFalse(active(s, paris, 2026, 3, 14, 12, 0))
    }

    @Test fun sameInstantDiffersAcrossTimezones() {
        val s = Schedule(22 * 60, 7 * 60)
        // 2026-03-10 23:00 in Paris is 18:00 in New York (both on standard-ish offsets in March 2026:
        // Paris CET = UTC+1, New York EDT = UTC-4 after the 8th).
        val instant = at(paris, 2026, 3, 10, 23, 0)
        assertTrue(s.activeAt(instant, paris))
        assertFalse(s.activeAt(instant, newYork))
    }

    @Test fun keys() {
        assertEquals("cat:PROMO", Schedule.keyFor(Category.PROMO))
        assertEquals("rule:42", Schedule.keyFor(42L))
    }
}
