package xyz.martelle.clapet.notifications

import androidx.compose.runtime.Immutable

/** A notification as Android posts it, reduced to what Clapet decides on. */
data class PostedNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    /** The user can swipe it away (not a foreground service, download in progress…). */
    val clearable: Boolean,
    val ongoing: Boolean,
    /** The bundle header of a group: its children carry the content. */
    val groupSummary: Boolean,
    /** A player's notification: the cover player already shows it. */
    val media: Boolean,
    /** Offers a free-text reply. */
    val canReply: Boolean,
)

/** One notification shown under its app. */
@Immutable
data class AppNotification(
    val key: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val canReply: Boolean,
)

/** An app's notifications, newest first. Never empty. */
@Immutable
data class AppNotifications(val packageName: String, val items: List<AppNotification>) {
    val count: Int get() = items.size
    val latest: AppNotification get() = items.first()
}

object NotificationRules {

    /** Everything the user could clear, silent ones included; persistent and player ones are not news. */
    fun isShown(notification: PostedNotification): Boolean = with(notification) {
        clearable && !ongoing && !groupSummary && !media && (title.isNotBlank() || text.isNotBlank())
    }

    /** Shown notifications, by app, newest first. */
    fun byPackage(posted: List<PostedNotification>): Map<String, AppNotifications> =
        posted
            .filter(::isShown)
            .groupBy { it.packageName }
            .mapValues { (packageName, list) ->
                AppNotifications(
                    packageName,
                    list.sortedByDescending { it.postedAt }.map {
                        AppNotification(it.key, it.title, it.text, it.postedAt, it.canReply)
                    },
                )
            }

    /** Who and what, when both are known (« Léa », « On se retrouve à 19 h ? »). */
    fun previewParts(notification: AppNotification): List<String> =
        listOf(notification.title, notification.text).filter { it.isNotBlank() }
}
