package com.mati.tradenotify.ui.permissions

import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.mati.tradenotify.util.TAG

/**
 * Getting the user to the one screen that actually matters on their phone.
 *
 * Android's own battery allowlist is a single documented dialog, but it is not the whole story:
 * an app can be allowlisted and still be put in the *Restricted* standby bucket, and most Chinese
 * OEMs bolt on a separate "auto launch" manager that overrides both. Those OEM screens have no
 * public intent, so the only way in is to try their known component names and fall back.
 */
object OemBatterySettings {

    /**
     * True when the system has put us in the Restricted bucket — background work is curtailed
     * regardless of the battery allowlist, which is exactly how the listener dies quietly.
     *
     * The no-argument `getAppStandbyBucket()` reports the calling app's own bucket and needs no
     * permission.
     */
    fun isRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val usage = context.getSystemService(UsageStatsManager::class.java) ?: return false
        return runCatching {
            usage.appStandbyBucket >= UsageStatsManager.STANDBY_BUCKET_RESTRICTED
        }.getOrDefault(false)
    }

    /** The standard, documented one-tap dialog. Always worth trying first. */
    fun ignoreBatteryOptimizations(context: Context): Intent =
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )

    /** Always resolves; the universal fallback when nothing more specific works. */
    fun appDetails(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

    /** Whether this phone has a vendor auto-launch manager worth offering a shortcut to. */
    fun hasOemLauncherManager(): Boolean = oemIntents().isNotEmpty()

    /**
     * Opens the vendor's auto-launch / protected-apps screen.
     *
     * Verified component names are tried first, then the vendor's declared action as a fallback.
     * The action is more durable across firmware, but on HONOR more than one activity claims it,
     * so leading with it drops the user into an app-chooser dialog instead of the screen they
     * asked for. Each candidate is simply attempted in turn — a missing one throws and we move on
     * — ending at the app's own details page, which always exists.
     */
    fun openOemLauncherManager(context: Context) {
        for (intent in oemIntents()) {
            val launched = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (launched) {
                Log.i(TAG, "Opened OEM launcher manager via $intent")
                return
            }
        }
        Log.i(TAG, "No OEM launcher manager matched; falling back to app details")
        context.startActivitySafely(appDetails(context))
    }

    private fun oemIntents(): List<Intent> =
        oemComponents().map { Intent().setComponent(it) } + oemActions().map { Intent(it) }

    /**
     * Vendor intent actions, verified present on the device where possible.
     *
     * `hihonor.intent.action.HSM_STARTUPAPP_MANAGER` was read off a HONOR Magic7 Pro running
     * MagicOS on Android 16, where the target activity declares it with category DEFAULT.
     */
    private fun oemActions(): List<String> =
        when (Build.MANUFACTURER.lowercase()) {
            "honor" -> listOf(
                "hihonor.intent.action.HSM_STARTUPAPP_MANAGER",
                "hihonor.intent.action.HSM_BOOTAPP_MANAGER",
                "huawei.intent.action.HSM_STARTUPAPP_MANAGER",
            )

            "huawei" -> listOf(
                "huawei.intent.action.HSM_STARTUPAPP_MANAGER",
                "huawei.intent.action.HSM_BOOTAPP_MANAGER",
            )

            else -> emptyList()
        }

    private fun oemComponents(): List<ComponentName> =
        when (Build.MANUFACTURER.lowercase()) {
            "honor" -> listOf(
                // Read off a HONOR Magic7 Pro (MagicOS / Android 16). HONOR split from Huawei but
                // renamed the classes too, so the package AND the class use the hihonor namespace.
                ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
                ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.appcontrol.activity.StartupAppControlActivity",
                ),
                // Older HONOR firmware still carried Huawei's class names.
                ComponentName(
                    "com.hihonor.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
            )

            "huawei" -> listOf(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity",
                ),
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
                ),
            )

            "xiaomi", "redmi", "poco" -> listOf(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            )

            "oppo", "realme" -> listOf(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                ),
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity",
                ),
            )

            "vivo" -> listOf(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                ),
            )

            "oneplus" -> listOf(
                ComponentName(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                ),
            )

            else -> emptyList()
        }
}
