package com.mati.tradenotify.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
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
import com.mati.tradenotify.data.RulePreset
import com.mati.tradenotify.data.RulePresets
import com.mati.tradenotify.data.db.MatchMode
import com.mati.tradenotify.data.db.RuleEntity
import com.mati.tradenotify.ingest.Signal
import com.mati.tradenotify.match.RuleMatcher
import com.mati.tradenotify.ui.MainViewModel

@Composable
fun RuleEditScreen(
    viewModel: MainViewModel,
    ruleId: Long,
    prefillChannel: String,
    onDone: () -> Unit,
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val existing = remember(ruleId, rules) { rules.firstOrNull { it.id == ruleId } }

    var name by remember(existing) {
        mutableStateOf(existing?.name ?: prefillChannel.ifBlank { "New rule" })
    }
    var channel by remember(existing) {
        mutableStateOf(existing?.channelMatch ?: prefillChannel)
    }
    var mode by remember(existing) {
        mutableStateOf(existing?.channelMatchMode ?: MatchMode.EXACT)
    }
    var includes by remember(existing) {
        mutableStateOf(existing?.includeKeywords?.joinToString("\n") ?: "BUY\nSELL\nENTRY")
    }
    var requireAll by remember(existing) {
        mutableStateOf(existing?.requireAllIncludes ?: false)
    }
    var excludes by remember(existing) {
        mutableStateOf(existing?.excludeKeywords?.joinToString("\n") ?: "TP HIT\nCLOSED\nRESULT")
    }
    var cooldown by remember(existing) {
        mutableStateOf((existing?.cooldownSeconds ?: 60).toString())
    }
    var vibrate by remember(existing) { mutableStateOf(existing?.vibrate ?: true) }
    var sample by remember { mutableStateOf("") }

    fun build(): RuleEntity = RuleEntity(
        id = existing?.id ?: 0L,
        enabled = existing?.enabled ?: true,
        name = name.ifBlank { channel.ifBlank { "Rule" } },
        channelMatch = channel.trim(),
        channelMatchMode = mode,
        packageFilter = existing?.packageFilter,
        includeKeywords = includes.lines().map { it.trim() }.filter { it.isNotEmpty() },
        requireAllIncludes = requireAll,
        excludeKeywords = excludes.lines().map { it.trim() }.filter { it.isNotEmpty() },
        cooldownSeconds = cooldown.toIntOrNull()?.coerceIn(0, 3600) ?: 60,
        soundUri = existing?.soundUri,
        vibrate = vibrate,
        lastFiredAt = existing?.lastFiredAt ?: 0L,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (existing == null) "New rule" else "Edit rule",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )

        if (existing == null) {
            PresetPicker { preset ->
                includes = preset.includeKeywords.joinToString("\n")
                excludes = preset.excludeKeywords.joinToString("\n")
                if (name.isBlank() || name == "New rule" || name == prefillChannel) {
                    name = if (prefillChannel.isNotBlank()) prefillChannel else preset.title
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Rule name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = channel,
            onValueChange = { channel = it },
            label = { Text("Channel title") },
            supportingText = { Text(channelHelp(channel, mode)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // The match mode does nothing to a blank pattern, so it is disabled rather than hidden —
        // removing the row would make the whole form jump the moment the first character is typed.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            MatchMode.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = mode == entry,
                    onClick = { mode = entry },
                    enabled = channel.isNotBlank(),
                    shape = SegmentedButtonDefaults.itemShape(index, MatchMode.entries.size),
                ) {
                    Text(entry.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }

        OutlinedTextField(
            value = includes,
            onValueChange = { includes = it },
            label = { Text("Must contain — one per line") },
            supportingText = {
                Text("Leave empty to alarm on every message. Wrap in slashes for a regex: /^BUY\\b/")
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = requireAll, onCheckedChange = { requireAll = it })
            Spacer(Modifier.height(0.dp))
            Text(
                if (requireAll) "  All of them must be present" else "  Any one of them is enough",
                fontSize = 14.sp,
            )
        }

        OutlinedTextField(
            value = excludes,
            onValueChange = { excludes = it },
            label = { Text("Must NOT contain — one per line") },
            supportingText = { Text("Useful for follow-ups like \"TP hit\" or \"closed\".") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = cooldown,
            onValueChange = { cooldown = it.filter { c -> c.isDigit() }.take(4) },
            label = { Text("Cooldown (seconds)") },
            supportingText = { Text("Stops a burst of messages from alarming over and over.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = vibrate, onCheckedChange = { vibrate = it })
            Text("  Vibrate", fontSize = 14.sp)
        }

        RuleTester(rule = build(), sample = sample, onSampleChange = { sample = it })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    viewModel.saveRule(build())
                    onDone()
                },
                modifier = Modifier.weight(1f),
            ) { Text("Save") }
            OutlinedButton(onClick = onDone) { Text("Cancel") }
        }

        if (existing != null) {
            OutlinedButton(
                onClick = {
                    viewModel.deleteRule(existing)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Delete rule", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * One-tap starting points for the keyword lists.
 *
 * Only offered on a new rule — silently rewriting the keywords of a rule you already tuned would
 * be a nasty surprise.
 */
@Composable
private fun PresetPicker(onPick: (RulePreset) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Start from a preset", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                "Fills in the keywords below. You can edit them afterwards.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            RulePresets.ALL.forEach { preset ->
                OutlinedButton(
                    onClick = { onPick(preset) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(preset.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(
                            preset.summary,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Explains what the channel field will actually do, including the blank "any chat" case. */
private fun channelHelp(channel: String, mode: MatchMode): String = when {
    channel.isBlank() ->
        "Empty — watches every chat. Type a name to limit this rule to one channel."

    mode == MatchMode.EXACT ->
        "Must match the channel name exactly as Telegram shows it in notifications."

    mode == MatchMode.CONTAINS ->
        "Matches any channel whose name contains this text."

    else ->
        "Matched as a regular expression against the channel name."
}

/**
 * Live preview of the rule against a sample message.
 *
 * Getting a rule subtly wrong is the likeliest way to miss a signal, and the feedback loop of
 * "wait for a real post to find out" is hours long. This closes it to instant.
 */
@Composable
private fun RuleTester(rule: RuleEntity, sample: String, onSampleChange: (String) -> Unit) {
    val matches = remember(rule, sample) {
        sample.isNotBlank() && RuleMatcher.wants(
            rule,
            Signal(
                packageName = rule.packageFilter ?: "org.telegram.messenger",
                channel = rule.channelMatch,
                text = sample,
                postedAt = System.currentTimeMillis(),
            ),
        )
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Test this rule", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = sample,
                onValueChange = onSampleChange,
                label = { Text("Paste a real message") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            if (sample.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (matches) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                ) {
                    Text(
                        if (matches) "✓ Would alarm" else "· Would be ignored",
                        modifier = Modifier.padding(12.dp),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
