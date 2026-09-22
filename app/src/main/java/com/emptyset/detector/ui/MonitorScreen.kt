package com.emptyset.detector.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emptyset.detector.MonitorUiState
import com.emptyset.detector.R

@Composable
fun MonitorScreen(
    state: MonitorUiState,
    recordedCount: Int,
    alertTitle: String,
    onToggle: () -> Unit,
    onSilence: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenRadio: () -> Unit,
    onLogoClick: () -> Unit,
    onExportLog: () -> Unit
) {
    val flash by animateColorAsState(
        if (state.attacking) Alert else Ink,
        label = "flash"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(flash)
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("EMPTY SET", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("deauthentication monitor", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                IconButton(
                    onClick = onLogoClick,
                    modifier = Modifier.size(48.dp)
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = "Menu",
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            StatusCard(state)
            Spacer(Modifier.height(16.dp))
            if (state.attacking) {
                AlertBanner(title = alertTitle, state = state, onSilence = onSilence)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (state.running) Color(0xFF1B2A20) else Phosphor,
                    contentColor = if (state.running) Phosphor else Ink
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    when {
                        state.running && state.attacking -> "STOP RECORDING"
                        state.running -> "STOP MONITOR"
                        else -> "START MONITOR"
                    },
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenHistory,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Phosphor),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("RECORDS  ($recordedCount)", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenRadio,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Phosphor),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("RADIO", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("EVENT LOG", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                Button(
                    onClick = onExportLog,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Phosphor),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("SAVE TXT", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Panel, RoundedCornerShape(4.dp))
                    .border(1.dp, Line, RoundedCornerShape(4.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.events) { line ->
                    Text(line, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Receive-only. Use on networks you own or are authorized to monitor.",
                color = Dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun AlertBanner(title: String, state: MonitorUiState, onSilence: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2A0C0C), RoundedCornerShape(4.dp))
            .border(1.dp, Alert, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(title.uppercase(), color = Alert, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (state.soundSilenced) {
            Text(
                "Sound silenced. Still recording.",
                color = Phosphor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        if (!state.soundSilenced) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onSilence,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Alert, contentColor = Ink),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("SILENCE SOUND", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatusCard(state: MonitorUiState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(4.dp))
            .border(1.dp, Line, RoundedCornerShape(4.dp))
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(state.radioTitle, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            Text(
                if (state.attacking) "ALERT" else if (state.alertHeld) "SEEN" else if (state.running) "LIVE" else "IDLE",
                color = if (state.attacking || state.alertHeld) Alert else Phosphor,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
        Text(state.radioDetail, color = Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Text(state.status, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric("CHANNEL", state.channel?.toString() ?: "--")
            Metric("FRAMES", state.packetsPerSweep.toString())
            Metric("BAND", state.band?.label ?: com.emptyset.detector.detect.Ieee80211.bandOf(state.channel))
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.Start) {
        Text(label, color = Dim, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        Text(value, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
