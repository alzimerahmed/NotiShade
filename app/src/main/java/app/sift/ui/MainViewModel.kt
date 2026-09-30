package app.sift.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.service.quicksettings.TileService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sift.App
import app.sift.R
import app.sift.data.BackupCodec
import app.sift.data.Batch
import app.sift.data.Category
import app.sift.data.CategoryStats
import app.sift.data.ChannelAction
import app.sift.data.ChannelInfo
import app.sift.data.HistoryCodec
import app.sift.data.HistoryEntry
import app.sift.data.Rule
import app.sift.data.Schedule
import app.sift.data.ThemeMode
import app.sift.service.PauseTileService
import app.sift.widget.BlockedWidgetProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiMessage(val text: String, val undo: Batch? = null, val onUndo: (() -> Unit)? = null)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as App
    val apps = app.repo.apps
    val progress = app.repo.progress
    val access = app.access.state
    val store = app.store.data
    val message = MutableStateFlow<UiMessage?>(null)
    private var scanned = false

    fun say(text: String) {
        message.value = UiMessage(text)
    }

    private fun launch(block: suspend () -> Unit) = viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            say(e.message ?: e.javaClass.simpleName)
        }
    }

    fun scan() = launch {
        val count = app.repo.scanAll()
        say(app.resources.getQuantityString(R.plurals.msg_scanned, count, count))
    }

    fun scanOnce() {
        if (!scanned) {
            scanned = true
            launch { app.repo.scanAll() }
        }
    }

    fun refreshAccess() = app.access.refresh()

    /** Bulk action; "Allow" leaves already-allowed channels alone so pop-up channels aren't downgraded. */
    fun bulk(targets: List<ChannelInfo>, action: ChannelAction, title: String) = launch {
        val list = if (action == ChannelAction.ALERT) targets.filter { it.status() != Status.ALLOWED } else targets
        if (list.isEmpty()) return@launch say(app.getString(R.string.msg_already_allowed))
        val outcome = app.engine.apply("${action.verb}: $title", list, action)
        message.value = UiMessage(outcome.describe(app, action.verb), outcome.batch)
    }

    fun setChannel(c: ChannelInfo, action: ChannelAction) = launch {
        val outcome = app.engine.apply("${action.verb}: ${c.appLabel} · ${c.channel.name}", listOf(c), action)
        message.value = UiMessage(outcome.describe(app, action.verb), outcome.batch)
    }

    /** Blocks (or re-allows) a whole category, including channels apps add to it later. */
    fun setCategoryBlocked(cat: Category, channels: List<ChannelInfo>, block: Boolean) = launch {
        val previous = app.store.data.value.policies[cat]
        val action = if (block) ChannelAction.BLOCK else ChannelAction.ALERT
        val targets = channels.filter { (it.status() == Status.BLOCKED) != block }
        when {
            block -> setPolicy(cat, ChannelAction.BLOCK)
            previous == ChannelAction.BLOCK -> setPolicy(cat, null)
        }
        if (targets.isEmpty()) return@launch
        val outcome = app.engine.apply("${action.verb}: ${cat.label}", targets, action)
        message.value = UiMessage("${cat.label} · ${outcome.describe(app, action.verb)}", outcome.batch) { setPolicy(cat, previous) }
    }

    fun setPolicy(cat: Category, action: ChannelAction?) = app.store.update {
        it.copy(policies = if (action == null) it.policies - cat else it.policies + (cat to action))
    }

    /** Quiet hours for a category or rule; null clears the schedule. */
    fun setSchedule(key: String, schedule: Schedule?) = app.store.update {
        it.copy(schedules = if (schedule == null) it.schedules - key else it.schedules + (key to schedule))
    }

    fun setOverride(c: ChannelInfo, cat: Category?) = app.store.update {
        it.copy(overrides = if (cat == null) it.overrides - c.key else it.overrides + (c.key to cat))
    }

    /**
     * Per-app default: fans one action out to every channel of the app as ONE undoable batch
     * (locked channels reported by the outcome), and stores it so channels the app adds later
     * get the same action on arrival. Clearing it only affects future channels.
     */
    fun setAppDefault(pkg: String, label: String, action: ChannelAction?, channels: List<ChannelInfo>) = launch {
        app.store.update {
            it.copy(appDefaults = if (action == null) it.appDefaults - pkg else it.appDefaults + (pkg to action))
        }
        if (action == null) return@launch say(app.getString(R.string.msg_appdefault_keep, label))
        val targets = if (action == ChannelAction.ALERT) channels.filter { it.status() != Status.ALLOWED } else channels
        if (targets.isEmpty()) return@launch say(app.getString(R.string.msg_appdefault_willbe, label, action.label.lowercase()))
        val outcome = app.engine.apply("${action.verb}: $label · all channels", targets, action)
        message.value = UiMessage(app.getString(R.string.all_channels) + " · " + outcome.describe(app, action.verb), outcome.batch)
    }

    fun undo(batch: Batch) = launch { say(app.getString(R.string.undone) + " · " + app.engine.undo(batch).describe(app, app.getString(R.string.verb_restored))) }

    val history = app.history.entries

    /** Per-category shown/blocked counts, derived from history entries. */
    private val _categoryStats = MutableStateFlow(emptyMap<Category, CategoryStats.Counts>())
    val categoryStats: StateFlow<Map<Category, CategoryStats.Counts>> = _categoryStats

    init {
        viewModelScope.launch { app.history.entries.collect { _categoryStats.value = CategoryStats.perCategory(it) } }
    }

    fun channelFor(e: HistoryEntry) = apps.value.firstOrNull { it.pkg == e.pkg }?.channels?.firstOrNull { it.channel.id == e.channelId }

    fun setChannelFromLog(e: HistoryEntry, action: ChannelAction) {
        val c = channelFor(e) ?: return say(app.getString(R.string.err_category_unavailable))
        setChannel(c, action)
    }

    fun deleteEntry(e: HistoryEntry) = app.history.remove(e)

    fun clearHistory() {
        app.history.clear()
        app.scope.launch(Dispatchers.Default) { runCatching { BlockedWidgetProvider.refresh(app) } }
    }

    fun setLogExcluded(pkg: String, excluded: Boolean) {
        app.store.update { data ->
            data.copy(
                logExcludedApps = if (excluded) data.logExcludedApps + pkg else data.logExcludedApps - pkg,
                hints = if (excluded) data.hints.filterKeys { !it.startsWith("$pkg|") } else data.hints,
            )
        }
        if (excluded) app.history.removePackage(pkg)
    }

    fun saveRule(rule: Rule) = app.store.update { d ->
        val exists = d.rules.any { it.id == rule.id }
        d.copy(rules = if (exists) d.rules.map { if (it.id == rule.id) rule else it } else d.rules + rule)
    }

    fun toggleRule(rule: Rule) = saveRule(rule.copy(enabled = !rule.enabled))

    fun deleteRule(rule: Rule) = app.store.update { it.copy(rules = it.rules.filterNot { r -> r.id == rule.id }) }

    fun setTheme(mode: ThemeMode) = app.store.update { it.copy(theme = mode) }

    fun setMaterialYou(on: Boolean) = app.store.update { it.copy(materialYou = on) }

    /** Global pause; mirrors the Quick Settings tile, so nudge any pinned tile to refresh. */
    fun setPaused(on: Boolean) {
        app.store.update { it.copy(paused = on) }
        TileService.requestListeningState(getApplication(), ComponentName(getApplication(), PauseTileService::class.java))
        say(if (on) app.getString(R.string.msg_paused_on) else app.getString(R.string.msg_paused_off))
    }

    fun exportTo(uri: Uri) = launch {
        val out = getApplication<App>().contentResolver.openOutputStream(uri) ?: error(app.getString(R.string.err_open_file))
        out.use { it.write(BackupCodec.encode(app.store.data.value).encodeToByteArray()) }
        say(app.getString(R.string.msg_settings_exported))
    }

    /** History export; the format is chosen by the caller (CSV or JSON). */
    fun exportHistoryCsvTo(uri: Uri) = exportHistoryTo(uri, HistoryCodec::encodeCsv, app.getString(R.string.msg_history_exported_csv))

    fun exportHistoryJsonTo(uri: Uri) = exportHistoryTo(uri, HistoryCodec::encodeJson, app.getString(R.string.msg_history_exported_json))

    private fun exportHistoryTo(uri: Uri, encode: (List<HistoryEntry>) -> String, done: String) = launch {
        val out = getApplication<App>().contentResolver.openOutputStream(uri) ?: error(app.getString(R.string.err_open_file))
        out.use { it.write(encode(app.history.entries.value).encodeToByteArray()) }
        say(done)
    }

    fun importFrom(uri: Uri) = launch {
        val text = getApplication<App>().contentResolver.openInputStream(uri)?.use {
            val bytes = it.readNBytes(MAX_BACKUP_BYTES + 1)
            if (bytes.size > MAX_BACKUP_BYTES) error(app.getString(R.string.err_file_too_large))
            bytes.decodeToString()
        } ?: error(app.getString(R.string.err_read_file))
        val imported = BackupCodec.decode(text) ?: error(app.getString(R.string.err_not_sift_file))
        app.store.update { imported }
        // Settings alone aren't enough: re-apply the imported policies to the actual channels.
        say(app.getString(R.string.msg_applying_import))
        app.repo.scanAll()
        val apps = app.repo.apps.value
        imported.policies.forEach { (cat, action) ->
            val targets = apps.flatMap { a -> a.channels.filter { it.category == cat } }
            if (targets.isNotEmpty()) app.engine.apply("Restored: ${action.verb.lowercase()} ${cat.label}", targets, action)
        }
        val blocked = apps.flatMap { a -> a.channels.filter { it.key in imported.logBlocked } }
        if (blocked.isNotEmpty()) app.engine.apply("Restored blocked channels", blocked, ChannelAction.BLOCK)
        // App defaults only govern future channels; apply them to existing channels too, so a
        // restore matches what the source device looked like.
        imported.appDefaults.forEach { (pkg, action) ->
            val targets = apps.firstOrNull { it.pkg == pkg }?.channels.orEmpty().filter { !alreadyAllowed(it, action) }
            if (targets.isNotEmpty()) app.engine.apply("Restored: ${action.verb.lowercase()} ${targets.firstOrNull()?.appLabel ?: pkg}", targets, action)
        }
        say(app.getString(R.string.msg_settings_imported))
    }

    /** "Allow" must not downgrade channels the user set to pop up; the rest are enforced as-is. */
    private fun alreadyAllowed(c: ChannelInfo, action: ChannelAction) =
        action == ChannelAction.ALERT && c.channel.importance >= android.app.NotificationManager.IMPORTANCE_DEFAULT

    private companion object {
        const val MAX_BACKUP_BYTES = 2_000_000
    }
}
