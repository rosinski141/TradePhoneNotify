package com.mati.tradenotify.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Turns "the user tapped Fix on this row" into the right action.
 *
 * Runtime permissions and Settings screens need opposite handling, and getting it wrong is
 * user-visible: Android silently auto-denies a permission refused twice, and some OEM builds
 * (MagicOS among them) show a bare "denied access to this permission" toast when that happens.
 * So the system dialog is only ever launched when it can actually appear; once it can't, the user
 * goes straight to Settings.
 *
 * @param askedBefore whether the runtime dialog has already been shown at least once.
 * @param onAsked called when the dialog is actually launched, so the caller can persist that.
 */
@Composable
fun rememberPermissionAction(
    askedBefore: Boolean,
    onAsked: () -> Unit,
): (PermissionItem) -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val pending = remember { mutableStateOf<PermissionItem?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val item = pending.value
        pending.value = null
        // Refused and Android will not ask again — Settings is the only remaining route.
        if (!granted && item?.fixIntent != null && activity != null &&
            item.runtimePermission != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, item.runtimePermission)
        ) {
            context.startActivitySafely(item.fixIntent)
        }
    }

    return { item ->
        val permission = item.runtimePermission
        val alreadyGranted = permission != null &&
            ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED

        val canShowDialog = permission != null && activity != null && !alreadyGranted &&
            (!askedBefore || ActivityCompat.shouldShowRequestPermissionRationale(activity, permission))

        if (canShowDialog) {
            pending.value = item
            onAsked()
            launcher.launch(permission)
        } else {
            item.fixIntent?.let { context.startActivitySafely(it) }
        }
    }
}

/** Some OEMs ship without a given Settings screen; a missing one must not crash the app. */
fun Context.startActivitySafely(intent: Intent) {
    runCatching { startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
