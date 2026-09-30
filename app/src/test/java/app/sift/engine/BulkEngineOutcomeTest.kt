package app.sift.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class BulkEngineOutcomeTest {
    private fun outcome(
        changed: Int = 0,
        locked: Int = 0,
        failed: Int = 0,
        error: String? = null,
    ) = BulkEngine.Outcome(changed, 0, locked, failed, error, batch = null)

    @Test fun noChangeSaysSo() {
        assertEquals("Nothing changed", outcome().describe("Blocked"))
        assertEquals("Nothing changed: no access", outcome(error = "no access").describe("Blocked"))
    }

    @Test fun singularAndPluralChannels() {
        assertEquals("Blocked 1 channel", outcome(changed = 1).describe("Blocked"))
        assertEquals("Silenced 3 channels", outcome(changed = 3).describe("Silenced"))
    }

    @Test fun skippedChannelsAreReported() {
        assertEquals("Blocked 2 channels · 3 couldn't be changed", outcome(changed = 2, locked = 2, failed = 1).describe("Blocked"))
    }
}
