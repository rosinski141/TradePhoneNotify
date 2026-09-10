package com.mati.tradenotify.ingest

import android.util.Log
import com.mati.tradenotify.alarm.AlarmController
import com.mati.tradenotify.alarm.AlarmNotifications
import com.mati.tradenotify.alarm.AlarmPayload
import com.mati.tradenotify.data.db.EventDao
import com.mati.tradenotify.data.db.EventEntity
import com.mati.tradenotify.data.db.EventOutcome
import com.mati.tradenotify.data.db.RuleDao
import com.mati.tradenotify.data.db.RuleEntity
import com.mati.tradenotify.match.MatchOutcome
import com.mati.tradenotify.match.RuleMatcher
import com.mati.tradenotify.util.TAG
import android.content.Context

/**
 * The one path from an observed message to an alarm.
 *
 * Both the notification listener and the debug injector go through here, so testing with a
 * synthetic signal exercises exactly the production code path rather than an approximation.
 */
class SignalPipeline(
    private val context: Context,
    private val ruleDao: RuleDao,
    private val eventDao: EventDao,
    private val alarms: AlarmController,
) : SignalSink {

    override suspend fun onSignal(signal: Signal) {
        val now = System.currentTimeMillis()
        val rules = runCatching { ruleDao.getEnabled() }.getOrElse {
            Log.e(TAG, "Could not read rules: ${it.message}")
            emptyList()
        }

        when (val outcome = RuleMatcher.match(signal, rules, now)) {
            is MatchOutcome.NoMatch -> {
                Log.d(TAG, "No rule for '${signal.channel}': ${signal.text.take(60)}")
                record(signal, EventOutcome.NO_MATCH, null)
            }

            is MatchOutcome.Cooldown -> {
                Log.i(
                    TAG,
                    "Rule '${outcome.rule.name}' matched but is cooling down " +
                        "(${outcome.remainingMs / 1000}s left)",
                )
                record(signal, EventOutcome.COOLDOWN, outcome.rule)
            }

            is MatchOutcome.Fire -> fire(signal, outcome.rule, now)
        }
    }

    private suspend fun fire(signal: Signal, rule: RuleEntity, now: Long) {
        if (alarms.isQuietNow(now)) {
            Log.i(TAG, "Rule '${rule.name}' matched during quiet hours; staying silent")
            record(signal, EventOutcome.QUIET_HOURS, rule)
            AlarmNotifications.postStatus(
                context,
                "Signal received (quiet hours)",
                "${signal.channel}: ${signal.text}",
            )
            return
        }

        val eventId = record(signal, EventOutcome.ALARMED, rule)
        // Recorded before the alarm starts so the cooldown holds even if the service is killed.
        runCatching { ruleDao.markFired(rule.id, now) }

        alarms.fire(
            AlarmPayload(
                eventId = eventId,
                channel = signal.channel,
                text = signal.text,
                ruleName = rule.name,
                soundUri = rule.soundUri,
                vibrate = rule.vibrate,
                postedAt = signal.postedAt,
            ),
        )
    }

    private suspend fun record(
        signal: Signal,
        outcome: EventOutcome,
        rule: RuleEntity?,
    ): Long = runCatching {
        val id = eventDao.insert(
            EventEntity(
                postedAt = signal.postedAt,
                packageName = signal.packageName,
                channel = signal.channel,
                text = signal.text,
                outcome = outcome,
                matchedRuleId = rule?.id,
                matchedRuleName = rule?.name,
            ),
        )
        eventDao.trimTo(MAX_EVENTS)
        id
    }.getOrElse {
        Log.e(TAG, "Could not record event: ${it.message}")
        -1L
    }

    private companion object {
        /** Enough history to debug a rule without letting the log grow forever. */
        const val MAX_EVENTS = 500
    }
}
