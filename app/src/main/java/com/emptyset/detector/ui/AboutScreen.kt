package com.emptyset.detector.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("ABOUT", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Empty Set", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
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
        Text(
            "Receive-only deauthentication monitor for authorized networks. It never transmits and never injects packets.",
            color = Phosphor,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(16.dp))
        Text("SOURCE", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        LinkLine("https://github.com/Undrcrrnt") {
            openUrl(context, "https://github.com/Undrcrrnt")
        }
        Spacer(Modifier.height(6.dp))
        LinkLine("https://github.com/Undrcrrnt/Empty-Set") {
            openUrl(context, "https://github.com/Undrcrrnt/Empty-Set")
        }
        Spacer(Modifier.height(18.dp))
        Text("CYBER KILL CHAIN", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        KillChainGroup("PREPARATION", listOf(
            "1  Reconnaissance" to "Harvest email addresses, conference information, and other targeting data.",
            "2  Weaponization" to "Couple an exploit with a backdoor into a deliverable payload."
        ))
        KillChainGroup("INTRUSION", listOf(
            "3  Delivery" to "Deliver the weaponized bundle to the victim via email, web, USB, or wireless.",
            "4  Exploitation" to "Exploit a vulnerability to execute code on the victim system.",
            "5  Installation" to "Install malware on the asset."
        ))
        KillChainGroup("ACTIVE BREACH", listOf(
            "6  Command and Control" to "Open a channel for remote manipulation of the victim system.",
            "7  Actions on Objectives" to "Hands-on-keyboard access to accomplish the intruder's goal."
        ))
    }
}

@Composable
private fun LinkLine(url: String, onClick: () -> Unit) {
    Text(
        url,
        color = Phosphor,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}

@Composable
private fun KillChainGroup(phase: String, steps: List<Pair<String, String>>) {
    Text(
        phase,
        color = Phosphor,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
    steps.forEach { (title, body) ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .background(Panel, RoundedCornerShape(4.dp))
                .border(1.dp, Line, RoundedCornerShape(4.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(body, color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
    Spacer(Modifier.height(8.dp))
}

private fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
