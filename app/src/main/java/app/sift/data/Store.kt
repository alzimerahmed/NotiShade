package app.sift.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class Store(context: Context, private val scope: CoroutineScope) {
    private val file = AtomicFile(File(context.filesDir, "store.json"))
    private val writeLock = Mutex()
    private val _data = MutableStateFlow(load())
    val data: StateFlow<StoreData> = _data

    fun update(block: (StoreData) -> StoreData) {
        _data.update(block)
        scope.launch(Dispatchers.IO) { writeLock.withLock { save(_data.value) } }
    }

    fun addHint(pkg: String, channelId: String, notificationCategory: String) {
        val key = keyOf(pkg, channelId)
        if (data.value.hints[key]?.contains(notificationCategory) == true) return
        update { it.copy(hints = it.hints + (key to (it.hints[key].orEmpty() + notificationCategory))) }
    }

    fun addBatch(batch: Batch) = update { it.copy(history = (listOf(batch) + it.history).take(50)) }

    private fun load(): StoreData = runCatching {
        StoreMigrations.decode(file.readFully().decodeToString())
    }.getOrDefault(StoreData())

    private fun save(d: StoreData) {
        val out = file.startWrite()
        try {
            out.write(StoreMigrations.json.encodeToString(StoreData.serializer(), d).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
        }
    }
}
