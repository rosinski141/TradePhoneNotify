package com.mati.tradenotify

import android.app.Application
import android.content.Context
import com.mati.tradenotify.alarm.AlarmController
import com.mati.tradenotify.alarm.AlarmNotifications
import com.mati.tradenotify.data.SettingsStore
import com.mati.tradenotify.data.db.AppDatabase
import com.mati.tradenotify.ingest.ListenerWatchdogWorker
import com.mati.tradenotify.ingest.SignalPipeline

/**
 * Hand-rolled dependency container. The graph is four objects deep, so a DI framework would cost
 * more in build time and indirection than it would save.
 */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val database: AppDatabase by lazy { AppDatabase.get(app) }
    val settings: SettingsStore by lazy { SettingsStore(app) }
    val alarms: AlarmController by lazy { AlarmController(app, settings) }

    val pipeline: SignalPipeline by lazy {
        SignalPipeline(
            context = app,
            ruleDao = database.ruleDao(),
            eventDao = database.eventDao(),
            alarms = alarms,
        )
    }
}

class TradeNotifyApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AlarmNotifications.createChannels(this)
        ListenerWatchdogWorker.schedule(this)
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as TradeNotifyApp).container
    }
}
