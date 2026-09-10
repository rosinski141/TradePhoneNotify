package com.mati.tradenotify.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mati.tradenotify.R
import com.mati.tradenotify.ui.MainActivity

object AlarmNotifications {

    const val CHANNEL_ALARM = "signal_alarm"
    const val CHANNEL_STATUS = "status"

    const val ALARM_NOTIFICATION_ID = 1001
    const val STATUS_NOTIFICATION_ID = 1002

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val alarm = NotificationChannel(
            CHANNEL_ALARM,
            context.getString(R.string.channel_alarm_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_alarm_desc)
            // The service owns the audio and the vibration so it can loop them and stop them on
            // dismiss; the channel itself must stay silent or the two would fight.
            setSound(null, null)
            enableVibration(false)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }

        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.channel_status_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_status_desc)
        }

        nm.createNotificationChannel(alarm)
        nm.createNotificationChannel(status)
    }

    /** The ringing notification: full-screen intent plus dismiss/snooze actions. */
    fun buildAlarmNotification(context: Context, payload: AlarmPayload): Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            0,
            payload.writeTo(
                Intent(context, AlarmActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
                ),
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val dismiss = PendingIntent.getService(
            context,
            1,
            Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_DISMISS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val snooze = PendingIntent.getService(
            context,
            2,
            payload.writeTo(
                Intent(context, AlarmService::class.java).setAction(AlarmService.ACTION_SNOOZE),
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(context.getString(R.string.alarm_title, payload.channel))
            .setContentText(payload.text)
            .setStyle(Notification.BigTextStyle().bigText(payload.text))
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.action_snooze),
                    snooze,
                ).build(),
            )
            .addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.action_dismiss),
                    dismiss,
                ).build(),
            )
            .build()
    }

    /** Low-priority notice used for watchdog warnings and quiet-hours suppressions. */
    fun postStatus(context: Context, title: String, body: String, id: Int = STATUS_NOTIFICATION_ID) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id, notification)
    }
}
