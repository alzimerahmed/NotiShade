package app.notishade.data

import android.app.Notification
import android.app.NotificationChannel
import android.content.pm.ApplicationInfo

/** Heuristic channel classifier: keywords, categories seen on posted notifications, and weak app-level priors. */
object Classifier {
    private val keywords: Map<Category, List<String>> = mapOf(
        Category.PROMO to listOf(
            "promo", "promotion", "marketing", "offer", "deal", "discount", "sale", "coupon", "cashback",
            "voucher", "reward", "loyalty", "campaign", "advert", "ads", "sponsored", "exclusive", "newsletter",
            "cart", "engagement", "retention", "crm", "limited time", "price drop", "upsell",
            // Default channel ids of common marketing-push SDKs.
            "clevertap", "moengage", "moe", "webengage", "braze", "appboy", "smartech", "netcore", "iterable", "leanplum",
        ),
        Category.RECOMMENDATIONS to listOf(
            "recommend", "suggest", "for you", "trending", "popular", "discover", "personali", "you may",
            "tips", "highlight", "inspiration", "picked",
        ),
        Category.NEWS to listOf(
            "news", "breaking", "headline", "article", "story", "stories", "digest", "briefing", "blog",
            "podcast", "episode", "new video", "subscri", "live", "sport", "score", "match", "weather", "magazine",
        ),
        Category.SOCIAL to listOf(
            "like", "comment", "follow", "mention", "friend", "reaction", "social", "community", "tagged", "invite",
        ),
        Category.MESSAGES to listOf(
            "message", "messaging", "chat", "dm", "inbox", "conversation", "reply", "sms", "mms", "email", "mail", "direct",
        ),
        Category.CALLS to listOf("call", "voip", "ringing", "missed", "voicemail"),
        Category.ORDERS to listOf(
            "order", "delivery", "deliver", "shipment", "shipping", "tracking", "parcel", "courier", "ride",
            "trip", "booking", "reservation", "driver", "dispatch", "pickup",
        ),
        Category.PAYMENTS to listOf(
            "payment", "pay", "transaction", "bank", "card", "upi", "wallet", "balance", "bill", "invoice",
            "refund", "credit", "debit", "statement", "emi", "loan", "finance",
        ),
        Category.SECURITY to listOf(
            "security", "secure", "otp", "login", "sign in", "signin", "verification", "verify", "password",
            "passcode", "2fa", "auth", "fraud", "safety", "account", "privacy",
        ),
        Category.REMINDERS to listOf(
            "reminder", "remind", "alarm", "timer", "calendar", "event", "schedule", "task", "todo", "due",
            "appointment", "meeting", "agenda", "stopwatch",
        ),
        Category.MEDIA to listOf(
            "media", "playback", "player", "music", "playing", "audio", "foreground", "ongoing", "service",
            "running", "location", "navigation", "recording", "cast", "workout",
        ),
        Category.SYSTEM to listOf(
            "update", "download", "install", "system", "battery", "storage", "backup", "restore", "error",
            "status", "debug", "background", "sync", "progress", "upload", "crash", "diagnostic",
            "usb", "network", "wifi", "bluetooth", "vpn", "hotspot", "charging", "connection", "connectivity",
            "permission", "developer", "device", "screenshot", "keyboard",
        ),
    )

    private val notificationCategories = mapOf(
        Notification.CATEGORY_PROMO to Category.PROMO,
        Notification.CATEGORY_RECOMMENDATION to Category.RECOMMENDATIONS,
        Notification.CATEGORY_SOCIAL to Category.SOCIAL,
        Notification.CATEGORY_MESSAGE to Category.MESSAGES,
        Notification.CATEGORY_EMAIL to Category.MESSAGES,
        Notification.CATEGORY_CALL to Category.CALLS,
        Notification.CATEGORY_MISSED_CALL to Category.CALLS,
        Notification.CATEGORY_ALARM to Category.REMINDERS,
        Notification.CATEGORY_REMINDER to Category.REMINDERS,
        Notification.CATEGORY_EVENT to Category.REMINDERS,
        Notification.CATEGORY_STOPWATCH to Category.REMINDERS,
        Notification.CATEGORY_TRANSPORT to Category.MEDIA,
        Notification.CATEGORY_SERVICE to Category.MEDIA,
        Notification.CATEGORY_NAVIGATION to Category.MEDIA,
        Notification.CATEGORY_LOCATION_SHARING to Category.MEDIA,
        Notification.CATEGORY_WORKOUT to Category.MEDIA,
        Notification.CATEGORY_PROGRESS to Category.SYSTEM,
        Notification.CATEGORY_SYSTEM to Category.SYSTEM,
        Notification.CATEGORY_ERROR to Category.SYSTEM,
        Notification.CATEGORY_STATUS to Category.SYSTEM,
        // Android 16+ system bundles: the OS's own classifier moved a notification into one of these channels.
        "android.app.promotions" to Category.PROMO,
        "android.app.social" to Category.SOCIAL,
        "android.app.news" to Category.NEWS,
        "android.app.recs" to Category.RECOMMENDATIONS,
    )

    val bundleChannelIds = setOf("android.app.promotions", "android.app.social", "android.app.news", "android.app.recs")

    /** The app's own declared type (android:appCategory), used as a weak prior. Messengers declare "social", so it's left out. */
    private val appCategories = mapOf(
        ApplicationInfo.CATEGORY_NEWS to Category.NEWS,
        ApplicationInfo.CATEGORY_AUDIO to Category.MEDIA,
        ApplicationInfo.CATEGORY_VIDEO to Category.MEDIA,
        ApplicationInfo.CATEGORY_MAPS to Category.MEDIA,
        ApplicationInfo.CATEGORY_ACCESSIBILITY to Category.SYSTEM,
    )

    // On score ties prefer categories that are riskier to silence by mistake.
    private val tiePriority = listOf(
        Category.SECURITY, Category.CALLS, Category.MESSAGES, Category.PAYMENTS, Category.ORDERS,
        Category.REMINDERS, Category.PROMO, Category.RECOMMENDATIONS, Category.SOCIAL, Category.NEWS,
        Category.MEDIA, Category.SYSTEM,
    )

    private val camel = Regex("([a-z])([A-Z])")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")

    fun classify(
        channel: NotificationChannel,
        groupName: String?,
        hints: Set<String>,
        systemApp: Boolean = false,
        appCategory: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    ): Pair<Category, Int> = classify(
        channel.id, channel.name?.toString(), channel.description, groupName,
        conversation = channel.conversationId != null || channel.isConversation,
        hints, systemApp, appCategory,
    )

    fun classify(
        id: String,
        name: String?,
        description: String?,
        groupName: String?,
        conversation: Boolean,
        hints: Set<String>,
        systemApp: Boolean = false,
        appCategory: Int = ApplicationInfo.CATEGORY_UNDEFINED,
    ): Pair<Category, Int> {
        val text = normalize(id, name, description, groupName)
        val scores = HashMap<Category, Int>()
        for ((cat, words) in keywords) {
            val n = words.count { matches(text, it) }
            if (n > 0) scores[cat] = n
        }
        for (hint in hints) notificationCategories[hint]?.let { scores.merge(it, 3, Int::plus) }
        if (conversation) scores.merge(Category.MESSAGES, 3, Int::plus)
        // Weak priors: only decide when keywords are absent or tied.
        if (systemApp) scores.merge(Category.SYSTEM, 1, Int::plus)
        appCategories[appCategory]?.let { scores.merge(it, 1, Int::plus) }

        val best = tiePriority.filter { (scores[it] ?: 0) > 0 }.maxByOrNull { scores.getValue(it) }
            ?: return Category.OTHER to 0
        return best to minOf(95, 30 + scores.getValue(best) * 20)
    }

    private fun normalize(vararg parts: String?): String =
        " " + parts.filterNotNull().joinToString(" ") { camel.replace(it, "$1 $2") }
            .lowercase().replace(nonWord, " ").trim() + " "

    // Short keywords must match a whole word; longer ones match a word prefix ("offer" -> "offers").
    private fun matches(text: String, keyword: String) =
        if (keyword.length <= 3) text.contains(" $keyword ") else text.contains(" $keyword")
}
