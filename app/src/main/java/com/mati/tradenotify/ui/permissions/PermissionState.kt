package com.mati.tradenotify.ui.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mati.tradenotify.alarm.AlarmNotifications

/** One row of the home-screen readiness checklist. */
data class PermissionItem(
    val key: String,
    val title: String,
    val why: String,
    val granted: Boolean,
    val required: Boolean,
    /** Intent that takes the user to the screen where they can grant it, if there is one. */
    val fixIntent: Intent?,
    /**
     * Set for grants that are a runtime permission rather than a Settings screen, so the UI can
     * show the system dialog first and only fall back to Settings once Android stops asking.
     */
    val runtimePermission: String? = null,
)

/**
 * The app is inert without a handful of grants that live in different corners of Settings, and
 * silently so. This gathers them into one checklist that is re-read on every resume.
 */
object PermissionState {

    fun collect(context: Context): List<PermissionItem> = buildList {
        add(notificationAccess(context))
        add(postNotifications(context))
        add(fullScreenIntent(context))
        add(alarmChannelEnabled(context))
        add(batteryOptimization(context))
        add(dndBypass(context))
    }

    fun allRequiredGranted(items: List<PermissionItem>): Boolean =
        items.filter { it.required }.all { it.granted }

    private fun notificationAccess(context: Context) = PermissionItem(
        key = "listener",
        title = "Notification access",
        why = "Lets TradeNotify read Telegram's notifications. Nothing works without this.",
        granted = NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName),
        required = true,
        fixIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
    )

    private fun postNotifications(context: Context): PermissionItem {
        // POST_NOTIFICATIONS only exists from API 33. Below that, checkSelfPermission reports
        // DENIED for it, which would pin this row to a permanent ✗ on Android 10-12 — so ask the
        // question that is meaningful on every version instead.
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
        return PermissionItem(
            key = "post",
            title = "Show notifications",
            why = "The alarm is delivered as a full-screen notification.",
            granted = granted,
            required = true,
            fixIntent = appNotificationSettings(context),
            runtimePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else {
                null
            },
        )
    }

    /**
     * Android 14 restricts full-screen intents to calling and alarm apps, and the Play Store
     * revokes it at install for everything else. Without it the alarm still rings, but it will
     * not take over the lock screen.
     */
    private fun fullScreenIntent(context: Context): PermissionItem {
        val nm = context.getSystemService(NotificationManager::class.java)
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            nm?.canUseFullScreenIntent() ?: false
        } else {
            true
        }
        val fix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}"),
            )
        } else {
            null
        }
        return PermissionItem(
            key = "fsi",
            title = "Full-screen alarms",
            why = "Lets the alarm wake the screen and show over the lock screen. " +
                "Without it you still get sound and a banner.",
            granted = granted,
            required = false,
            fixIntent = fix,
        )
    }

    private fun alarmChannelEnabled(context: Context): PermissionItem {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = runCatching {
            nm?.getNotificationChannel(AlarmNotifications.CHANNEL_ALARM)
        }.getOrNull()
        val enabled = channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
        return PermissionItem(
            key = "channel",
            title = "Alarm channel enabled",
            why = "The 'Signal alarms' notification category must not be turned off.",
            granted = enabled,
            required = true,
            fixIntent = appNotificationSettings(context),
        )
    }

    private fun batteryOptimization(context: Context): PermissionItem {
        val pm = context.getSystemService(PowerManager::class.java)
        val ignoring = pm?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        // Being on the allowlist and being Restricted are independent: an app can be both, and
        // Restricted still curtails background work. Only clear when neither applies.
        val restricted = OemBatterySettings.isRestricted(context)

        return PermissionItem(
            key = "battery",
            title = if (restricted) "Battery use is Restricted" else "Unrestricted battery",
            why = if (restricted) {
                "Android has put TradeNotify in the Restricted bucket, which stops it running in " +
                    "the background — alarms will be missed. Open App info → Battery and choose " +
                    "Unrestricted."
            } else {
                "Stops the system from unbinding the listener while the phone is idle."
            },
            granted = ignoring && !restricted,
            required = false,
            // The allowlist dialog cannot clear a Restricted bucket, so when that's the problem
            // send the user to App info, where the Unrestricted option actually lives.
            fixIntent = if (restricted) {
                OemBatterySettings.appDetails(context)
            } else {
                OemBatterySettings.ignoreBatteryOptimizations(context)
            },
        )
    }

    private fun dndBypass(context: Context): PermissionItem {
        val nm = context.getSystemService(NotificationManager::class.java)
        val granted = nm?.isNotificationPolicyAccessGranted ?: false
        return PermissionItem(
            key = "dnd",
            title = "Ring through Do Not Disturb",
            why = "Optional. Lets signal alarms sound while DND is on.",
            granted = granted,
            required = false,
            fixIntent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS),
        )
    }

    private fun appNotificationSettings(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
}
