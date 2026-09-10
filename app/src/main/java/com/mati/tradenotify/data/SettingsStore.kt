package com.mati.tradenotify.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val watchedPackages: Set<String> = TelegramPackages.DEFAULT_WATCHED,
    val quietHoursEnabled: Boolean = false,
    val quietStartMinutes: Int = 22 * 60,
    val quietEndMinutes: Int = 7 * 60,
    val snoozeMinutes: Int = 5,
    /** Stop ringing by itself after this long so a missed alarm can't flatten the battery. */
    val autoStopMinutes: Int = 5,
    val defaultSoundUri: String? = null,
    /** Raise the alarm stream to [alarmVolumePercent] while ringing, restoring it afterwards. */
    val forceAlarmVolume: Boolean = true,
    val alarmVolumePercent: Int = 100,
    /** False until the first-run wizard has been completed or skipped. */
    val setupComplete: Boolean = false,
    /**
     * Whether the POST_NOTIFICATIONS dialog has been shown at least once.
     *
     * Android silently auto-denies a permission the user has refused twice, and some OEM
     * builds surface that as a "denied access to this permission" toast. Knowing we have
     * asked before lets us send the user to Settings instead of triggering that.
     */
    val askedPostNotifications: Boolean = false,
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val WATCHED = stringSetPreferencesKey("watched_packages")
        val QUIET_ENABLED = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val SNOOZE = intPreferencesKey("snooze_minutes")
        val AUTO_STOP = intPreferencesKey("auto_stop_minutes")
        val SOUND = stringPreferencesKey("default_sound_uri")
        val FORCE_VOLUME = booleanPreferencesKey("force_alarm_volume")
        val VOLUME_PCT = intPreferencesKey("alarm_volume_percent")
        val SETUP_DONE = booleanPreferencesKey("setup_complete")
        val ASKED_POST = booleanPreferencesKey("asked_post_notifications")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        val defaults = AppSettings()
        AppSettings(
            watchedPackages = p[Keys.WATCHED] ?: defaults.watchedPackages,
            quietHoursEnabled = p[Keys.QUIET_ENABLED] ?: defaults.quietHoursEnabled,
            quietStartMinutes = p[Keys.QUIET_START] ?: defaults.quietStartMinutes,
            quietEndMinutes = p[Keys.QUIET_END] ?: defaults.quietEndMinutes,
            snoozeMinutes = p[Keys.SNOOZE] ?: defaults.snoozeMinutes,
            autoStopMinutes = p[Keys.AUTO_STOP] ?: defaults.autoStopMinutes,
            defaultSoundUri = p[Keys.SOUND],
            forceAlarmVolume = p[Keys.FORCE_VOLUME] ?: defaults.forceAlarmVolume,
            alarmVolumePercent = p[Keys.VOLUME_PCT] ?: defaults.alarmVolumePercent,
            setupComplete = p[Keys.SETUP_DONE] ?: defaults.setupComplete,
            askedPostNotifications = p[Keys.ASKED_POST] ?: defaults.askedPostNotifications,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setWatchedPackages(value: Set<String>) = edit { it[Keys.WATCHED] = value }

    suspend fun setQuietHours(enabled: Boolean, startMinutes: Int, endMinutes: Int) = edit {
        it[Keys.QUIET_ENABLED] = enabled
        it[Keys.QUIET_START] = startMinutes
        it[Keys.QUIET_END] = endMinutes
    }

    suspend fun setSnoozeMinutes(value: Int) = edit { it[Keys.SNOOZE] = value.coerceIn(1, 120) }

    suspend fun setAutoStopMinutes(value: Int) = edit { it[Keys.AUTO_STOP] = value.coerceIn(1, 60) }

    suspend fun setDefaultSoundUri(value: String?) = edit {
        if (value == null) it.remove(Keys.SOUND) else it[Keys.SOUND] = value
    }

    suspend fun setForceAlarmVolume(value: Boolean) = edit { it[Keys.FORCE_VOLUME] = value }

    suspend fun setSetupComplete(value: Boolean) = edit { it[Keys.SETUP_DONE] = value }

    suspend fun setAskedPostNotifications() = edit { it[Keys.ASKED_POST] = true }

    suspend fun setAlarmVolumePercent(value: Int) = edit {
        it[Keys.VOLUME_PCT] = value.coerceIn(10, 100)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
