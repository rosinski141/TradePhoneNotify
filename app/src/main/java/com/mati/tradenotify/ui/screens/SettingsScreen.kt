package com.mati.tradenotify.ui.screens

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mati.tradenotify.BuildConfig
import com.mati.tradenotify.data.TelegramPackages
import com.mati.tradenotify.match.QuietHours
import com.mati.tradenotify.ui.ImportResult
import com.mati.tradenotify.ui.MainViewModel
import com.mati.tradenotify.ui.permissions.OemBatterySettings

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val installed = remember {
        TelegramPackages.KNOWN.keys.filter { pkg ->
            runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
        }.toSet()
    }

    val update by viewModel.update.collectAsStateWithLifecycle()
    val importResult by viewModel.importResult.collectAsStateWithLifecycle()
    var backupStatus by remember { mutableStateOf("") }

    LaunchedEffect(importResult) {
        val result = importResult ?: return@LaunchedEffect
        backupStatus = when (result) {
            is ImportResult.Success ->
                "Imported ${result.count} rule${if (result.count == 1) "" else "s"}."

            ImportResult.NotABackup -> "That file isn't a TradeNotify rules backup."
            ImportResult.Failed -> "Could not read that file."
        }
        viewModel.clearImportResult()
    }

    // Storage Access Framework, so no storage permission is needed and the user picks the location.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.exportRulesTo(uri) { ok ->
                backupStatus = if (ok) "Rules exported." else "Export failed."
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) viewModel.importRulesFrom(uri) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Spacer(Modifier.height(12.dp))
                Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }

        item {
            SettingsCard("Watched apps") {
                Text(
                    "Which messaging apps to read notifications from. Installed ones are listed " +
                        "first; the rest are harmless to leave on.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TelegramPackages.KNOWN.entries
                    .sortedByDescending { it.key in installed }
                    .forEach { (pkg, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = pkg in settings.watchedPackages,
                                onCheckedChange = { checked ->
                                    val next = settings.watchedPackages.toMutableSet()
                                    if (checked) next += pkg else next -= pkg
                                    viewModel.setWatchedPackages(next)
                                },
                            )
                            Text(
                                label + if (pkg in installed) "  (installed)" else "",
                                fontSize = 14.sp,
                            )
                        }
                    }
            }
        }

        item {
            SettingsCard("Alarm") {
                LabeledSlider(
                    label = "Snooze",
                    value = settings.snoozeMinutes.toFloat(),
                    range = 1f..30f,
                    display = "${settings.snoozeMinutes} min",
                    onChange = { viewModel.setSnoozeMinutes(it.toInt()) },
                )
                LabeledSlider(
                    label = "Stop ringing after",
                    value = settings.autoStopMinutes.toFloat(),
                    range = 1f..30f,
                    display = "${settings.autoStopMinutes} min",
                    onChange = { viewModel.setAutoStopMinutes(it.toInt()) },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = settings.forceAlarmVolume,
                        onCheckedChange = { viewModel.setForceAlarmVolume(it) },
                    )
                    Text("  Raise alarm volume while ringing", fontSize = 14.sp)
                }
                if (settings.forceAlarmVolume) {
                    LabeledSlider(
                        label = "Volume",
                        value = settings.alarmVolumePercent.toFloat(),
                        range = 10f..100f,
                        display = "${settings.alarmVolumePercent}%",
                        onChange = { viewModel.setAlarmVolumePercent(it.toInt()) },
                    )
                    Text(
                        "The previous alarm volume is restored when the alarm stops.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SettingsCard("Quiet hours") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = settings.quietHoursEnabled,
                        onCheckedChange = {
                            viewModel.setQuietHours(
                                it,
                                settings.quietStartMinutes,
                                settings.quietEndMinutes,
                            )
                        },
                    )
                    Text("  Stay silent during a window", fontSize = 14.sp)
                }
                if (settings.quietHoursEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TimeField(
                            label = "From",
                            minutes = settings.quietStartMinutes,
                            onChange = {
                                viewModel.setQuietHours(true, it, settings.quietEndMinutes)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        TimeField(
                            label = "To",
                            minutes = settings.quietEndMinutes,
                            onChange = {
                                viewModel.setQuietHours(true, settings.quietStartMinutes, it)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Signals arriving in this window are logged and shown as a silent " +
                            "notification instead of ringing.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SettingsCard("Backup your rules") {
                Text(
                    "Rules are the only thing here you can't get back automatically. Export them " +
                        "before switching phones, or to share a set-up with someone else.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch("tradenotify-rules.json") }) {
                        Text("Export")
                    }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                        Text("Import")
                    }
                }
                if (backupStatus.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(backupStatus, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item {
            SettingsCard("Version") {
                Text("TradeNotify ${BuildConfig.VERSION_NAME}", fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                val available = update
                if (available == null) {
                    Text(
                        "You're on the latest version.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { viewModel.checkForUpdate() }) {
                        Text("Check for updates")
                    }
                } else {
                    Text(
                        "Version ${available.versionName} is available.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            safeStart(
                                context,
                                Intent(Intent.ACTION_VIEW, (available.apkUrl ?: available.releaseUrl).toUri()),
                            )
                        },
                    ) { Text("Download") }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Downloads the new APK. Open it from your notifications to install over " +
                            "the top — your rules and history are kept.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            SettingsCard("If alarms stop arriving") {
                Text(
                    "Two things silence this app without any visible error:\n\n" +
                        "• The channel is muted in Telegram. TradeNotify only sees messages " +
                        "Telegram itself notifies about.\n\n" +
                        "• The phone's battery manager unbound the listener.",
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                Text(oemBatteryAdvice(), fontSize = 13.sp)
                if (OemBatterySettings.hasOemLauncherManager()) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { OemBatterySettings.openOemLauncherManager(context) }) {
                        Text("Open app launch settings")
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Row {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text(display, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

@Composable
private fun TimeField(
    label: String,
    minutes: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = QuietHours.format(minutes),
        onValueChange = { raw ->
            parseTime(raw)?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

/**
 * Battery-manager instructions for the phone this is actually running on.
 *
 * Generic "check your battery settings" advice is useless here, because the setting that matters
 * is buried in a different place on every skin — and on Honor/Huawei the system-level
 * "Unrestricted battery" toggle is not enough on its own: App launch has to be taken off automatic
 * management too, or the listener gets unbound whenever the screen is off.
 */
private fun oemBatteryAdvice(): String = when (Build.MANUFACTURER.lowercase()) {
    "honor", "huawei" -> "On this phone (${Build.MANUFACTURER} ${Build.MODEL}), also open " +
        "Settings → Battery → App launch, find TradeNotify, turn OFF \"Manage automatically\" " +
        "and switch on Auto-launch, Secondary launch and Run in background. This is the setting " +
        "that keeps the listener alive when the screen is off."

    "xiaomi", "redmi", "poco" -> "On this phone (${Build.MANUFACTURER} ${Build.MODEL}), also " +
        "enable Autostart for TradeNotify in Security → Permissions → Autostart, and set " +
        "battery saver to No restrictions."

    "samsung" -> "On this phone (${Build.MANUFACTURER} ${Build.MODEL}), also open Settings → " +
        "Battery → Background usage limits and make sure TradeNotify is not in \"Sleeping apps\" " +
        "or \"Deep sleeping apps\"."

    "oppo", "realme", "oneplus", "vivo" -> "On this phone (${Build.MANUFACTURER} ${Build.MODEL}), " +
        "also allow Auto-startup for TradeNotify and set battery usage to Allow background " +
        "activity."

    else -> "If your phone has an autostart or app-launch manager, allow TradeNotify to run in " +
        "the background there as well as setting battery usage to Unrestricted."
}

/** Accepts `HH:MM`; anything else leaves the value untouched. */
private fun parseTime(raw: String): Int? {
    val parts = raw.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}
