package com.mati.tradenotify.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mati.tradenotify.TradeNotifyApp
import com.mati.tradenotify.data.AppSettings
import com.mati.tradenotify.data.RuleBackupCodec
import com.mati.tradenotify.data.db.ChannelSummary
import com.mati.tradenotify.data.db.EventEntity
import com.mati.tradenotify.data.db.RuleEntity
import com.mati.tradenotify.ingest.TelegramNotificationListener
import com.mati.tradenotify.update.AvailableUpdate
import com.mati.tradenotify.update.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Outcome of an import, so the UI can say what actually happened. */
sealed interface ImportResult {
    data class Success(val count: Int) : ImportResult
    data object NotABackup : ImportResult
    data object Failed : ImportResult
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = TradeNotifyApp.container(app)
    private val ruleDao = container.database.ruleDao()
    private val eventDao = container.database.eventDao()

    val rules: StateFlow<List<RuleEntity>> = ruleDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val events: StateFlow<List<EventEntity>> = eventDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val channels: StateFlow<List<ChannelSummary>> = eventDao.observeChannels()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = container.settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val listenerConnected: StateFlow<Boolean> = TelegramNotificationListener.connected

    /**
     * null until DataStore has actually been read.
     *
     * Defaulting to `false` would flash the setup wizard at existing users for a frame on every
     * cold start, because "not loaded yet" and "setup not done" would look identical.
     */
    val setupComplete: StateFlow<Boolean?> = container.settings.settings
        .map { it.setupComplete }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _update = MutableStateFlow<AvailableUpdate?>(null)
    val update: StateFlow<AvailableUpdate?> = _update

    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult: StateFlow<ImportResult?> = _importResult

    fun saveRule(rule: RuleEntity) = viewModelScope.launch {
        if (rule.id == 0L) ruleDao.insert(rule) else ruleDao.update(rule)
    }

    fun deleteRule(rule: RuleEntity) = viewModelScope.launch { ruleDao.delete(rule) }

    fun setRuleEnabled(rule: RuleEntity, enabled: Boolean) = viewModelScope.launch {
        ruleDao.setEnabled(rule.id, enabled)
    }

    fun clearHistory() = viewModelScope.launch { eventDao.clear() }

    /** Runs the real alarm path so what you test is what fires on a live signal. */
    fun testAlarm() = container.alarms.fireTest()

    // ---- setup ----

    fun setSetupComplete(value: Boolean) = viewModelScope.launch {
        container.settings.setSetupComplete(value)
    }

    // ---- backup ----

    fun exportRulesTo(uri: Uri, onDone: (Boolean) -> Unit) = viewModelScope.launch {
        val ok = runCatching {
            val text = RuleBackupCodec.export(ruleDao.getAll())
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(text.toByteArray())
                } ?: error("could not open $uri")
            }
        }.isSuccess
        onDone(ok)
    }

    fun importRulesFrom(uri: Uri) = viewModelScope.launch {
        val text = runCatching {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.readText()
            }
        }.getOrNull()

        if (text == null) {
            _importResult.value = ImportResult.Failed
            return@launch
        }
        val imported = RuleBackupCodec.import(text)
        if (imported == null) {
            _importResult.value = ImportResult.NotABackup
            return@launch
        }
        // Imported rules are added alongside existing ones rather than replacing them, so a
        // mistaken import is undone by deleting a few rules instead of losing everything.
        imported.forEach { ruleDao.insert(it) }
        _importResult.value = ImportResult.Success(imported.size)
    }

    fun clearImportResult() {
        _importResult.value = null
    }

    // ---- updates ----

    fun checkForUpdate() = viewModelScope.launch {
        _update.value = runCatching { UpdateChecker.check() }.getOrNull()
    }

    // ---- settings ----

    fun setWatchedPackages(value: Set<String>) = viewModelScope.launch {
        container.settings.setWatchedPackages(value)
    }

    fun setQuietHours(enabled: Boolean, start: Int, end: Int) = viewModelScope.launch {
        container.settings.setQuietHours(enabled, start, end)
    }

    fun setSnoozeMinutes(value: Int) = viewModelScope.launch {
        container.settings.setSnoozeMinutes(value)
    }

    fun setAutoStopMinutes(value: Int) = viewModelScope.launch {
        container.settings.setAutoStopMinutes(value)
    }

    fun setForceAlarmVolume(value: Boolean) = viewModelScope.launch {
        container.settings.setForceAlarmVolume(value)
    }

    fun setAlarmVolumePercent(value: Int) = viewModelScope.launch {
        container.settings.setAlarmVolumePercent(value)
    }
}
