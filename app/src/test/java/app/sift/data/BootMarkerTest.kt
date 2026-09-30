package app.sift.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The decision logic only; the file I/O in [BootMarker] is thin and framework-bound. */
class BootMarkerTest {
    @Test fun runsWhenNeverRun() {
        assertTrue(BootMarker.shouldRun(null, boot = 5, now = 0))
    }

    @Test fun runsWhenBootChanged() {
        val last = BootMarker.State(boot = 4, at = 1_000)
        assertTrue(BootMarker.shouldRun(last, boot = 5, now = last.at + 1_000))
    }

    @Test fun skipsRoutineRebindOnSameBoot() {
        val last = BootMarker.State(boot = 5, at = 1_000)
        assertFalse(BootMarker.shouldRun(last, boot = 5, now = last.at + 60_000))
    }
}
