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
import com.emptyset.detector.radio.RadioOption

@Composable
fun RadioScreen(
    options: List<RadioOption>,
    selectedId: String,
    onSelect: (String) -> Unit,
    band24: Boolean,
    band5: Boolean,
    band6: Boolean,
    onBand24: (Boolean) -> Unit,
    onBand5: (Boolean) -> Unit,
    onBand6: (Boolean) -> Unit,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("RADIO", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("select the adapter Empty Set should use", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Phosphor),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("BACK", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(18.dp))
        Text("AVAILABLE ADAPTERS", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        options.forEach { option ->
            RadioRow(
                option = option,
                selected = option.id == selectedId,
                onClick = { onSelect(option.id) }
            )
        }
        if (options.isEmpty()) {
            Text(
                "No adapters listed yet.",
                color = Dim,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
        Spacer(Modifier.height(18.dp))
        Text("SCAN BANDS", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        BandRow("2.4 GHz", band24) { onBand24(!band24) }
        BandRow("5 GHz", band5) { onBand5(!band5) }
        BandRow("6 GHz", band6) { onBand6(!band6) }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenSetup,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Phosphor),
            shape = RoundedCornerShape(4.dp)
        ) {
            Text("SETUP NOTES", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RadioRow(option: RadioOption, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(if (selected) Line else Panel, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) Phosphor else Line, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(option.title, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            Text(option.detail, color = Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        }
        Text(
            when {
                selected -> "ON"
                option.backendReady -> ""
                else -> "LATER"
            },
            color = Phosphor,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun BandRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(if (selected) Line else Panel, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) Phosphor else Line, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(
            if (selected) "ON" else "OFF",
            color = Phosphor,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )
    }
}
