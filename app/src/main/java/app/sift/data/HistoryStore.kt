package app.sift.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Notification history, kept separate from settings because it changes on every notification. */
class HistoryStore(context: Context, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(context.filesDir, "history.json"))
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow(load().trimmed())
    val entries: StateFlow<List<HistoryEntry>> = _entries
    private var saveJob: Job? = null

    /** [inPlace] keeps an updating notification (music, navigation) at its original spot instead of moving it up. */
    fun record(e: HistoryEntry, inPlace: Boolean = false) {
        _entries.update { list ->
            val index = list.indexOfFirst { it.key == e.key }
            val prev = list.getOrNull(index)
            val rest = when {
                prev == null -> list
                prev.title == e.title && prev.text == e.text && prev.outcome == e.outcome -> return@update list
                inPlace -> return@update list.toMutableList().also { it[index] = e.copy(time = prev.time) }
                // Rapid updates of the same notification (e.g. progress) replace the previous entry.
                e.time - prev.time < 60_000 -> list - prev
                else -> list
            }
            (listOf(e) + rest).trimmed()
        }
        scheduleSave()
    }

    fun remove(e: HistoryEntry) {
        _entries.update { it - e }
        scheduleSave()
    }

    fun removePackage(pkg: String) {
        _entries.update { it.filterNot { entry -> entry.pkg == pkg } }
        scheduleSave()
    }

    fun clear() {
        _entries.value = emptyList()
        scheduleSave()
    }

    private fun List<HistoryEntry>.trimmed(): List<HistoryEntry> {
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        return filter { it.time >= cutoff }.take(MAX_ENTRIES)
    }

    // Batches writes so a burst of notifications costs one disk write.
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(2_000)
            withContext(NonCancellable) { save(_entries.value) }
        }
    }

    private fun load(): List<HistoryEntry> = runCatching {
        json.decodeFromString<List<HistoryEntry>>(file.readFully().decodeToString())
    }.getOrDefault(emptyList())

    private fun save(list: List<HistoryEntry>) {
        val out = file.startWrite()
        try {
            out.write(json.encodeToString(list).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }

    private companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
        const val MAX_ENTRIES = 5_000
    }
}
