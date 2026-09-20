package com.emptyset.detector.radio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import com.emptyset.detector.detect.ChannelPlan
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.yield
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * USB host path for the TP-Link Archer T2U Plus (RTL8821AU).
 *
 * Opens the Android USB device, hands the file descriptor to the receive-only
 * Jaguar1 userspace driver, hops 2.4/5 GHz, and never transmits.
 */
class TplinkT2uBackend(
    private val context: Context,
    private val device: UsbDevice,
    private val hop24: Boolean = true,
    private val hop5: Boolean = true,
    private val dwellMs: Long = 140
) : RadioBackend {

    override val info: RadioInfo = RadioInfo(
        kind = RadioKind.T2U_PLUS,
        title = "TP-Link T2U Plus",
        detail = "RTL8821AU  %04x:%04x".format(device.vendorId, device.productId),
        device = device,
        canCapture = NativeRx.available()
    )

    @Volatile
    private var running = false
    private var connection: UsbDeviceConnection? = null
    private var deliver: ExecutorService? = null

    override suspend fun start(listener: RadioBackend.Listener) {
        val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
        if (!usb.hasPermission(device)) {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_IMMUTABLE
            }
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(RadioBackend.ACTION_USB_PERMISSION),
                flags
            )
            usb.requestPermission(device, permissionIntent)
            listener.onError("USB permission requested for T2U Plus. Grant it and start again.")
            return
        }
        running = true
        deliver?.shutdownNow()
        val exec = Executors.newSingleThreadExecutor { task ->
            Thread(task, "t2u-rx").apply { isDaemon = true }
        }
        deliver = exec
        val channels = ChannelPlan.hopset(hop24, hop5)
        if (channels.isEmpty()) {
            listener.onError("No channels selected")
            return
        }

        if (!NativeRx.available()) {
            listener.onError(
                "Native RTL8821AU RX library is missing from this APK (64-bit build required)."
            )
            hopOnly(listener, channels)
            return
        }

        val opened = usb.openDevice(device)
        if (opened == null) {
            listener.onError("Could not open T2U Plus USB device")
            running = false
            return
        }
        connection = opened
        val fd = opened.fileDescriptor
        val lockDir = context.cacheDir.absolutePath
        listener.onStatus("Loading RTL8821AU firmware (receive-only)...")
        val sink = object : NativeRx.Sink {
            override fun onFrame(frame: ByteArray, rssi: Int, channel: Int) {
                if (!running) return
                exec.execute {
                    if (!running) return@execute
                    listener.onRawFrame(frame, channel, rssi)
                }
            }

            override fun onReady() {
                exec.execute { listener.onStatus("T2U firmware running, starting monitor RX...") }
            }

            override fun onNativeError(message: String) {
                running = false
                exec.execute { listener.onError(message) }
            }
        }
        val err = runCatching {
            NativeRx.nativeStart(fd, lockDir, channels.first(), sink)
        }.getOrElse { crash ->
            "Driver failed to start: ${crash.message ?: crash.javaClass.simpleName}"
        }
        if (!err.isNullOrEmpty()) {
            runCatching { NativeRx.nativeStop() }
            connection?.close()
            connection = null
            listener.onError(err)
            hopOnly(listener, channels)
            return
        }
        listener.onChannel(channels.first())
        delay(2800)
        if (!running || !currentCoroutineContext().isActive) {
            stopNative()
            return
        }
        listener.onStatus("T2U Plus monitor RX active on 2.4/5 GHz (receive-only)")
        var index = 0
        while (running && currentCoroutineContext().isActive) {
            val ch = channels[index]
            NativeRx.nativeSetChannel(ch)
            listener.onChannel(ch)
            delay(dwellMs)
            index = (index + 1) % channels.size
            yield()
        }
        stopNative()
    }

    private suspend fun hopOnly(listener: RadioBackend.Listener, channels: List<Int>) {
        var index = 0
        while (running && currentCoroutineContext().isActive) {
            listener.onChannel(channels[index])
            delay(dwellMs)
            index = (index + 1) % channels.size
            yield()
        }
    }

    @Synchronized
    private fun stopNative() {
        runCatching { NativeRx.nativeStop() }
        connection?.close()
        connection = null
    }

    override fun stop() {
        running = false
        stopNative()
        deliver?.shutdownNow()
        deliver = null
    }
}
