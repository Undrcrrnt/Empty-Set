package com.emptyset.detector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emptyset.detector.log.CaptureAttempt
import com.emptyset.detector.log.CaptureLog
import com.emptyset.detector.log.CapturedFrame

@Composable
fun HistoryScreen(
    attempts: List<CaptureAttempt>,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(20.dp)
    ) {
        Text("RECORDS", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("passive deauthentication log", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("BACK", onBack, Modifier.weight(1f))
            SmallButton("JSON", onExportJson, Modifier.weight(1f))
            SmallButton("CSV", onExportCsv, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        SmallButton("CLEAR LOG", onClear, Modifier.fillMaxWidth(), alert = true)
        Spacer(Modifier.height(14.dp))
        if (attempts.isEmpty()) {
            Text(
                "No attempts recorded yet. Start the monitor with a T2U Plus attached. Each attack is saved as a Deauth Notification file in Downloads and in the app Documents/recordings folder.",
                color = Dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(attempts, key = { it.id }) { attempt ->
                    AttemptRow(
                        attempt = attempt,
                        onClick = { onOpen(attempt.id) },
                        onDelete = { onDelete(attempt.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun AttemptDetailScreen(
    attempt: CaptureAttempt?,
    frames: List<CapturedFrame>,
    onBack: () -> Unit,
    onExportJson: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(20.dp)
    ) {
        Text("DEAUTH NOTIFICATION", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("BACK", onBack, Modifier.weight(1f))
            SmallButton("JSON", onExportJson, Modifier.weight(1f))
            SmallButton("CSV", onExportCsv, Modifier.weight(1f))
        }
        if (attempt != null) {
            Spacer(Modifier.height(8.dp))
            SmallButton("DELETE RECORD", onDelete, Modifier.fillMaxWidth(), alert = true)
        }
        Spacer(Modifier.height(14.dp))
        if (attempt == null) {
            Text("Attempt not found.", color = Dim, fontFamily = FontFamily.Monospace)
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Panel, RoundedCornerShape(4.dp))
                    .border(1.dp, Line, RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Text(CaptureLog.displayTime(attempt.startedAtMs), color = Phosphor, fontFamily = FontFamily.Monospace)
                Text("${attempt.frameCount} frames   ${(attempt.durationMs / 1000)}s", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text("ch ${attempt.channels.sorted().joinToString(",")}", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(attempt.bands.joinToString(" / "), color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(frames, key = { it.id }) { frame ->
                    FrameRow(frame)
                }
            }
        }
    }
}

@Composable
private fun AttemptRow(attempt: CaptureAttempt, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(4.dp))
            .border(1.dp, Line, RoundedCornerShape(4.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(12.dp)
        ) {
            Text("Deauth Notification", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(CaptureLog.displayTime(attempt.startedAtMs), color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text(
                "${attempt.frameCount} frames  ${attempt.bands.joinToString("/")}  ch ${attempt.channels.sorted().joinToString(",")}",
                color = Dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
            Text(
                "src ${attempt.sources.firstOrNull() ?: "--"}",
                color = Phosphor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        SmallButton("DEL", onDelete, Modifier.padding(end = 8.dp), alert = true)
    }
}

@Composable
private fun FrameRow(frame: CapturedFrame) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(4.dp))
            .border(1.dp, Line, RoundedCornerShape(4.dp))
            .padding(12.dp)
    ) {
        Text(
            "${frame.kind.uppercase()}  ch ${frame.channel ?: "--"}  ${frame.band}",
            color = Alert,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
        Text(CaptureLog.displayTime(frame.epochMs), color = Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("dst ${frame.destination}", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("src ${frame.source}", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text("bss ${frame.bssid}", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(
            "reason ${frame.reasonCode ?: "--"}  ${frame.reasonName}",
            color = Dim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
        frame.rssiDbm?.let {
            Text("rssi ${it} dBm", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SmallButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alert: Boolean = false
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (alert) Alert else Panel,
            contentColor = if (alert) Ink else Phosphor
        ),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}
