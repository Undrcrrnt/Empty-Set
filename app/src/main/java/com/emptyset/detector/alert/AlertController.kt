package com.emptyset.detector.alert

import android.app.KeyguardManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.emptyset.detector.MainActivity
import com.emptyset.detector.MonitorStore
import com.emptyset.detector.R
import com.emptyset.detector.service.MonitorService

class AlertController(private val context: Context) {
    private val settings = AlertSettings(context)
    private val notifications = context.getSystemService(NotificationManager::class.java)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    @Volatile
    private var attacking = false
    private var ringtone: Ringtone? = null
    private var player: MediaPlayer? = null

    fun startAttack(detail: String, channel: Int? = null) {
        attacking = true
        MonitorStore.setSoundSilenced(false)
        postAlertNotification(detail, channel, silenced = false)
        startSound()
        if (settings.vibrate) vibrate(true)
    }

    fun silence() {
        if (!attacking) return
        stopSound()
        vibrator?.cancel()
        MonitorStore.setSoundSilenced(true)
        postAlertNotification(
            "Recording continues. Sound silenced.",
            MonitorStore.state.value.channel,
            silenced = true
        )
    }

    fun stopAttack() {
        attacking = false
        notifications.cancel(ALERT_ID)
        stopSound()
        vibrator?.cancel()
        MonitorStore.setSoundSilenced(false)
    }

    private fun postAlertNotification(detail: String, channel: Int?, silenced: Boolean) {
        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val silence = PendingIntent.getService(
            context,
            2,
            Intent(context, MonitorService::class.java)
                .setAction(MonitorService.ACTION_SILENCE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getService(
            context,
            3,
            Intent(context, MonitorService::class.java)
                .setAction(MonitorService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val lockScreen = PendingIntent.getActivity(
            context,
            0,
            Intent(context, IncomingAlertActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(IncomingAlertActivity.EXTRA_CHANNEL, channel ?: -1)
                .putExtra(IncomingAlertActivity.EXTRA_MESSAGE, settings.lockScreenMessage)
                .putExtra(IncomingAlertActivity.EXTRA_SENDER, settings.senderName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        settings.applySenderLabel()
        val builder = NotificationCompat.Builder(
            context,
            if (silenced) settings.silentChannelId else settings.channelId
        )
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(settings.lockScreenMessage)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setPriority(if (silenced) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(silenced)
            .setContentIntent(openApp)
            .setDefaults(0)
            .setSound(null)
            .setVibrate(null)
            .addAction(0, "Silence", silence)
            .addAction(0, "Stop recording", stop)
        builder.extras.putCharSequence("android.substName", settings.senderName)
        if (!silenced && shouldLaunchLockScreen()) {
            builder.setFullScreenIntent(lockScreen, true)
        }
        notifications.notify(ALERT_ID, builder.build())
    }

    private fun shouldLaunchLockScreen(): Boolean {
        if (MonitorStore.mainResumed) return false
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val power = context.getSystemService(PowerManager::class.java)
        val locked = keyguard?.isKeyguardLocked == true
        val interactive = power?.isInteractive == true
        return locked || !interactive
    }

    fun release() {
        stopAttack()
    }

    private fun startSound() {
        stopSound()
        if (settings.silent) return
        val uri = settings.soundUri() ?: return
        try {
            if (settings.soundMode == AlertSoundMode.RINGTONE) {
                ringtone = RingtoneManager.getRingtone(context, uri)?.also {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.isLooping = true
                    it.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    it.play()
                }
            } else {
                player = MediaPlayer().also {
                    it.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    it.setDataSource(context, uri)
                    it.isLooping = true
                    it.prepare()
                    it.start()
                }
            }
        } catch (_: Exception) {
            try {
                player = MediaPlayer.create(context, R.raw.incoming_deauth)?.also {
                    it.isLooping = true
                    it.start()
                }
            } catch (_: Exception) { }
        }
    }

    private fun stopSound() {
        runCatching { ringtone?.stop() }
        ringtone = null
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
    }

    private fun vibrate(repeating: Boolean) {
        val pattern = longArrayOf(0, 400, 200, 400, 200, 600)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                if (repeating) VibrationEffect.createWaveform(pattern, 0)
                else VibrationEffect.createWaveform(pattern, -1)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, if (repeating) 0 else -1)
        }
    }

    companion object {
        const val MONITOR_CHANNEL_ID = "emptyset.monitor"
        const val ALERT_CHANNEL_ID = "emptyset.incoming.bundled"
        const val MONITOR_ID = 1001
        const val ALERT_ID = 1002
    }
}
