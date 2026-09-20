package com.emptyset.detector.radio

import android.hardware.usb.UsbDevice

enum class RadioKind { T2U_PLUS, UNKNOWN }

data class RadioInfo(
    val kind: RadioKind,
    val title: String,
    val detail: String,
    val device: UsbDevice?,
    val canCapture: Boolean
)

interface RadioBackend {
    val info: RadioInfo
    suspend fun start(listener: Listener)
    fun stop()

    interface Listener {
        fun onStatus(message: String)
        fun onChannel(channel: Int)
        fun onRawFrame(frame: ByteArray, channel: Int?, rssiDbm: Int?)
        fun onError(message: String)
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.emptyset.detector.USB_PERMISSION"
    }
}

object UsbIds {
    const val TPLINK_VID = 0x2357
    const val T2U_PLUS_PID = 0x0120
    const val REALTEK_VID = 0x0BDA
    const val RTL8821AU_PID = 0x0811
    const val RTL8821AU_PID_ALT = 0x0821

    fun classify(device: UsbDevice): RadioKind = when {
        device.vendorId == TPLINK_VID && device.productId == T2U_PLUS_PID -> RadioKind.T2U_PLUS
        device.vendorId == REALTEK_VID &&
            (device.productId == RTL8821AU_PID || device.productId == RTL8821AU_PID_ALT) -> RadioKind.T2U_PLUS
        else -> RadioKind.UNKNOWN
    }
}
