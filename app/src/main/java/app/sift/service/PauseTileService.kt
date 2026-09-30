package app.sift.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.sift.App
import app.sift.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * "Pause blocking" tile. The flag itself lives in [app.sift.data.StoreData.paused]; the tile
 * only reflects and flips it. Freshness: the tile re-reads the store in `onStartListening`
 * (the system calls it whenever the shade opens), and the in-app toggle calls
 * `TileService.requestListeningState` so a pinned tile updates immediately.
 */
class PauseTileService : TileService() {
    private val app get() = App.of(this)
    private var scope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        render(app.store.data.value.paused)
        // Keep the tile live while the shade is open, in case the state changes mid-session.
        scope?.cancel()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
            it.launch { app.store.data.collect { render(it.paused) } }
        }
    }

    override fun onStopListening() {
        scope?.cancel()
        scope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val next = !app.store.data.value.paused
        app.store.update { it.copy(paused = next) }
        render(next)
    }

    private fun render(paused: Boolean) {
        val tile = qsTile ?: return
        runCatching {
            tile.state = if (paused) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = getString(if (paused) R.string.tile_paused else R.string.tile_active)
            tile.updateTile()
        }
    }
}
