package app.sift.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.sift.App
import app.sift.data.Batch
import app.sift.data.Category
import app.sift.data.ChannelAction
import app.sift.data.ChannelInfo
import app.sift.data.HistoryEntry
import app.sift.data.Rule
import app.sift.data.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
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

    fun scan() = launch { say("Scanned ${app.repo.scanAll()} apps") }

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
        if (list.isEmpty()) return@launch say("Already allowed")
        val outcome = app.engine.apply("${action.verb}: $title", list, action)
        message.value = UiMessage(outcome.describe(action.verb), outcome.batch)
    }

    fun setChannel(c: ChannelInfo, action: ChannelAction) = launch {
        val outcome = app.engine.apply("${action.verb}: ${c.appLabel} · ${c.channel.name}", listOf(c), action)
        message.value = UiMessage(outcome.describe(action.verb), outcome.batch)
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
        message.value = UiMessage("${cat.label} · ${outcome.describe(action.verb)}", outcome.batch) { setPolicy(cat, previous) }
    }

    fun setPolicy(cat: Category, action: ChannelAction?) = app.store.update {
        it.copy(policies = if (action == null) it.policies - cat else it.policies + (cat to action))
    }

    fun setOverride(c: ChannelInfo, cat: Category?) = app.store.update {
        it.copy(overrides = if (cat == null) it.overrides - c.key else it.overrides + (c.key to cat))
    }

    fun undo(batch: Batch) = launch { say("Undone · " + app.engine.undo(batch).describe("Restored")) }

    val history = app.history.entries

    fun channelFor(e: HistoryEntry) = apps.value.firstOrNull { it.pkg == e.pkg }?.channels?.firstOrNull { it.channel.id == e.channelId }

    fun setChannelFromLog(e: HistoryEntry, action: ChannelAction) {
        val c = channelFor(e) ?: return say("That category isn't available anymore")
        setChannel(c, action)
    }

    fun deleteEntry(e: HistoryEntry) = app.history.remove(e)

    fun clearHistory() = app.history.clear()

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
}
