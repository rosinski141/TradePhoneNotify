package com.mati.tradenotify.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mati.tradenotify.data.db.EventOutcome
import com.mati.tradenotify.ui.MainViewModel
import com.mati.tradenotify.util.formatDateTime

/**
 * Every message seen, matched or not — the tool for answering "why didn't my rule fire?".
 */
@Composable
fun HistoryScreen(viewModel: MainViewModel) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var onlyAlarms by remember { mutableStateOf(false) }

    val shown = remember(events, onlyAlarms) {
        if (onlyAlarms) events.filter { it.outcome == EventOutcome.ALARMED } else events
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column {
                Spacer(Modifier.height(12.dp))
                Text("History", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = onlyAlarms,
                        onClick = { onlyAlarms = !onlyAlarms },
                        label = { Text("Alarms only") },
                    )
                    OutlinedButton(onClick = { viewModel.clearHistory() }) { Text("Clear") }
                }
            }
        }

        if (shown.isEmpty()) {
            item {
                Text(
                    "Nothing recorded yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
        }

        items(shown, key = { it.id }) { event ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(outcomeGlyph(event.outcome), color = outcomeColor(event.outcome))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            event.channel,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatDateTime(event.postedAt),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(event.text, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        outcomeDescription(event.outcome, event.matchedRuleName),
                        fontSize = 12.sp,
                        color = outcomeColor(event.outcome),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun outcomeDescription(outcome: EventOutcome, ruleName: String?): String = when (outcome) {
    EventOutcome.ALARMED -> "Alarmed — rule \"$ruleName\""
    EventOutcome.COOLDOWN -> "Matched \"$ruleName\" but it had just fired (cooldown)"
    EventOutcome.QUIET_HOURS -> "Matched \"$ruleName\" but quiet hours were on"
    EventOutcome.NO_MATCH -> "No rule matched"
}
