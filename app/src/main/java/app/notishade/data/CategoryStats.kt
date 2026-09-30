package app.notishade.data

/** Per-category notification counts derived from history entries. Pure and unit-tested. */
object CategoryStats {
    data class Counts(val shown: Int, val blocked: Int) {
        val total get() = shown + blocked
    }

    /** Blocked counts dismissals by rule too: both are notifications kept out of the shade. */
    fun perCategory(entries: List<HistoryEntry>): Map<Category, Counts> =
        entries
            .groupingBy { it.category }
            .fold(Counts(0, 0)) { acc, e ->
                if (e.outcome == Outcome.SHOWN) acc.copy(shown = acc.shown + 1) else acc.copy(blocked = acc.blocked + 1)
            }
            .filterValues { it.total > 0 }
}
