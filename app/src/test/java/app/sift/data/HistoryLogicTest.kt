package app.sift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryLogicTest {
    private fun entry(
        key: String,
        time: Long,
        title: String = "t",
        text: String = "x",
        outcome: Outcome = Outcome.SHOWN,
    ) = HistoryEntry(time, key, "com.a", "App", "ch", "Channel", Category.OTHER, title, text, outcome)

    @Test fun newEntryGoesToTheFront() {
        val a = entry("1", 100)
        val b = entry("2", 200)
        assertEquals(listOf(b, a), HistoryLogic.merge(listOf(a), b, inPlace = false))
    }

    @Test fun identicalContentIsNotReRecorded() {
        val a = entry("1", 100)
        assertNull(HistoryLogic.merge(listOf(a), entry("1", 200), inPlace = false))
    }

    @Test fun inPlaceUpdateKeepsOriginalTimeAndSpot() {
        val a = entry("1", 100)
        val updated = entry("1", 500, title = "now playing 2")
        assertEquals(listOf(updated.copy(time = 100)), HistoryLogic.merge(listOf(a), updated, inPlace = true))
    }

    @Test fun rapidUpdatesReplaceThePreviousEntry() {
        val a = entry("1", 100)
        val b = entry("1", 100 + 59_999, text = "x 50%")
        assertEquals(listOf(b), HistoryLogic.merge(listOf(a), b, inPlace = false))
    }

    @Test fun slowerUpdatesPushANewEntry() {
        val a = entry("1", 100)
        val b = entry("1", 100 + 60_000, text = "x 100%")
        assertEquals(listOf(b, a), HistoryLogic.merge(listOf(a), b, inPlace = false))
    }

    @Test fun contentChangeReplacesEvenInPlace() {
        val a = entry("1", 100)
        val blocked = entry("1", 500, outcome = Outcome.BLOCKED)
        assertEquals(listOf(blocked.copy(time = 100)), HistoryLogic.merge(listOf(a), blocked, inPlace = true))
    }

    @Test fun trimDropsExpiredEntries() {
        val now = 10L * HistoryLogic.RETENTION_MS
        val expired = entry("old", now - HistoryLogic.RETENTION_MS - 1)
        val fresh = entry("new", now)
        assertEquals(listOf(fresh), HistoryLogic.trim(listOf(expired, fresh), now))
    }

    @Test fun trimCapsAtMaxEntries() {
        val now = 1_000_000L
        val many = (0 until HistoryLogic.MAX_ENTRIES + 10).map { entry(it.toString(), now - it) }
        val trimmed = HistoryLogic.trim(many, now)
        assertEquals(HistoryLogic.MAX_ENTRIES, trimmed.size)
        assertEquals("0", trimmed.first().key)
    }
}
