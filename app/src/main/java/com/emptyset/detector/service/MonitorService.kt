package com.emptyset.detector.service

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.emptyset.detector.EmptySetApp
import com.emptyset.detector.MainActivity
import com.emptyset.detector.MonitorStore
import com.emptyset.detector.R
import com.emptyset.detector.alert.AlertController
import com.emptyset.detector.detect.AttackDetector
import com.emptyset.detector.detect.ChannelPlan
import com.emptyset.detector.detect.Ieee80211
import com.emptyset.detector.radio.RadioBackend
import com.emptyset.detector.radio.RadioKind
import com.emptyset.detector.radio.TplinkT2uBackend
import com.emptyset.detector.radio.UsbDeviceFinder
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitorService : LifecycleService() {
    private val alerts by lazy { AlertController(this) }
    private val detector = AttackDetector()
    private val log by lazy { (application as EmptySetApp).captureLog }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var backend: RadioBackend? = null
    private var hopJob: Job? = null
    private var sweepStartedAt = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_SILENCE -> runCatching { alerts.silence() }
            else -> startMonitor()
        }
        return START_STICKY
    }

    private fun startMonitor() {
        try {
            startForegroundNotification()
        } catch (t: Throwable) {
            MonitorStore.setRunning(false)
            MonitorStore.setStatus("Could not start monitor: ${t.message ?: t.javaClass.simpleName}")
            MonitorStore.addEvent("Foreground service failed")
            stopSelf()
            return
        }
        MonitorStore.setRunning(true)
        val chosen = runCatching {
            UsbDeviceFinder.attached(this).firstOrNull { it.kind == RadioKind.T2U_PLUS }
        }.getOrNull()
        if (chosen?.device == null) {
            MonitorStore.setStatus("No TP-Link T2U Plus attached")
            MonitorStore.addEvent("Connect the T2U Plus with a USB-OTG cable")
            return
        }
        MonitorStore.setRadio(chosen.title, chosen.detail, chosen.kind, chosen.canCapture)
        log.onRecordingSaved = { path ->
            MonitorStore.addEvent("Saved $path", force = true)
        }
        backend?.stop()
        backend = TplinkT2uBackend(this, chosen.device)
        hopJob?.cancel()
        val handler = CoroutineExceptionHandler { _, e ->
            MonitorStore.setStatus("Monitor failed: ${e.message ?: e.javaClass.simpleName}")
            MonitorStore.addEvent("Monitor crashed: ${e.message ?: e.javaClass.simpleName}")
        }
        hopJob = lifecycleScope.launch(handler) {
            try {
                launch { sweepClock(ChannelPlan.hopset(band24 = true, band5 = true).size) }
                backend?.start(listener)
            } catch (t: Throwable) {
                MonitorStore.setStatus("Monitor failed: ${t.message ?: t.javaClass.simpleName}")
                MonitorStore.addEvent("Monitor crashed: ${t.message ?: t.javaClass.simpleName}")
            }
        }
    }

    private val listener = object : RadioBackend.Listener {
        override fun onStatus(message: String) {
            MonitorStore.setStatus(message)
            MonitorStore.addEvent(message)
        }

        override fun onChannel(channel: Int) {
            detector.onChannel(channel)
            MonitorStore.setChannel(channel)
        }

        override fun onRawFrame(frame: ByteArray, channel: Int?, rssiDbm: Int?) {
            val event = Ieee80211.parse(frame, channel, rssiDbm) ?: return
            detector.onFrame(event)
            log.recordFrame(event, channel, rssiDbm)
            MonitorStore.addEvent(
                "${event.kind} ch=${event.channelHint ?: channel ?: "?"}  ${event.addr2} -> ${event.addr1}",
                event
            )
        }

        override fun onError(message: String) {
            MonitorStore.setStatus(message)
            MonitorStore.addEvent(message)
        }
    }

    private suspend fun sweepClock(channelCount: Int) {
        val windowMs = 140L * channelCount
        sweepStartedAt = System.currentTimeMillis()
        while (currentCoroutineContext().isActive) {
            delay(windowMs)
            val windowStart = sweepStartedAt
            sweepStartedAt = System.currentTimeMillis()
            val result = detector.onSweepEnd()
            MonitorStore.setSweep(result.packetsPerSweep, result.attacking)
            when {
                result.started -> {
                    log.startAttempt(windowStart)
                    MonitorStore.addEvent("DEAUTH ATTEMPT  ${result.packetsPerSweep} frames/sweep", force = true)
                    mainHandler.post {
                        alerts.startAttack(
                            "${result.packetsPerSweep} deauth/disassoc frames in one sweep",
                            MonitorStore.state.value.channel
                        )
                    }
                }
                result.stopped -> {
                    log.endAttempt()
                    MonitorStore.addEvent("ATTEMPT ENDED", force = true)
                    mainHandler.post { alerts.stopAttack() }
                }
            }
        }
    }

    private fun startForegroundNotification() {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, AlertController.MONITOR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("Empty Set")
            .setContentText("Passive 2.4/5 GHz deauthentication monitor")
            .setContentIntent(launch)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            startForeground(AlertController.MONITOR_ID, notification)
            return
        }
        val types = mutableListOf(ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            types += ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        }
        var started = false
        for (type in types) {
            try {
                startForeground(AlertController.MONITOR_ID, notification, type)
                started = true
                break
            } catch (_: Throwable) {
            }
        }
        if (!started) {
            startForeground(AlertController.MONITOR_ID, notification)
        }
    }

    override fun onDestroy() {
        hopJob?.cancel()
        backend?.stop()
        log.endAttempt()
        alerts.release()
        MonitorStore.setRunning(false)
        MonitorStore.setStatus("Stopped")
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.emptyset.detector.STOP"
        const val ACTION_SILENCE = "com.emptyset.detector.SILENCE"
    }
}
