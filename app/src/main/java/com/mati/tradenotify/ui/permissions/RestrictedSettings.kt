package com.mati.tradenotify.ui.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Android 13+ "Restricted Settings".
 *
 * When an app is installed from outside an app store, Android greys out its notification-listener
 * and accessibility toggles and says "Controlled by restricted setting" with no route forward. The
 * unlock lives behind the overflow menu on the app's own info page — **App info → ⋮ → Allow
 * restricted settings** — which is undiscoverable unless someone tells you.
 *
 * Note that `adb install` does not trip this, but opening a downloaded APK does, so it never shows
 * up in development and always shows up for real users.
 */
object RestrictedSettings {

    private const val PLAY_STORE = "com.android.vending"

    /** True when this install is likely subject to the restriction. */
    fun mayApply(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && isSideloaded(context)

    /** The app's own info page — where the ⋮ menu with "Allow restricted settings" lives. */
    fun appInfo(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        )

    private fun isSideloaded(context: Context): Boolean {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                pm.getInstallSourceInfo(context.packageName).installingPackageName
            }.getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching { pm.getInstallerPackageName(context.packageName) }.getOrNull()
        }
        // null covers adb and plain file-manager installs; anything that isn't Play may be
        // restricted, and over-showing the hint is harmless while under-showing it is a dead end.
        return installer != PLAY_STORE
    }
}
