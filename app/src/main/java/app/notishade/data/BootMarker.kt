package app.notishade.data

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

    /**
     * Runs only when the boot changed (first connect since boot, or never run). Routine listener
     * rebinds on the same boot must NOT re-enforce: that would stomp channels the user manually
     * re-allowed after our policies were applied.
     */
    fun shouldRun(last: State?, boot: Int, now: Long): Boolean = last == null || last.boot != boot
}
