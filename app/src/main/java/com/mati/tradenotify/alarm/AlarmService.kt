package com.mati.tradenotify.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.mati.tradenotify.TradeNotifyApp
import com.mati.tradenotify.util.TAG
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns a ringing alarm: sound, vibration, wake lock and the full-screen notification.
 *
 * A foreground service rather than letting [AlarmActivity] play the sound, so the alarm keeps
 * ringing if the activity never launches (full-screen intent revoked) or the user swipes it away.
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var autoStop: Runnable? = null
    private var previousAlarmVolume: Int? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                stopEverything()
                return START_NOT_STICKY
            }

            ACTION_SNOOZE -> {
                val payload = AlarmPayload.from(intent)
                if (payload != null) scheduleSnooze(payload)
                stopEverything()
                return START_NOT_STICKY
            }

            else -> {
                val payload = AlarmPayload.from(intent)
                if (payload == null) {
                    stopEverything()
                    return START_NOT_STICKY
                }
                startRinging(payload)
            }
        }
        return START_NOT_STICKY
    }

    private fun startRinging(payload: AlarmPayload) {
        Log.i(TAG, "Alarm firing for '${payload.channel}' via rule '${payload.ruleName}'")
        _ringing.value = payload

        val notification = AlarmNotifications.buildAlarmNotification(this, payload)
        startForegroundCompat(notification)

        acquireWakeLock()
        startSound(payload)
        if (payload.vibrate) startVibration()
        scheduleAutoStop()
    }

    /**
     * `specialUse` is the correct type for an alarm, but it is the fussiest one to be granted.
     * Falling back to `mediaPlayback` keeps the alarm audible rather than crashing the service.
     */
    private fun startForegroundCompat(notification: android.app.Notification) {
        val types = listOf(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
        for (type in types) {
            val ok = runCatching {
                ServiceCompat.startForeground(
                    this,
                    AlarmNotifications.ALARM_NOTIFICATION_ID,
                    notification,
                    type,
                )
            }.onFailure { Log.w(TAG, "startForeground type=$type failed: ${it.message}") }.isSuccess
            if (ok) return
        }
        Log.e(TAG, "Could not enter the foreground; alarm may be killed early")
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TradeNotify:alarm").apply {
            setReferenceCounted(false)
            runCatching { acquire(MAX_RING_MS) }
        }
    }

    private fun startSound(payload: AlarmPayload) {
        val settings = TradeNotifyApp.container(this).settings
        scope.launch {
            val current = runCatching { settings.current() }.getOrNull()
            val chosen = payload.soundUri ?: current?.defaultSoundUri
            handler.post {
                if (current?.forceAlarmVolume == true) {
                    raiseAlarmVolume(current.alarmVolumePercent)
                }
                playFirstWorking(candidateSounds(chosen))
            }
        }
    }

    /** Falls back through user choice, system alarm, then ringtone — silence is the worst outcome. */
    private fun candidateSounds(preferred: String?): List<Uri> = buildList {
        preferred?.let { runCatching { Uri.parse(it) }.getOrNull()?.let(::add) }
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)?.let(::add)
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)?.let(::add)
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)?.let(::add)
    }

    private fun playFirstWorking(candidates: List<Uri>) {
        stopSound()
        for (uri in candidates) {
            val attempt = runCatching {
                MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            }
            if (attempt.isSuccess) {
                player = attempt.getOrNull()
                return
            }
            attempt.exceptionOrNull()?.let { Log.w(TAG, "Sound $uri failed: ${it.message}") }
        }
        Log.e(TAG, "No alarm sound could be played")
    }

    private fun raiseAlarmVolume(percent: Int) {
        val am = getSystemService(AudioManager::class.java) ?: return
        runCatching {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            previousAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val target = (max * percent / 100).coerceIn(1, max)
            am.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        }.onFailure { Log.w(TAG, "Could not raise alarm volume: ${it.message}") }
    }

    private fun restoreAlarmVolume() {
        val previous = previousAlarmVolume ?: return
        previousAlarmVolume = null
        val am = getSystemService(AudioManager::class.java) ?: return
        runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, previous, 0) }
    }

    private fun startVibration() {
        val v = resolveVibrator() ?: return
        vibrator = v
        val pattern = longArrayOf(0, 600, 400, 600, 1200)
        val effect = VibrationEffect.createWaveform(pattern, 0)
        runCatching {
            v.vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }.onFailure { Log.w(TAG, "Vibration failed: ${it.message}") }
    }

    private fun resolveVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }

    private fun scheduleAutoStop() {
        autoStop?.let(handler::removeCallbacks)
        val settings = TradeNotifyApp.container(this).settings
        scope.launch {
            val minutes = runCatching { settings.current().autoStopMinutes }.getOrDefault(5)
            handler.post {
                val runnable = Runnable {
                    Log.i(TAG, "Alarm auto-stopped after $minutes min")
                    stopEverything()
                }
                autoStop = runnable
                handler.postDelayed(runnable, minutes * 60_000L)
            }
        }
    }

    private fun scheduleSnooze(payload: AlarmPayload) {
        val settings = TradeNotifyApp.container(this).settings
        val context = applicationContext
        scope.launch {
            val minutes = runCatching { settings.current().snoozeMinutes }.getOrDefault(5)
            SnoozeScheduler.schedule(context, payload, minutes)
        }
    }

    private fun stopSound() {
        player?.let { p -> runCatching { p.stop() }; runCatching { p.release() } }
        player = null
    }

    private fun stopEverything() {
        autoStop?.let(handler::removeCallbacks)
        autoStop = null
        stopSound()
        runCatching { vibrator?.cancel() }
        vibrator = null
        restoreAlarmVolume()
        wakeLock?.let { if (it.isHeld) runCatching { it.release() } }
        wakeLock = null
        _ringing.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopEverything()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.mati.tradenotify.ALARM_START"
        const val ACTION_DISMISS = "com.mati.tradenotify.ALARM_DISMISS"
        const val ACTION_SNOOZE = "com.mati.tradenotify.ALARM_SNOOZE"

        private const val MAX_RING_MS = 60 * 60 * 1000L

        private val _ringing = MutableStateFlow<AlarmPayload?>(null)

        /** Non-null while an alarm is sounding; drives the in-app "ringing" state. */
        val ringing: StateFlow<AlarmPayload?> = _ringing

        fun start(context: Context, payload: AlarmPayload) {
            val intent = payload.writeTo(
                Intent(context, AlarmService::class.java).setAction(ACTION_START),
            )
            context.startForegroundService(intent)
        }

        fun dismiss(context: Context) {
            context.startService(
                Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS),
            )
        }

        fun snooze(context: Context, payload: AlarmPayload) {
            context.startService(
                payload.writeTo(
                    Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE),
                ),
            )
        }
    }
}
