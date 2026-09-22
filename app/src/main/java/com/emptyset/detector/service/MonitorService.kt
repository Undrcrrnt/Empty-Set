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
import com.emptyset.detector.detect.WifiBand
import com.emptyset.detector.radio.RadioBackend
import com.emptyset.detector.radio.RadioFactory
import com.emptyset.detector.radio.RadioSelection
import com.emptyset.detector.radio.UsbDeviceFinder
import com.emptyset.detector.radio.UsbIds
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
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
        MonitorStore.clearEvents()
        val option = RadioSelection(this).selected
        if (!option.backendReady) {
            MonitorStore.setRadio(option.title, option.detail, option.kind, false)
            MonitorStore.setStatus("${option.title}: backend not added yet")
            MonitorStore.addEvent("Selected adapter has no receive backend in this build")
            return
        }
        val attached = runCatching { UsbDeviceFinder.attached(this) }.getOrDefault(emptyList())
        val chosen = attached.firstOrNull { it.kind == option.kind }
            ?: attached.firstOrNull { info -> UsbIds.matches(option.kind, info.device) }
        if (chosen?.device == null) {
            MonitorStore.setRadio(option.title, "Selected. Plug it in with OTG.", option.kind, false)
            MonitorStore.setStatus("${option.title} not attached")
            MonitorStore.addEvent("Connect ${option.title} with a USB-OTG cable")
            return
        }
        val opened = RadioFactory.create(this, option, chosen.device)
        if (opened == null) {
            MonitorStore.setRadio(option.title, option.detail, option.kind, false)
            MonitorStore.setStatus("${option.title}: backend not added yet")
            MonitorStore.addEvent("Selected adapter has no receive backend in this build")
            return
        }
        MonitorStore.setRadio(option.title, chosen.detail, option.kind, chosen.canCapture)
        log.onRecordingSaved = { path ->
            MonitorStore.addEvent("Saved $path", force = true)
        }
        backend?.stop()
        backend = opened
        hopJob?.cancel()
        val handler = CoroutineExceptionHandler { _, e ->
            MonitorStore.setStatus("Monitor failed: ${e.message ?: e.javaClass.simpleName}")
            MonitorStore.addEvent("Monitor crashed: ${e.message ?: e.javaClass.simpleName}")
        }
        hopJob = lifecycleScope.launch(Dispatchers.IO + handler) {
            try {
                val hops = ChannelPlan.hopsetFor(
                    option.kind,
                    RadioSelection(this@MonitorService).band24,
                    RadioSelection(this@MonitorService).band5,
                    RadioSelection(this@MonitorService).band6
                )
                launch { sweepClock(hops.size.coerceAtLeast(1)) }
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

        override fun onChannel(channel: Int, band: WifiBand) {
            detector.onChannel(channel)
            MonitorStore.setChannel(channel, band)
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
                    val snap = MonitorStore.state.value
                    val summary = "${result.packetsPerSweep} frames  ch=${snap.channel ?: "?"}  ${snap.band?.label ?: ""}"
                    MonitorStore.latchAlert(summary)
                    MonitorStore.addEvent("DEAUTH ATTEMPT  ${result.packetsPerSweep} frames/sweep", force = true)
                    mainHandler.post {
                        alerts.startAttack(
                            "${result.packetsPerSweep} deauth/disassoc frames in one sweep",
                            snap.channel
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
