package com.mati.tradenotify.data

import com.mati.tradenotify.data.db.MatchMode
import com.mati.tradenotify.data.db.RuleEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A portable copy of a rule set.
 *
 * Rules are the only thing in this app that can't be recreated automatically, and rebuilding them
 * by hand on a new phone is exactly where a mistyped channel name creeps in. Ids and `lastFiredAt`
 * are deliberately dropped — they are local bookkeeping, not part of what the rule means.
 */
@Serializable
data class RuleBackup(
    val version: Int = CURRENT_VERSION,
    @SerialName("exported_at") val exportedAt: Long = 0,
    val rules: List<BackupRule> = emptyList(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupRule(
    val name: String,
    val channelMatch: String,
    val channelMatchMode: String = MatchMode.EXACT.name,
    val packageFilter: String? = null,
    val includeKeywords: List<String> = emptyList(),
    val requireAllIncludes: Boolean = false,
    val excludeKeywords: List<String> = emptyList(),
    val cooldownSeconds: Int = 60,
    val vibrate: Boolean = true,
    val enabled: Boolean = true,
)

object RuleBackupCodec {

    private val json = Json {
        prettyPrint = true
        // A backup written by a newer build must not hard-fail an older one.
        ignoreUnknownKeys = true
    }

    fun export(rules: List<RuleEntity>, nowMs: Long = System.currentTimeMillis()): String =
        json.encodeToString(
            RuleBackup(
                exportedAt = nowMs,
                rules = rules.map { it.toBackup() },
            ),
        )

    /**
     * @return the decoded rules, or null if the file isn't a rule backup at all. Callers show that
     * as "this doesn't look like a TradeNotify backup" rather than crashing on a stray file.
     */
    fun import(text: String): List<RuleEntity>? {
        val backup = runCatching { json.decodeFromString<RuleBackup>(text) }.getOrNull() ?: return null
        if (backup.rules.isEmpty()) return emptyList()
        return backup.rules.map { it.toEntity() }
    }

    private fun RuleEntity.toBackup() = BackupRule(
        name = name,
        channelMatch = channelMatch,
        channelMatchMode = channelMatchMode.name,
        packageFilter = packageFilter,
        includeKeywords = includeKeywords,
        requireAllIncludes = requireAllIncludes,
        excludeKeywords = excludeKeywords,
        cooldownSeconds = cooldownSeconds,
        vibrate = vibrate,
        enabled = enabled,
    )

    private fun BackupRule.toEntity() = RuleEntity(
        // id 0 means "insert as new", so importing never clobbers an existing rule.
        id = 0,
        enabled = enabled,
        name = name,
        channelMatch = channelMatch,
        channelMatchMode = runCatching { MatchMode.valueOf(channelMatchMode) }
            .getOrDefault(MatchMode.EXACT),
        packageFilter = packageFilter,
        includeKeywords = includeKeywords,
        requireAllIncludes = requireAllIncludes,
        excludeKeywords = excludeKeywords,
        cooldownSeconds = cooldownSeconds,
        vibrate = vibrate,
        lastFiredAt = 0,
    )
}
