package com.mati.tradenotify.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mati.tradenotify.data.db.MatchMode
import com.mati.tradenotify.data.db.RuleEntity
import com.mati.tradenotify.ui.MainViewModel

@Composable
fun RulesScreen(
    viewModel: MainViewModel,
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
    onPickChannel: () -> Unit,
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Text("Rules", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "A message alarms when it comes from a matching channel and passes the " +
                            "keyword filters. Rules are checked top to bottom.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onPickChannel) {
                        Text("Pick from channels already seen")
                    }
                }
            }

            if (rules.isEmpty()) {
                item {
                    Text(
                        "No rules yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            items(rules, key = { it.id }) { rule ->
                RuleCard(
                    rule = rule,
                    onToggle = { viewModel.setRuleEnabled(rule, it) },
                    onClick = { onEditRule(rule.id) },
                )
            }

            item { Spacer(Modifier.height(96.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = onAddRule,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            text = { Text("New rule") },
            icon = { Text("+") },
        )
    }
}

@Composable
private fun RuleCard(rule: RuleEntity, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    channelDescription(rule),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    keywordDescription(rule),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

private fun channelDescription(rule: RuleEntity): String {
    // A blank pattern matches every chat. Saying so beats rendering `Channel is ""`, which reads
    // like a rule that can never match when it is in fact the broadest one you can write.
    if (rule.channelMatch.isBlank()) return "Any channel"

    val verb = when (rule.channelMatchMode) {
        MatchMode.EXACT -> "is"
        MatchMode.CONTAINS -> "contains"
        MatchMode.REGEX -> "matches"
    }
    return "Channel $verb \"${rule.channelMatch}\""
}

private fun keywordDescription(rule: RuleEntity): String {
    val parts = mutableListOf<String>()
    if (rule.includeKeywords.isEmpty()) {
        parts += "every message"
    } else {
        val joiner = if (rule.requireAllIncludes) " AND " else " / "
        parts += rule.includeKeywords.joinToString(joiner)
    }
    if (rule.excludeKeywords.isNotEmpty()) {
        parts += "not ${rule.excludeKeywords.joinToString(" / ")}"
    }
    parts += "${rule.cooldownSeconds}s cooldown"
    return parts.joinToString(" · ")
}
