package app.notishade.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class BulkEngineOutcomeTest {
    private fun outcome(
        changed: Int = 0,
        locked: Int = 0,
        failed: Int = 0,
        error: String? = null,
    ) = BulkEngine.Outcome(changed, 0, locked, failed, error, batch = null)

    // English words, as the resources resolve them; the pure formatting core is under test.
    private val channels = { n: Int -> if (n == 1) "channel" else "channels" }
    private val skippedFmt = " · %d couldn't be changed"

    private fun describe(o: BulkEngine.Outcome, verb: String) = BulkEngine.Outcome.describe(
        o.changed, o.locked + o.failed, o.error, verb, "Nothing changed", channels, skippedFmt,
    )

    @Test fun noChangeSaysSo() {
        assertEquals("Nothing changed", describe(outcome(), "Blocked"))
        assertEquals("Nothing changed: no access", describe(outcome(error = "no access"), "Blocked"))
    }

    @Test fun singularAndPluralChannels() {
        assertEquals("Blocked 1 channel", describe(outcome(changed = 1), "Blocked"))
        assertEquals("Silenced 3 channels", describe(outcome(changed = 3), "Silenced"))
    }

    @Test fun skippedChannelsAreReported() {
        assertEquals("Blocked 2 channels · 3 couldn't be changed", describe(outcome(changed = 2, locked = 2, failed = 1), "Blocked"))
    }
}
