package com.mati.tradenotify.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mati.tradenotify.data.db.EventOutcome
import com.mati.tradenotify.ingest.ListenerControl
import com.mati.tradenotify.match.ChannelHealth
import com.mati.tradenotify.ui.MainViewModel
import com.mati.tradenotify.ui.permissions.PermissionItem
import com.mati.tradenotify.ui.permissions.PermissionState
import com.mati.tradenotify.util.formatRelative

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onOpenRules: () -> Unit,
    onOpenChannels: () -> Unit,
) {
    val context = LocalContext.current
    val connected by viewModel.listenerConnected.collectAsStateWithLifecycle()
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()

    val quietRules = remember(rules, channels) { ChannelHealth.findQuiet(rules, channels) }

    // Permissions are changed in system Settings, outside this process, so re-read them every
    // time the user comes back rather than caching.
    var refreshKey by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refreshKey++
        // Coming back from the Settings grant screen is exactly when a rebind is worth asking for.
        ListenerControl.requestRebindIfNeeded(context)
        onPauseOrDispose { }
    }
    val permissions = remember(refreshKey) { PermissionState.collect(context) }
    val ready = PermissionState.allRequiredGranted(permissions)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        item {
            StatusCard(
                ready = ready && connected,
                readyButDisconnected = ready && !connected,
                enabledRules = rules.count { it.enabled },
            )
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    permissions.forEach { item ->
                        ChecklistRow(item) { intent -> safeStart(context, intent) }
                    }
                }
            }
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Try it", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Fires a real alarm through the same path a live signal takes — " +
                            "sound, screen wake and the dismiss screen.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.testAlarm() }) { Text("Test alarm") }
                        OutlinedButton(onClick = onOpenChannels) { Text("Seen channels") }
                    }
                }
            }
        }

        if (quietRules.isNotEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Nothing heard recently",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF78350F),
                        )
                        Spacer(Modifier.height(6.dp))
                        quietRules.forEach { (rule, lastSeen) ->
                            Text(
                                "• ${rule.name} — " + if (lastSeen == null) {
                                    "no message ever seen from a matching channel"
                                } else {
                                    "last message ${formatRelative(lastSeen)}"
                                },
                                fontSize = 13.sp,
                                color = Color(0xFF78350F),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Most often this means the channel is muted in Telegram, or the " +
                                "rule's channel name doesn't match what Telegram actually sends.",
                            fontSize = 12.sp,
                            color = Color(0xFF92400E),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = onOpenChannels) { Text("Compare with seen channels") }
                    }
                }
            }
        }

        if (rules.isEmpty()) {
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No rules yet", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Nothing will alarm until you add a rule. Open a channel Telegram " +
                                "has already notified you about, or add one by hand.",
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onOpenRules) { Text("Add a rule") }
                    }
                }
            }
        }

        item {
            Text(
                "Recent activity",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (events.isEmpty()) {
            item {
                Text(
                    "Nothing seen yet. Once Telegram posts a notification from a watched app it " +
                        "will appear here, matched or not.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(events.take(8), key = { it.id }) { event ->
                Column(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(outcomeGlyph(event.outcome), color = outcomeColor(event.outcome))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            event.channel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatRelative(event.postedAt),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        event.text.take(120),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun StatusCard(ready: Boolean, readyButDisconnected: Boolean, enabledRules: Int) {
    val (title, body, color) = when {
        ready -> Triple(
            "Listening",
            "$enabledRules rule${if (enabledRules == 1) "" else "s"} active.",
            Color(0xFF166534),
        )

        readyButDisconnected -> Triple(
            "Reconnecting",
            "Permissions are granted but the listener isn't bound yet. This usually clears " +
                "within a minute; if it doesn't, toggle notification access off and on.",
            Color(0xFF92400E),
        )

        else -> Triple(
            "Not listening",
            "Finish the setup steps below — no signal can be detected until they're done.",
            Color(0xFF991B1B),
        )
    }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(body, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun ChecklistRow(item: PermissionItem, onFix: (Intent) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (item.granted) "✓" else if (item.required) "✕" else "!",
                color = when {
                    item.granted -> Color(0xFF16A34A)
                    item.required -> MaterialTheme.colorScheme.error
                    else -> Color(0xFFCA8A04)
                },
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                item.title + if (!item.required) " (optional)" else "",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            if (!item.granted && item.fixIntent != null) {
                TextButton(onClick = { onFix(item.fixIntent) }) { Text("Fix") }
            }
        }
        if (!item.granted) {
            Text(
                item.why,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 22.dp),
            )
        }
    }
}

internal fun outcomeGlyph(outcome: EventOutcome) = when (outcome) {
    EventOutcome.ALARMED -> "▲"
    EventOutcome.COOLDOWN -> "◷"
    EventOutcome.QUIET_HOURS -> "☾"
    EventOutcome.NO_MATCH -> "·"
}

@Composable
internal fun outcomeColor(outcome: EventOutcome) = when (outcome) {
    EventOutcome.ALARMED -> MaterialTheme.colorScheme.error
    EventOutcome.COOLDOWN -> Color(0xFFCA8A04)
    EventOutcome.QUIET_HOURS -> Color(0xFF6366F1)
    EventOutcome.NO_MATCH -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** Some OEMs ship without these Settings screens; a missing one must not crash the app. */
internal fun safeStart(context: Context, intent: Intent) {
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
