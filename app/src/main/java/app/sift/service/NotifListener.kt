package app.sift.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.sift.App
import app.sift.data.BootMarker
import app.sift.data.Category
import app.sift.data.ChannelAction
import app.sift.data.ChannelInfo
import app.sift.data.Classifier
import app.sift.data.HistoryEntry
import app.sift.data.Outcome
import app.sift.data.RuleMatcher
import app.sift.data.RuleAction
import app.sift.data.Schedule
import app.sift.data.StoreData
import app.sift.data.keyOf
import java.time.ZoneId
import kotlinx.coroutines.launch

class NotifListener : NotificationListenerService() {
    companion object {
        @Volatile
        var instance: NotifListener? = null
            private set

        private val textKeys = listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT,
        )
    }

    private val app get() = App.of(this)

    override fun onListenerConnected() {
        instance = this
        app.access.refresh()
        val ranking = currentRanking
        runCatching { activeNotifications }.getOrNull()?.sortedBy { it.postTime }?.forEach { sbn ->
            if (worthLogging(sbn)) record(sbn, channelOf(sbn, ranking), Outcome.SHOWN, time = sbn.postTime)
        }
        // Boot resilience: the system restores channel importance itself, but only for channels
        // it knows about; re-apply our policies in the background, throttled. The hot path
        // (onNotificationPosted) is untouched.
        app.scope.launch { runCatching { reenforcePolicies() } }
    }

    /** Re-applies stored policies (category actions, app defaults; overrides via classification). */
    private suspend fun reenforcePolicies() {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        val last = BootMarker.load(applicationContext)
        if (!BootMarker.shouldRun(last, boot, System.currentTimeMillis())) return
        app.repo.scanAll()
        val apps = app.repo.apps.value
        val d = app.store.data.value
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        d.policies.forEach { (cat, action) ->
            // Quiet hours: a category with a schedule is only re-enforced inside its window.
            val schedule = d.schedules[Schedule.keyFor(cat)]
            if (schedule != null && !schedule.activeAt(now, zone)) return@forEach
            val targets = apps.flatMap { a -> a.channels.filter { it.category == cat && !alreadyAllowed(it, action) } }
            if (targets.isNotEmpty()) {
                app.engine.apply("Re-applied: ${action.verb.lowercase()} ${cat.label}", targets, action)
            }
        }
        d.appDefaults.forEach { (pkg, action) ->
            val targets = apps.firstOrNull { it.pkg == pkg }?.channels.orEmpty().filter { !alreadyAllowed(it, action) }
            if (targets.isNotEmpty()) {
                app.engine.apply("Re-applied: ${action.verb.lowercase()} ${targets.firstOrNull()?.appLabel ?: pkg}", targets, action)
            }
        }
        BootMarker.save(applicationContext, boot, System.currentTimeMillis())
    }

    /** "Allow" must not downgrade channels the user set to pop up; the rest are enforced as-is. */
    private fun alreadyAllowed(c: ChannelInfo, action: ChannelAction) =
        action == ChannelAction.ALERT && c.channel.importance >= NotificationManager.IMPORTANCE_DEFAULT

    override fun onListenerDisconnected() {
        instance = null
        app.access.refresh()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (sbn.packageName == packageName) return
        // Global pause (Quick Settings tile / Settings): notifications pass through untouched.
        // Deliberately not logged either — a paused session is a deliberate gap in the log,
        // not a stream of "shown" entries the user didn't ask to record.
        if (app.store.data.value.paused) return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val channel = channelOf(sbn, rankingMap)
        val channelId = sbn.notification.channelId ?: channel?.id
        // Android 16+ may move a notification into a system bundle channel; that's a free classification hint.
        val bundled = channel != null && channel.id != channelId && channel.id in Classifier.bundleChannelIds
        if (channelId != null) {
            sbn.notification.category?.let { app.store.addHint(sbn.packageName, channelId, it) }
            if (bundled) app.store.addHint(sbn.packageName, channelId, channel.id)
            app.scope.launch { runCatching { app.engine.onChannelSeen(sbn.packageName, channelId) } }
        }
        val d = app.store.data.value
        val clearable = !sbn.isOngoing && sbn.isClearable
        val blockAndLog = channelId != null && keyOf(sbn.packageName, channelId) in d.logBlocked &&
            (bundled || channel?.importance == NotificationManager.IMPORTANCE_MIN)
        if (blockAndLog && clearable && channelId != null) {
            // Quiet hours: outside the window the block pauses and the notification shows as usual.
            // Only classified when a schedule exists, so the hot path stays cheap otherwise.
            val schedule = if (d.schedules.isEmpty()) {
                null
            } else {
                d.schedules[Schedule.keyFor(categoryOf(sbn, channel, channelId, d))]
            }
            if (schedule == null || schedule.activeAt(now, zone)) {
                // Group summaries are removed too, but only real notifications are logged.
                cancelNotification(sbn.key)
                if (worthLogging(sbn)) record(sbn, channel, Outcome.BLOCKED)
                return
            }
        }
        if (!worthLogging(sbn)) return

        val rule = if (clearable) {
            val extras = sbn.notification.extras
            RuleMatcher.match(d.rules, sbn.packageName, textKeys.map { extras.getCharSequence(it) })
                // Quiet hours: a rule with a schedule only fires inside its window.
                ?.takeIf { r -> d.schedules[Schedule.keyFor(r.id)]?.activeAt(now, zone) != false }
        } else {
            null
        }
        when {
            rule != null -> {
                when (rule.action) {
                    RuleAction.DISMISS -> cancelNotification(sbn.key)
                    RuleAction.SNOOZE -> snoozeNotification(sbn.key, 60 * 60 * 1000L)
                }
                record(sbn, channel, Outcome.RULE, reason = rule.name)
            }
            else -> record(sbn, channel, Outcome.SHOWN)
        }
    }

    override fun onNotificationChannelModified(pkg: String, user: UserHandle, channel: NotificationChannel, modificationType: Int) {
        if (user != Process.myUserHandle()) return
        when {
            modificationType == NOTIFICATION_CHANNEL_OR_GROUP_ADDED ->
                app.scope.launch { runCatching { app.engine.onChannelSeen(pkg, channel.id) } }
            // Blocked in Android Settings: switch it to our block so its notifications still get logged.
            modificationType == NOTIFICATION_CHANNEL_OR_GROUP_UPDATED && channel.importance == NotificationManager.IMPORTANCE_NONE ->
                app.scope.launch { runCatching { app.engine.adoptBlocked(pkg) } }
        }
    }

    // Group summaries only wrap their children, which are logged individually.
    private fun worthLogging(sbn: StatusBarNotification) =
        sbn.packageName != packageName && (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0

    private fun channelOf(sbn: StatusBarNotification, rankingMap: RankingMap?): NotificationChannel? {
        val r = Ranking()
        return if (rankingMap?.getRanking(sbn.key, r) == true) r.channel else null
    }

    private fun record(
        sbn: StatusBarNotification,
        channel: NotificationChannel?,
        outcome: Outcome,
        reason: String? = null,
        time: Long = System.currentTimeMillis(),
    ) {
        val d = app.store.data.value
        if (sbn.packageName in d.logExcludedApps) return
        val extras = sbn.notification.extras
        val channelId = sbn.notification.channelId ?: channel?.id.orEmpty()
        val known = app.repo.apps.value.firstOrNull { it.pkg == sbn.packageName }
        val knownChannel = known?.channels?.firstOrNull { it.channel.id == channelId }
        val category = categoryOf(sbn, channel, channelId, d)
        app.history.record(
            HistoryEntry(
                time = time,
                key = sbn.key,
                pkg = sbn.packageName,
                app = known?.label ?: appLabel(sbn.packageName),
                channelId = channelId,
                channelName = knownChannel?.channel?.name?.toString() ?: channel?.name?.toString().orEmpty(),
                category = category,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                    ?.toString().orEmpty(),
                outcome = outcome,
                reason = reason,
            ),
            inPlace = sbn.isOngoing,
        )
    }

    /** Overrides first, then the scanned classification, then the classifier fallback. */
    private fun categoryOf(sbn: StatusBarNotification, channel: NotificationChannel?, channelId: String, d: StoreData): Category {
        val key = keyOf(sbn.packageName, channelId)
        val knownChannel = app.repo.apps.value.firstOrNull { it.pkg == sbn.packageName }
            ?.channels?.firstOrNull { it.channel.id == channelId }
        return d.overrides[key]
            ?: knownChannel?.category
            ?: channel?.let { Classifier.classify(it, null, d.hints[key].orEmpty()).first }
            ?: Category.OTHER
    }

    private fun appLabel(pkg: String) = runCatching {
        packageManager.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0)).loadLabel(packageManager).toString()
    }.getOrDefault(pkg)
}
