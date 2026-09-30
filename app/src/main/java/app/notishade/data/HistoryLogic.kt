package app.notishade.data

/** Pure history-list logic: dedup/merge of rapid updates and retention trimming. */
object HistoryLogic {
    const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    const val MAX_ENTRIES = 5_000

    /**
     * Merges [e] into [list]. Returns null when nothing changes (identical content already stored).
     * [inPlace] keeps an updating notification (music, navigation) at its original spot instead of moving it up.
     */
    fun merge(list: List<HistoryEntry>, e: HistoryEntry, inPlace: Boolean): List<HistoryEntry>? {
        val index = list.indexOfFirst { it.key == e.key }
        val prev = list.getOrNull(index)
        val rest = when {
            prev == null -> list
            prev.title == e.title && prev.text == e.text && prev.outcome == e.outcome -> return null
            inPlace -> return list.toMutableList().also { it[index] = e.copy(time = prev.time) }
            // Rapid updates of the same notification (e.g. progress) replace the previous entry.
            e.time - prev.time < 60_000 -> list - prev
            else -> list
        }
        return listOf(e) + rest
    }

    fun trim(list: List<HistoryEntry>, now: Long): List<HistoryEntry> {
        val cutoff = now - RETENTION_MS
        return list.filter { it.time >= cutoff }.take(MAX_ENTRIES)
    }
}
