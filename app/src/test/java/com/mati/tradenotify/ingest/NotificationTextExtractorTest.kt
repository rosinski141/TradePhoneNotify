package com.mati.tradenotify.ingest

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationTextExtractorTest {

    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val pkg = "org.telegram.messenger"
    private val postTime = 1_700_000_000_000L

    private fun builder() = NotificationCompat.Builder(context, "chan")
        .setSmallIcon(android.R.drawable.ic_dialog_alert)

    private fun messagingNotification(
        conversationTitle: String?,
        vararg messages: String,
    ): Notification {
        val person = Person.Builder().setName("Author").build()
        val style = NotificationCompat.MessagingStyle(person)
        if (conversationTitle != null) style.conversationTitle = conversationTitle
        messages.forEachIndexed { i, m -> style.addMessage(m, postTime + i, person) }
        return builder().setStyle(style).build()
    }

    @Test
    fun `messaging style yields conversation title and the last message`() {
        val n = messagingNotification("FX Signals Pro", "old one", "BUY EURUSD @ 1.0840")
        val signal = NotificationTextExtractor.extract(pkg, n, postTime)

        assertNotNull(signal)
        assertEquals("FX Signals Pro", signal!!.channel)
        assertEquals("BUY EURUSD @ 1.0840", signal.text)
        assertEquals(pkg, signal.packageName)
    }

    @Test
    fun `big text is used when there is no messaging style`() {
        val long = "BUY EURUSD @ 1.0840\nSL 1.0810\nTP1 1.0900\nTP2 1.0950"
        val n = builder()
            .setContentTitle("FX Signals Pro")
            .setContentText("BUY EURUSD @ 1.0840…")
            .setStyle(NotificationCompat.BigTextStyle().bigText(long))
            .build()

        val signal = NotificationTextExtractor.extract(pkg, n, postTime)
        assertNotNull(signal)
        // BigText carries the whole signal; content text is the truncated version.
        assertEquals(long, signal!!.text)
        assertEquals("FX Signals Pro", signal.channel)
    }

    @Test
    fun `plain content text is used as a last resort`() {
        val n = builder()
            .setContentTitle("FX Signals Pro")
            .setContentText("SELL GBPUSD")
            .build()

        val signal = NotificationTextExtractor.extract(pkg, n, postTime)
        assertEquals("SELL GBPUSD", signal?.text)
    }

    @Test
    fun `inbox style falls back to the last line`() {
        val n = builder()
            .setContentTitle("FX Signals Pro")
            .setStyle(
                NotificationCompat.InboxStyle()
                    .addLine("older signal")
                    .addLine("BUY XAUUSD"),
            )
            .build()

        assertEquals("BUY XAUUSD", NotificationTextExtractor.extract(pkg, n, postTime)?.text)
    }

    @Test
    fun `group summaries are ignored`() {
        val n = builder()
            .setContentTitle("Telegram")
            .setContentText("3 new messages")
            .setGroup("g")
            .setGroupSummary(true)
            .build()

        assertNull(NotificationTextExtractor.extract(pkg, n, postTime))
    }

    @Test
    fun `ongoing service notifications are ignored`() {
        val n = builder()
            .setContentTitle("Telegram")
            .setContentText("Connecting…")
            .setOngoing(true)
            .build()

        assertNull(NotificationTextExtractor.extract(pkg, n, postTime))
    }

    @Test
    fun `burst placeholders are ignored`() {
        listOf("3 new messages", "12 messages", "2 new messages from 2 chats").forEach { text ->
            val n = builder().setContentTitle("FX Signals Pro").setContentText(text).build()
            assertNull("Expected '$text' to be dropped", NotificationTextExtractor.extract(pkg, n, postTime))
        }
    }

    @Test
    fun `a message that merely starts with a number is kept`() {
        val n = builder()
            .setContentTitle("FX Signals Pro")
            .setContentText("2 lots BUY EURUSD")
            .build()

        assertEquals("2 lots BUY EURUSD", NotificationTextExtractor.extract(pkg, n, postTime)?.text)
    }

    @Test
    fun `a notification with no body is ignored`() {
        val n = builder().setContentTitle("FX Signals Pro").build()
        assertNull(NotificationTextExtractor.extract(pkg, n, postTime))
    }

    @Test
    fun `falls back to post time when none is supplied`() {
        val n = builder().setContentTitle("FX").setContentText("BUY").build()
        val signal = NotificationTextExtractor.extract(pkg, n, 0L)
        assertNotNull(signal)
        assert(signal!!.postedAt > 0)
    }
}
