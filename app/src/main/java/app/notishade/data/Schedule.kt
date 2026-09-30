package app.notishade.data

import java.time.Instant
import java.time.ZoneId
import kotlinx.serialization.Serializable

/**
 * A quiet-hours window for one category or rule: the action it belongs to only applies while
 * this schedule is active. Pure and framework-free — [activeAt] takes the instant from the
 * caller, so it never reads the clock itself and is unit-testable.
 *
 * Times are minutes of the local day. A window whose start is after its end wraps midnight
 * (e.g. 22:00–07:00); start == end is an empty window (never active). [days] holds ISO
 * day-of-week numbers (1 = Monday … 7 = Sunday); empty means every day.
 */
@Serializable
data class Schedule(
    val startMinutes: Int,
    val endMinutes: Int,
    val days: Set<Int> = emptySet(),
    val enabled: Boolean = true,
) {
    /** Is the window active at [epochMillis] in [zone]? A pure predicate call. */
    fun activeAt(epochMillis: Long, zone: ZoneId): Boolean {
        if (!enabled) return false
        val time = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val minute = time.hour * 60 + time.minute
        val day = time.dayOfWeek.value
        return if (startMinutes < endMinutes) {
            daySelected(day) && minute in startMinutes until endMinutes
        } else if (startMinutes > endMinutes) {
            // Wraps midnight: the evening part belongs to today, the small-hours part to yesterday.
            (minute >= startMinutes && daySelected(day)) || (minute < endMinutes && daySelected(day - 1))
        } else {
            false
        }
    }

    private fun daySelected(isoDay: Int) = days.isEmpty() || ((isoDay + 6) % 7 + 1) in days

    companion object {
        fun keyFor(category: Category) = "cat:${category.name}"
        fun keyFor(ruleId: Long) = "rule:$ruleId"
    }
}
