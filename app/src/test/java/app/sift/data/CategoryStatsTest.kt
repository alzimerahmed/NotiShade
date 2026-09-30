package app.sift.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryStatsTest {
    private fun entry(cat: Category, outcome: Outcome) = HistoryEntry(
        time = 0, key = "k", pkg = "com.a", app = "A", channelId = "ch", channelName = "Ch",
        category = cat, title = "t", text = "", outcome = outcome,
    )

    @Test fun countsShownAndBlockedPerCategory() {
        val stats = CategoryStats.perCategory(
            listOf(
                entry(Category.PROMO, Outcome.SHOWN),
                entry(Category.PROMO, Outcome.SHOWN),
                entry(Category.PROMO, Outcome.BLOCKED),
                entry(Category.MESSAGES, Outcome.RULE),
            ),
        )
        assertEquals(CategoryStats.Counts(shown = 2, blocked = 1), stats[Category.PROMO])
        assertEquals(CategoryStats.Counts(shown = 0, blocked = 1), stats[Category.MESSAGES])
    }

    @Test fun rulesCountAsBlocked() {
        val stats = CategoryStats.perCategory(listOf(entry(Category.SOCIAL, Outcome.RULE)))
        assertEquals(0, stats.getValue(Category.SOCIAL).shown)
        assertEquals(1, stats.getValue(Category.SOCIAL).blocked)
    }

    @Test fun emptyHistoryGivesEmptyMap() {
        assertEquals(emptyMap<Category, CategoryStats.Counts>(), CategoryStats.perCategory(emptyList()))
    }

    @Test fun categoriesWithoutEntriesAreAbsent() {
        val stats = CategoryStats.perCategory(listOf(entry(Category.NEWS, Outcome.SHOWN)))
        assertNull(stats[Category.PROMO])
    }
}
