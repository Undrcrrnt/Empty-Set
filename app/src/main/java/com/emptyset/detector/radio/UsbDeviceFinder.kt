package com.emptyset.detector.radio

import android.content.Context
import android.hardware.usb.UsbManager

object UsbDeviceFinder {
    fun attached(context: Context): List<RadioInfo> {
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usb.deviceList.values.map { device ->
            when (UsbIds.classify(device)) {
                RadioKind.T2U_PLUS -> RadioInfo(
                    kind = RadioKind.T2U_PLUS,
                    title = "TP-Link T2U Plus",
                    detail = "RTL8821AU receive-only  %04x:%04x".format(device.vendorId, device.productId),
                    device = device,
                    canCapture = NativeRx.available()
                )
                RadioKind.UNKNOWN -> RadioInfo(
                    kind = RadioKind.UNKNOWN,
                    title = device.productName ?: "USB device",
                    detail = "%04x:%04x".format(device.vendorId, device.productId),
                    device = device,
                    canCapture = false
                )
            }
        }
    }
}
