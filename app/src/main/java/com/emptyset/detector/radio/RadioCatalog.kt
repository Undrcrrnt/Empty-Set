package com.emptyset.detector.radio

import android.content.Context
import android.hardware.usb.UsbDevice

data class RadioOption(
    val id: String,
    val kind: RadioKind,
    val title: String,
    val detail: String,
    val backendReady: Boolean
)

object RadioCatalog {
    const val T2U_PLUS = "t2u_plus"
    const val PAU0A = "pau0a"
    const val PAU0B = "pau0b"

    val options: List<RadioOption> = listOf(
        RadioOption(
            id = T2U_PLUS,
            kind = RadioKind.T2U_PLUS,
            title = "TP-Link Archer T2U Plus",
            detail = "RTL8821AU  ·  receive-only  ·  ready",
            backendReady = true
        ),
        RadioOption(
            id = PAU0A,
            kind = RadioKind.PAU0A,
            title = "Panda PAU0A",
            detail = "MT7610U  ·  receive-only  ·  0e8d:7610",
            backendReady = true
        ),
        RadioOption(
            id = PAU0B,
            kind = RadioKind.PAU0B,
            title = "Panda PAU0B",
            detail = "MT7610U  ·  receive-only  ·  0e8d:7610",
            backendReady = true
        )
    )

    fun option(id: String): RadioOption =
        options.firstOrNull { it.id == id } ?: options.first()
}

class RadioSelection(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("radio", Context.MODE_PRIVATE)

    var selectedId: String
        get() = prefs.getString(KEY_ID, RadioCatalog.T2U_PLUS) ?: RadioCatalog.T2U_PLUS
        set(value) {
            prefs.edit().putString(KEY_ID, value).apply()
        }

    val selected: RadioOption
        get() = RadioCatalog.option(selectedId)

    var band24: Boolean
        get() = prefs.getBoolean(KEY_24, true)
        set(value) = setBand(KEY_24, value)

    var band5: Boolean
        get() = prefs.getBoolean(KEY_5, true)
        set(value) = setBand(KEY_5, value)

    var band6: Boolean
        get() = prefs.getBoolean(KEY_6, false)
        set(value) = setBand(KEY_6, value)

    private fun setBand(key: String, value: Boolean) {
        if (!value) {
            val othersOn = listOf(
                KEY_24 to band24,
                KEY_5 to band5,
                KEY_6 to band6
            ).any { (k, on) -> k != key && on }
            if (!othersOn) return
        }
        prefs.edit().putBoolean(key, value).apply()
    }

    companion object {
        private const val KEY_ID = "selected_id"
        private const val KEY_24 = "band_24"
        private const val KEY_5 = "band_5"
        private const val KEY_6 = "band_6"
    }
}

object RadioFactory {
    fun create(context: Context, option: RadioOption, device: UsbDevice): RadioBackend? {
        val bands = RadioSelection(context)
        return when (option.kind) {
            RadioKind.T2U_PLUS -> TplinkT2uBackend(
                context, device, option, bands.band24, bands.band5, bands.band6
            )
            RadioKind.PAU0A,
            RadioKind.PAU0B -> PandaPau0aBackend(
                context, device, option, bands.band24, bands.band5, bands.band6
            )
            RadioKind.UNKNOWN -> null
        }
    }
}
