package app.sift.engine

import android.app.NotificationManager
import app.sift.App
import app.sift.R
import app.sift.backend.Channels
import app.sift.data.Batch
import app.sift.data.ChannelAction
import app.sift.data.ChannelChange
import app.sift.data.ChannelInfo
import app.sift.data.RawApp
import app.sift.data.Schedule
import app.sift.data.keyOf
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BulkEngine(private val app: App) {
    data class Outcome(val changed: Int, val unchanged: Int, val locked: Int, val failed: Int, val error: String?, val batch: Batch?) {
        /** Localized summary for snackbars; [app] supplies the string resources. */
        fun describe(app: App, verb: String): String = describe(
            changed, locked + failed, error, verb,
            app.getString(R.string.nothing_changed),
            { n -> app.resources.getQuantityString(R.plurals.channels_count, n, n) },
            app.getString(R.string.skipped_fmt),
        )

        companion object {
            /** Pure formatting core, unit-testable without Android types. */
            fun describe(
                changed: Int,
                skipped: Int,
                error: String?,
                verb: String,
                nothingChanged: String,
                channels: (Int) -> String,
                skippedFmt: String,
            ): String = buildString {
                append(if (changed == 0) nothingChanged else "$verb $changed ${channels(changed)}")
                if (skipped > 0) append(skippedFmt.format(skipped))
                if (changed == 0 && error != null) append(": $error")
            }
        }
    }

    private data class Target(val pkg: String, val uid: Int, val channelId: String, val importance: Int, val logged: Boolean)

    private val mutex = Mutex()

    suspend fun apply(title: String, targets: List<ChannelInfo>, action: ChannelAction): Outcome = setImportance(
        title, targets.map { Target(it.pkg, it.uid, it.channel.id, action.importance, action == ChannelAction.BLOCK) }, record = true,
    )

    suspend fun undo(batch: Batch): Outcome {
        val outcome = setImportance(
            "Undo", batch.changes.map { Target(it.pkg, it.uid, it.channelId, it.before, it.loggedBefore) }, record = false,
        )
        app.store.update { it.copy(history = it.history.filterNot { b -> b.id == batch.id }) }
        return outcome
    }

    /**
     * Applies policies to channels never seen before, then marks them as known. An app default
     * (per-app control) takes precedence over the category policy for that app's new channels.
     */
    suspend fun enforceNew(raws: Collection<RawApp>) {
        val d = app.store.data.value
        val fresh = raws.flatMap { app.repo.toInfo(it, d).channels }.filter { it.key !in d.known }
        if (fresh.isEmpty()) return
        app.store.update { it.copy(known = it.known + fresh.map { c -> c.key }) }
        fresh.groupBy { it.pkg }.forEach { (pkg, list) ->
            val appDefault = d.appDefaults[pkg]
            if (appDefault != null) {
                apply("Auto ${appDefault.label.lowercase()}: new ${list.first().appLabel} channels", list, appDefault)
            } else {
                list.groupBy { it.category }.forEach { (cat, l) ->
                    val action = d.policies[cat] ?: return@forEach
                    // Quiet hours: the category action only applies inside its window.
                    val schedule = d.schedules[Schedule.keyFor(cat)]
                    if (schedule != null && !schedule.activeAt(System.currentTimeMillis(), ZoneId.systemDefault())) return@forEach
                    apply("Auto ${action.label.lowercase()}: new ${cat.label} channels", l, action)
                }
            }
        }
    }

    suspend fun onChannelSeen(pkg: String, channelId: String) {
        if (keyOf(pkg, channelId) in app.store.data.value.known) return
        val raw = app.repo.rescan(pkg) ?: return
        enforceNew(listOf(raw))
    }

    /**
     * Android drops notifications from channels it blocks before we can see them, so blocked channels
     * (e.g. turned off in Android Settings) are switched to our own block, which keeps them in Logs.
     */
    suspend fun adoptBlocked(raws: Collection<RawApp>) {
        val targets = raws.flatMap { r ->
            r.channels.filter { it.importance == NotificationManager.IMPORTANCE_NONE }
                .map { Target(r.pkg, r.uid, it.id, NotificationManager.IMPORTANCE_MIN, logged = true) }
        }
        if (targets.isNotEmpty()) setImportance("", targets, record = false)
    }

    suspend fun adoptBlocked(pkg: String) {
        app.repo.rescan(pkg)?.let { adoptBlocked(listOf(it)) }
    }

    private data class Attempt(val target: Target, val name: String, val before: Int, val wasLogged: Boolean)

    private suspend fun setImportance(title: String, targets: List<Target>, record: Boolean): Outcome =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                app.access.requireReady()
                val logBlocked = app.store.data.value.logBlocked
                val changes = mutableListOf<ChannelChange>()
                var unchanged = 0
                var locked = 0
                var failed = 0
                var error: String? = null

                for ((owner, group) in targets.groupBy { it.pkg to it.uid }) {
                    val (pkg, uid) = owner
                    val current = try {
                        Channels.list(pkg, uid).associateBy { it.id }
                    } catch (e: Exception) {
                        failed += group.size
                        error = error ?: e.message
                        continue
                    }
                    val attempted = mutableListOf<Attempt>()
                    for (t in group) {
                        val ch = current[t.channelId] ?: continue
                        val wasLogged = keyOf(pkg, t.channelId) in logBlocked && ch.importance == NotificationManager.IMPORTANCE_MIN
                        if (ch.importance == t.importance && wasLogged == t.logged) {
                            unchanged++
                            continue
                        }
                        val before = ch.importance
                        try {
                            if (before != t.importance) {
                                ch.importance = t.importance
                                Channels.update(pkg, uid, ch)
                            }
                            attempted += Attempt(t, ch.name.toString(), before, wasLogged)
                        } catch (e: Exception) {
                            failed++
                            error = error ?: e.message
                        }
                    }
                    // The system silently ignores some changes (e.g. blocking non-blockable channels), so verify.
                    val after = runCatching { Channels.list(pkg, uid).associateBy { it.id } }.getOrDefault(emptyMap())
                    for (a in attempted) {
                        if (after[a.target.channelId]?.importance == a.target.importance) {
                            changes += ChannelChange(
                                pkg, uid, a.target.channelId, a.name, a.before, a.target.importance,
                                loggedBefore = a.wasLogged, loggedAfter = a.target.logged,
                            )
                        } else {
                            locked++
                        }
                    }
                    if (attempted.isNotEmpty()) app.repo.rescan(pkg)
                }

                val nowLogged = changes.filter { it.loggedAfter }.map { keyOf(it.pkg, it.channelId) }
                val noLongerLogged = changes.filter { !it.loggedAfter }.map { keyOf(it.pkg, it.channelId) }.toSet()
                if (nowLogged.isNotEmpty() || noLongerLogged.isNotEmpty()) {
                    app.store.update { it.copy(logBlocked = it.logBlocked - noLongerLogged + nowLogged) }
                }

                val batch = if (record && changes.isNotEmpty()) {
                    Batch(System.nanoTime(), System.currentTimeMillis(), title, changes).also(app.store::addBatch)
                } else {
                    null
                }
                Outcome(changes.size, unchanged, locked, failed, error, batch)
            }
        }
}
