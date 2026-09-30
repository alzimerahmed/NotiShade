package app.sift.data

import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.annotation.StringRes
import app.sift.R
import kotlinx.serialization.Serializable

// `label`/`description` stay as English keys because they leak into persisted data
// (batch titles, rule names); UI renders the localized [labelRes]/[descRes] instead.
@Serializable
enum class Category(val label: String, val description: String, @StringRes val labelRes: Int, @StringRes val descRes: Int) {
    PROMO("Promotions", "Offers, deals, coupons and marketing pushes", R.string.cat_promo, R.string.cat_promo_desc),
    RECOMMENDATIONS("Recommendations", "Suggestions, trending and \"for you\" picks", R.string.cat_recommendations, R.string.cat_recommendations_desc),
    NEWS("News & content", "Headlines, new posts, episodes and live updates", R.string.cat_news, R.string.cat_news_desc),
    SOCIAL("Social activity", "Likes, comments, follows and mentions", R.string.cat_social, R.string.cat_social_desc),
    MESSAGES("Messages", "Chats, direct messages and email", R.string.cat_messages, R.string.cat_messages_desc),
    CALLS("Calls", "Incoming, missed calls and voicemail", R.string.cat_calls, R.string.cat_calls_desc),
    ORDERS("Orders & delivery", "Order status, shipping, rides and bookings", R.string.cat_orders, R.string.cat_orders_desc),
    PAYMENTS("Payments", "Transactions, bills, bank and wallet alerts", R.string.cat_payments, R.string.cat_payments_desc),
    SECURITY("Security", "OTPs, sign-ins and account alerts", R.string.cat_security, R.string.cat_security_desc),
    REMINDERS("Reminders", "Alarms, timers, calendar and tasks", R.string.cat_reminders, R.string.cat_reminders_desc),
    MEDIA("Media & ongoing", "Playback, navigation and running services", R.string.cat_media, R.string.cat_media_desc),
    SYSTEM("Updates & system", "Downloads, sync, backups and errors", R.string.cat_system, R.string.cat_system_desc),
    OTHER("Other", "Channels that didn't match any category", R.string.cat_other, R.string.cat_other_desc),
}

@Serializable
enum class ChannelAction(val label: String, val description: String, val verb: String, val importance: Int, @StringRes val labelRes: Int, @StringRes val descRes: Int) {
    POPUP("Pop up", "Sound, and appears on screen", "Set to pop up", NotificationManager.IMPORTANCE_HIGH, R.string.action_popup, R.string.desc_popup),
    ALERT("Alert", "Sound or vibration", "Allowed", NotificationManager.IMPORTANCE_DEFAULT, R.string.action_alert, R.string.desc_alert),
    SILENT("Silent", "In the shade, without sound", "Silenced", NotificationManager.IMPORTANCE_LOW, R.string.action_silent, R.string.desc_silent),
    MINIMIZE("Minimized", "Collapsed at the bottom of the shade", "Minimized", NotificationManager.IMPORTANCE_MIN, R.string.action_minimized, R.string.desc_minimized),

    // Minimized so Android still delivers them to us; the listener removes each one on arrival and logs it.
    BLOCK("Blocked", "Never shown, kept in Logs", "Blocked", NotificationManager.IMPORTANCE_MIN, R.string.action_blocked, R.string.desc_blocked),
}

fun importanceLabel(importance: Int) = when (importance) {
    NotificationManager.IMPORTANCE_NONE -> "Blocked"
    NotificationManager.IMPORTANCE_MIN -> "Minimized"
    NotificationManager.IMPORTANCE_LOW -> "Silent"
    NotificationManager.IMPORTANCE_DEFAULT -> "Alerting"
    NotificationManager.IMPORTANCE_HIGH, NotificationManager.IMPORTANCE_MAX -> "Pop-up"
    else -> "Unspecified"
}

fun keyOf(pkg: String, channelId: String) = "$pkg|$channelId"

/** Channels + groups exactly as read from the system for one app. */
data class RawApp(
    val pkg: String,
    val uid: Int,
    val label: String,
    val system: Boolean,
    val appCategory: Int,
    val error: String?,
    val channels: List<NotificationChannel>,
    val groupNames: Map<String, String>,
)

data class ChannelInfo(
    val pkg: String,
    val uid: Int,
    val appLabel: String,
    val channel: NotificationChannel,
    val groupName: String?,
    val category: Category,
    val confidence: Int,
    val overridden: Boolean,
    /** Blocked by us: minimized in the system, removed and logged on arrival. */
    val logged: Boolean,
) {
    val key get() = keyOf(pkg, channel.id)
}

data class AppInfo(
    val pkg: String,
    val uid: Int,
    val label: String,
    val system: Boolean,
    val error: String?,
    val channels: List<ChannelInfo>,
)

@Serializable
data class ChannelChange(
    val pkg: String,
    val uid: Int,
    val channelId: String,
    val channelName: String,
    val before: Int,
    val after: Int,
    val loggedBefore: Boolean = false,
    val loggedAfter: Boolean = false,
)

@Serializable
data class Batch(val id: Long, val time: Long, val title: String, val changes: List<ChannelChange>)

@Serializable
enum class RuleAction(val label: String, val description: String, @StringRes val labelRes: Int, @StringRes val descRes: Int) {
    DISMISS("Remove", "Removed as soon as it arrives, kept in Logs", R.string.rule_action_remove, R.string.rule_desc_remove),
    SNOOZE("Snooze 1 hour", "Hidden for an hour, then shown again", R.string.rule_action_snooze, R.string.rule_desc_snooze),
}

@Serializable
data class Rule(
    val id: Long,
    val name: String,
    val keywords: List<String>,
    val pkg: String? = null,
    val action: RuleAction = RuleAction.DISMISS,
    val enabled: Boolean = true,
)

@Serializable
enum class Outcome { SHOWN, BLOCKED, RULE }

/** One notification as it arrived; kept for 7 days. */
@Serializable
data class HistoryEntry(
    val time: Long,
    val key: String,
    val pkg: String,
    val app: String,
    val channelId: String,
    val channelName: String,
    val category: Category,
    val title: String,
    val text: String,
    val outcome: Outcome,
    /** Rule name for [Outcome.RULE]. */
    val reason: String? = null,
)

@Serializable
enum class ThemeMode(val label: String, @StringRes val labelRes: Int) { SYSTEM("System", R.string.theme_system), LIGHT("Light", R.string.theme_light), DARK("Dark", R.string.theme_dark) }

@Serializable
data class StoreData(
    val overrides: Map<String, Category> = emptyMap(),
    val policies: Map<Category, ChannelAction> = emptyMap(),
    val known: Set<String> = emptySet(),
    val hints: Map<String, Set<String>> = emptyMap(),
    val history: List<Batch> = emptyList(),
    val rules: List<Rule> = emptyList(),
    /** Channel keys we block (and log). */
    val logBlocked: Set<String> = emptySet(),
    val logExcludedApps: Set<String> = emptySet(),
    /** Default action for every channel of an app, including ones it creates later. */
    val appDefaults: Map<String, ChannelAction> = emptyMap(),
    /** Quiet-hours windows, keyed by [Schedule.keyFor] — at most one per category or rule. */
    val schedules: Map<String, Schedule> = emptyMap(),
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** Wallpaper-based colours instead of the brand palette. */
    val materialYou: Boolean = false,
    /** Global pause: while true the listener leaves notifications untouched (Quick Settings tile / Settings toggle). */
    val paused: Boolean = false,
)
