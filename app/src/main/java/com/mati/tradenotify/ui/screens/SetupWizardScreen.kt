package com.mati.tradenotify.ui.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.mati.tradenotify.ingest.ListenerControl
import com.mati.tradenotify.ui.MainViewModel
import com.mati.tradenotify.ui.permissions.PermissionItem
import com.mati.tradenotify.ui.permissions.PermissionState

private data class WizardStep(
    val title: String,
    val body: String,
    val permission: PermissionItem?,
)

/**
 * First-run setup, one thing at a time.
 *
 * The Status checklist is a fine reference once you know what you're doing, but on a fresh install
 * it presents six unfamiliar toggles at once, spread across four Settings screens — and skipping a
 * required one leaves an app that looks fine and never rings. The wizard refuses to advance past a
 * required grant, so that failure can't happen quietly.
 */
@Composable
fun SetupWizardScreen(viewModel: MainViewModel, onFinish: () -> Unit) {
    val context = LocalContext.current

    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshKey++
        ListenerControl.requestRebindIfNeeded(context)
        onPauseOrDispose { }
    }
    val permissions = remember(refreshKey) { PermissionState.collect(context) }

    val steps = remember(permissions) { buildSteps(permissions) }
    var index by remember { mutableIntStateOf(0) }
    val step = steps[index.coerceIn(0, steps.lastIndex)]

    val granted = step.permission?.granted ?: true
    val required = step.permission?.required ?: false
    val canAdvance = granted || !required
    val isLast = index == steps.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(
            "Step ${index + 1} of ${steps.size}",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (index + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))
        Text(step.title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(step.body, fontSize = 15.sp, lineHeight = 22.sp)

        if (step.permission != null) {
            Spacer(Modifier.height(20.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (granted) Color(0xFF166534) else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        if (granted) "✓  Granted" else if (required) "Required — not granted yet" else "Optional — not granted",
                        color = if (granted) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (!granted && step.permission.fixIntent != null) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { safeStart(context, step.permission.fixIntent) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Open settings") }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Come back here afterwards — this page rechecks itself automatically.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (index > 0) {
                OutlinedButton(onClick = { index-- }) { Text("Back") }
            }
            Button(
                onClick = {
                    if (isLast) {
                        viewModel.setSetupComplete(true)
                        onFinish()
                    } else {
                        index++
                    }
                },
                enabled = canAdvance,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isLast) "Finish" else if (granted || step.permission == null) "Next" else "Skip")
            }
        }

        if (!canAdvance) {
            Spacer(Modifier.height(8.dp))
            Text(
                "This one is required — the app cannot detect a single signal without it.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = {
                viewModel.setSetupComplete(true)
                onFinish()
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Skip setup for now") }
    }
}

private fun buildSteps(permissions: List<PermissionItem>): List<WizardStep> {
    val byKey = permissions.associateBy { it.key }
    val steps = mutableListOf<WizardStep>()

    steps += WizardStep(
        title = "Let's make sure you never miss a signal",
        body = "TradeNotify watches the notifications Telegram already shows you, and rings a " +
            "full alarm when a message matches one of your rules.\n\n" +
            "It needs a few permissions first. This takes about a minute.",
        permission = null,
    )

    listOf("listener", "post", "channel", "fsi", "battery", "dnd").forEach { key ->
        val item = byKey[key] ?: return@forEach
        steps += WizardStep(title = item.title, body = item.why, permission = item)
    }

    steps += WizardStep(
        title = "Two things that silence it",
        body = "Both fail quietly, so they're worth knowing now.\n\n" +
            "1. A muted channel. TradeNotify only sees messages Telegram itself notifies about, " +
            "so unmute any channel you want alarms from.\n\n" +
            "2. Battery management. " + oemHint() + "\n\n" +
            "The Status screen warns you if a channel goes quiet for a day.",
        permission = null,
    )

    steps += WizardStep(
        title = "Last step: add a rule",
        body = "Nothing alarms until you add one.\n\n" +
            "The easy way: let Telegram deliver a message from the channel you care about, then " +
            "open Rules and tap \"Pick from channels already seen\" — the exact name gets filled " +
            "in for you. Typing it by hand is the most common way to end up with a rule that " +
            "never matches.\n\n" +
            "Then press Test alarm on the Status screen to hear what a real signal sounds like.",
        permission = null,
    )

    return steps
}

private fun oemHint(): String = when (Build.MANUFACTURER.lowercase()) {
    "honor", "huawei" ->
        "On this phone, open Settings → Battery → App launch, find TradeNotify, turn off " +
            "\"Manage automatically\" and enable Auto-launch, Secondary launch and Run in background."

    "xiaomi", "redmi", "poco" ->
        "On this phone, enable Autostart for TradeNotify in Security → Permissions → Autostart."

    "samsung" ->
        "On this phone, keep TradeNotify out of \"Sleeping apps\" in Settings → Battery → " +
            "Background usage limits."

    else ->
        "If your phone has an autostart manager, allow TradeNotify to run in the background there."
}
