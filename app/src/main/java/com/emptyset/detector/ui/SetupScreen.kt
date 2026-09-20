package com.emptyset.detector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("SETUP", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("phone + TP-Link T2U Plus", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Phosphor),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("BACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        sections.forEach { (title, body) ->
            Text(title, color = Phosphor, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text(body, color = Dim, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(18.dp))
        }
    }
}

private val sections = listOf(
    "What this app is" to
        "Empty Set is a receive-only deauthentication monitor. It never transmits, never injects packets, and never sends deauth frames. Use it only on networks you own or are authorized to watch.",
    "What you need" to
        "1. An Android phone with USB-C OTG (USB host).\n" +
        "2. A TP-Link Archer T2U Plus (chip RTL8821AU, USB id 2357:0120).\n" +
        "3. A USB-C OTG adapter. If the phone cannot power the dongle, use a powered USB hub.\n" +
        "4. This APK installed, with Notifications and USB permission allowed.",
    "First-run on the phone" to
        "Install the APK. Open Empty Set. Allow notifications. On Android 14+, also allow full-screen / lock-screen alerts so Incoming Deauthentication can appear over the lock screen. Plug in the T2U Plus, accept the USB prompt, then tap Start Monitor.",
    "Driver status" to
        "This APK includes a receive-only RTL8821AU userspace driver (OpenIPC devourer, GPLv2, Jaguar1 only) plus libusb. After you grant USB access, Empty Set wraps the adapter file descriptor, loads firmware, puts the T2U Plus in monitor mode, hops 2.4 and 5 GHz, and feeds deauth/disassoc frames into the parser. No root. Transmit and packet injection are not used.\n\n" +
        "The phone must be 64-bit (arm64). A powered OTG hub helps if the dongle browns out.",
    "If capture does not start" to
        "1. Unplug/replug the T2U and accept the USB permission prompt, then tap Start Monitor again.\n" +
        "2. Use a powered USB hub if the adapter disconnects when firmware loads.\n" +
        "3. If the stick first appears as a flash drive (Realtek ZeroCD, 0bda:1a2b), unplug, wait, replug until it is 2357:0120.\n" +
        "4. This build only ships arm64-v8a native code.",
    "Lock-screen alerts" to
        "Settings → Alerts. Choose built-in tone, a phone ringtone, a custom audio file, or vibration only. Incoming Deauthentication is shown on the lock screen via a high-priority full-screen notification. If the app is already open, it stays usable and the lock-screen overlay is not shown. If the overlay does not appear on a locked phone, enable full-screen notifications for Empty Set in Android system settings.",
    "Recordings" to
        "Each detected attack is written as Deauth Notification plus the date and time. JSON and CSV copies go to Downloads and to Android/data/com.emptyset.detector/files/Documents/recordings. Records inside the app lists the same attempts.",
    "ZeroCD / flash-drive mode" to
        "Some Realtek sticks first appear as a USB drive with a Windows installer. Unplug, wait, replug. If it still is not 2357:0120, a USB mode switch is required before Empty Set can claim it.",
    "License" to
        "Devourer is GNU GPL v2. A build that links it must keep Empty Set GPL and include source. This receive-only app does not include packet injection."
)
