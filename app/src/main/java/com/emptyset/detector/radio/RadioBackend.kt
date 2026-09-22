package com.emptyset.detector.radio

import android.hardware.usb.UsbDevice
import com.emptyset.detector.detect.WifiBand

enum class RadioKind { T2U_PLUS, PAU0A, PAU0B, UNKNOWN }

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
        fun onChannel(channel: Int, band: WifiBand = WifiBand.infer(channel))
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
    const val ZEROCD_PID = 0x1A2B
    const val MEDIATEK_VID = 0x0E8D
    const val MT7610U_PID = 0x7610
    const val RALINK_VID = 0x148F

    private val t2uIds = setOf(
        0x2357 to 0x0120,
        0x2357 to 0x011E,
        0x2357 to 0x0122
    )

    private val pau0aIds = setOf(
        0x0E8D to 0x7610,
        0x148F to 0x7610
    )

    fun isT2u(device: UsbDevice): Boolean =
        t2uIds.contains(device.vendorId to device.productId)

    fun isZeroCd(device: UsbDevice): Boolean =
        device.vendorId == REALTEK_VID && device.productId == ZEROCD_PID

    fun isPau0Family(device: UsbDevice): Boolean {
        val name = device.productName.orEmpty().uppercase()
        if (name.contains("PAU0A") || name.contains("PAU0B")) return true
        return pau0aIds.contains(device.vendorId to device.productId)
    }

    fun isPau0a(device: UsbDevice): Boolean = isPau0Family(device)

    fun matches(kind: RadioKind, device: UsbDevice?): Boolean {
        if (device == null) return false
        return when (kind) {
            RadioKind.T2U_PLUS -> isT2u(device)
            RadioKind.PAU0A, RadioKind.PAU0B -> isPau0Family(device)
            RadioKind.UNKNOWN -> false
        }
    }

    fun classify(device: UsbDevice): RadioKind = when {
        isT2u(device) -> RadioKind.T2U_PLUS
        isPau0Family(device) -> pau0KindFromName(device)
        else -> RadioKind.UNKNOWN
    }

    private fun pau0KindFromName(device: UsbDevice): RadioKind {
        val name = device.productName.orEmpty().uppercase()
        return when {
            name.contains("PAU0B") -> RadioKind.PAU0B
            name.contains("PAU0A") -> RadioKind.PAU0A
            else -> RadioKind.PAU0A
        }
    }
}
