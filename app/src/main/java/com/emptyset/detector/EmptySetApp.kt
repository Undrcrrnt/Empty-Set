package com.emptyset.detector

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.emptyset.detector.alert.AlertController
import com.emptyset.detector.alert.AlertSettings
import com.emptyset.detector.log.CaptureLog

class EmptySetApp : Application() {
    lateinit var captureLog: CaptureLog
        private set

    override fun onCreate() {
        super.onCreate()
        captureLog = CaptureLog(this)
        AlertSettings(this).rebuildChannel()
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                AlertController.MONITOR_CHANNEL_ID,
                getString(R.string.monitor_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                AlertController.ALERT_CHANNEL_ID,
                getString(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                enableVibration(false)
                setBypassDnd(true)
                setSound(null, null)
            }
        )
    }
}
