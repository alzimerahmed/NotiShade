package app.sift.data

import android.content.Context
import java.io.File

/**
 * Persists the last boot-time policy re-enforcement as "bootCount lastAt" in a tiny file, so the
 * listener can tell a first-connect-after-boot from a routine rebind. The decision is pure and
 * unit-tested; only the file I/O touches Android.
 */
object BootMarker {
    data class State(val boot: Int, val at: Long)

    private const val NAME = "boot-marker"

    fun load(context: Context): State? = runCatching {
        val parts = File(context.filesDir, NAME).readText().trim().split(' ')
        State(parts[0].toInt(), parts[1].toLong())
    }.getOrNull()

    fun save(context: Context, boot: Int, at: Long) {
        runCatching { File(context.filesDir, NAME).writeText("$boot $at") }
    }

    /** How long to wait before a full re-enforcement on the same boot. */
    const val MIN_INTERVAL_MILLIS = 60_000L

    /**
     * Runs when the boot changed (first connect since boot, or never run), or when at least
     * [MIN_INTERVAL_MILLIS] passed since the last run on this boot.
     */
    fun shouldRun(last: State?, boot: Int, now: Long): Boolean = when (last) {
        null -> true
        else -> last.boot != boot || now - last.at >= MIN_INTERVAL_MILLIS
    }
}
