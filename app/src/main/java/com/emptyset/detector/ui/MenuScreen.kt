package com.emptyset.detector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
fun MenuScreen(
    onBack: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenAbout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(20.dp)
    ) {
        Text("MENU", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("alerts  ·  setup  ·  about", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        MenuButton("BACK", onBack)
        Spacer(Modifier.height(18.dp))
        MenuButton("ALERTS", onOpenAlerts)
        Spacer(Modifier.height(8.dp))
        MenuButton("SETUP", onOpenSetup)
        Spacer(Modifier.height(8.dp))
        MenuButton("ABOUT", onOpenAbout)
    }
}

@Composable
private fun MenuButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Phosphor),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}
