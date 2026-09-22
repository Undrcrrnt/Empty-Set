package com.emptyset.detector.alert

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import com.emptyset.detector.R

enum class AlertSoundMode {
    BUNDLED,
    RINGTONE,
    CUSTOM,
    VIBRATE_ONLY
}

class AlertSettings(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    var soundMode: AlertSoundMode
        get() = runCatching {
            AlertSoundMode.valueOf(prefs.getString(KEY_MODE, AlertSoundMode.BUNDLED.name)!!)
        }.getOrDefault(AlertSoundMode.BUNDLED)
        set(value) {
            prefs.edit().putString(KEY_MODE, value.name).commit()
            rebuildChannelAsync()
        }

    var ringtoneUri: String?
        get() = prefs.getString(KEY_RINGTONE, null)
        set(value) {
            prefs.edit().putString(KEY_RINGTONE, value).commit()
            rebuildChannelAsync()
        }

    var customUri: String?
        get() = prefs.getString(KEY_CUSTOM, null)
        set(value) {
            prefs.edit().putString(KEY_CUSTOM, value).commit()
            rebuildChannelAsync()
        }

    var lockScreenMessageInput: String
        get() = prefs.getString(KEY_MESSAGE, DEFAULT_MESSAGE) ?: DEFAULT_MESSAGE
        set(value) {
            prefs.edit().putString(KEY_MESSAGE, value).apply()
        }

    var senderNameInput: String
        get() = prefs.getString(KEY_SENDER, DEFAULT_SENDER) ?: DEFAULT_SENDER
        set(value) {
            prefs.edit().putString(KEY_SENDER, value).apply()
            applySenderLabel()
        }

    val lockScreenMessage: String
        get() = lockScreenMessageInput.trim().ifBlank { DEFAULT_MESSAGE }

    val senderName: String
        get() = senderNameInput.trim().ifBlank { DEFAULT_SENDER }

    fun applySenderLabel() {
        app.applicationInfo.nonLocalizedLabel = senderName
    }

    val vibrate: Boolean
        get() = true

    val silent: Boolean
        get() = soundMode == AlertSoundMode.VIBRATE_ONLY

    val channelId: String
        get() = "emptyset.incoming.${soundMode.name.lowercase()}"

    val silentChannelId: String
        get() = "emptyset.incoming.muted"

    fun soundUri(): Uri? {
        if (silent) return null
        return when (soundMode) {
            AlertSoundMode.BUNDLED -> bundledUri()
            AlertSoundMode.RINGTONE -> ringtoneUri?.let(Uri::parse)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            AlertSoundMode.CUSTOM -> customUri?.let(Uri::parse) ?: bundledUri()
            AlertSoundMode.VIBRATE_ONLY -> null
        }
    }

    fun bundledUri(): Uri =
        Uri.parse("android.resource://${app.packageName}/${R.raw.incoming_deauth}")

    fun label(): String = when (soundMode) {
        AlertSoundMode.BUNDLED -> "Built-in alert tone"
        AlertSoundMode.RINGTONE -> "Phone ringtone"
        AlertSoundMode.CUSTOM -> "Custom audio file"
        AlertSoundMode.VIBRATE_ONLY -> "Vibration only"
    }

    fun rebuildChannelAsync() {
        Thread({ rebuildChannel() }, "emptyset-channel").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun rebuildChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.notificationChannels
            .filter { it.id.startsWith("emptyset.incoming") || it.id == "emptyset.alerts" }
            .forEach { manager.deleteNotificationChannel(it.id) }

        // Sound lives in AlertController's MediaPlayer/Ringtone, not the channel.
        // A channel sound would restart at notification volume when the alert is updated (Silence).
        val alert = NotificationChannel(
            channelId,
            app.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = lockScreenMessage
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setBypassDnd(true)
            setShowBadge(true)
            setSound(null, null)
        }
        val muted = NotificationChannel(
            silentChannelId,
            app.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "$lockScreenMessage (sound off)"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            enableVibration(false)
            setSound(null, null)
        }
        manager.createNotificationChannel(alert)
        manager.createNotificationChannel(muted)
    }

    companion object {
        const val DEFAULT_MESSAGE = "Incoming Deauthentication"
        const val DEFAULT_SENDER = "Empty Set"
        private const val KEY_MODE = "sound_mode"
        private const val KEY_RINGTONE = "ringtone_uri"
        private const val KEY_CUSTOM = "custom_uri"
        private const val KEY_MESSAGE = "lock_message"
        private const val KEY_SENDER = "sender_name"
    }
}
