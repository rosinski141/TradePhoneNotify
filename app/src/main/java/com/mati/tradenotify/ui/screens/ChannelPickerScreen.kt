package com.mati.tradenotify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mati.tradenotify.data.TelegramPackages
import com.mati.tradenotify.ui.MainViewModel
import com.mati.tradenotify.util.formatRelative

/**
 * Channels Telegram has actually notified about, newest first.
 *
 * Typing a channel name by hand is the easiest way to build a rule that silently never matches —
 * a stray space or a renamed channel is invisible. Picking from what was really observed removes
 * that whole class of mistake.
 */
@Composable
fun ChannelPickerScreen(
    viewModel: MainViewModel,
    onPick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val channels by viewModel.channels.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Spacer(Modifier.height(12.dp))
                Text("Seen channels", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Everything a watched Telegram app has notified about. Tap one to build a " +
                        "rule with its exact name already filled in.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }

        if (channels.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Nothing seen yet", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Make sure notification access is granted and the channel is not " +
                                "muted in Telegram, then wait for the next post.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        items(channels, key = { it.channel + it.packageName }) { summary ->
            Card(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPick(summary.channel) },
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(summary.channel, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${summary.messageCount} message${if (summary.messageCount == 1) "" else "s"}" +
                                " · ${TelegramPackages.KNOWN[summary.packageName] ?: summary.packageName}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        formatRelative(summary.lastSeen),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
