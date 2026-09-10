package com.mati.tradenotify.ingest

import android.app.Notification
import android.os.Bundle
import androidx.core.app.NotificationCompat

/**
 * Turns a Telegram notification into a [Signal], or null if it isn't a real message.
 *
 * Written as a plain function over [Notification] rather than over `StatusBarNotification` so it
 * can be driven from unit tests with hand-built notifications.
 */
object NotificationTextExtractor {

    /**
     * Telegram collapses bursts into "3 new messages" and similar. Those carry no message body,
     * so there is nothing to match against and they would only pollute the log.
     */
    private val PLACEHOLDER = Regex(
        """^\d+\s+(new\s+)?(message|messages|chats?)\b.*""",
        RegexOption.IGNORE_CASE,
    )

    fun extract(packageName: String, notification: Notification, postTime: Long): Signal? {
        if (!isMessage(notification)) return null

        val extras = notification.extras ?: return null
        val style = messagingStyle(notification)

        val channel = channelOf(style, extras)?.takeIf { it.isNotBlank() } ?: return null
        val text = textOf(style, extras)?.takeIf { it.isNotBlank() } ?: return null
        if (PLACEHOLDER.matches(text.trim())) return null

        return Signal(
            packageName = packageName,
            channel = channel.trim(),
            text = text.trim(),
            postedAt = if (postTime > 0) postTime else System.currentTimeMillis(),
        )
    }

    /** Filters out group summaries and Telegram's own "connecting…" / sync notifications. */
    private fun isMessage(notification: Notification): Boolean {
        val flags = notification.flags
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (flags and Notification.FLAG_ONGOING_EVENT != 0) return false
        return when (notification.category) {
            Notification.CATEGORY_SERVICE,
            Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_TRANSPORT,
            -> false

            else -> true
        }
    }

    private fun messagingStyle(notification: Notification): NotificationCompat.MessagingStyle? =
        runCatching {
            NotificationCompat.MessagingStyle
                .extractMessagingStyleFromNotification(notification)
        }.getOrNull()

    private fun channelOf(
        style: NotificationCompat.MessagingStyle?,
        extras: Bundle,
    ): String? {
        // MessagingStyle's conversation title is the most reliable channel name; it is set for
        // channels and groups, and null for one-to-one chats where the title is the person.
        style?.conversationTitle?.toString()?.takeIf { it.isNotBlank() }?.let { return it }

        extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            ?.takeIf { it.isNotBlank() }?.let { return it }

        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
    }

    /** First non-blank wins; earlier sources carry more of the original message. */
    private fun textOf(
        style: NotificationCompat.MessagingStyle?,
        extras: Bundle,
    ): String? {
        style?.messages?.lastOrNull()?.text?.toString()
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }?.let { return it }

        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?.takeIf { it.isNotBlank() }?.let { return it }

        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        return lines?.lastOrNull()?.toString()
    }
}
