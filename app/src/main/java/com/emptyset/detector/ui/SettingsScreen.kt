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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emptyset.detector.alert.AlertSettings
import com.emptyset.detector.alert.AlertSoundMode

@Composable
fun SettingsScreen(
    settings: AlertSettings,
    revision: Int,
    onBack: () -> Unit,
    onPickRingtone: () -> Unit,
    onPickFile: () -> Unit,
    onMode: (AlertSoundMode) -> Unit
) {
    var selectedMode by remember(revision) { mutableStateOf(settings.soundMode) }
    var currentLabel by remember(revision) { mutableStateOf(settings.label()) }
    var message by remember(revision) { mutableStateOf(settings.lockScreenMessageInput) }
    var sender by remember(revision) { mutableStateOf(settings.senderNameInput) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("ALERTS", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        NightButton("BACK", onBack)
        Spacer(Modifier.height(18.dp))
        FieldLabel("LOCK SCREEN MESSAGE")
        NightField(
            value = message,
            onValueChange = {
                message = it
                settings.lockScreenMessageInput = it
            },
            placeholder = AlertSettings.DEFAULT_MESSAGE
        )
        Spacer(Modifier.height(14.dp))
        FieldLabel("NOTIFICATION APP NAME")
        NightField(
            value = sender,
            onValueChange = {
                sender = it
                settings.senderNameInput = it
            },
            placeholder = AlertSettings.DEFAULT_SENDER
        )
        Text(
            "Name shown as the app sending the lock-screen notification.",
            color = Dim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 6.dp)
        )
        Spacer(Modifier.height(18.dp))
        FieldLabel("SOUND")
        Spacer(Modifier.height(8.dp))
        ModeRow("Built-in tone", AlertSoundMode.BUNDLED, selectedMode) { mode ->
            selectedMode = mode
            onMode(mode)
            currentLabel = settings.label()
        }
        ModeRow("Phone ringtone", AlertSoundMode.RINGTONE, selectedMode) { mode ->
            selectedMode = mode
            onMode(mode)
            currentLabel = settings.label()
        }
        ModeRow("Audio file", AlertSoundMode.CUSTOM, selectedMode) { mode ->
            selectedMode = mode
            onMode(mode)
            currentLabel = settings.label()
        }
        ModeRow("Vibration only", AlertSoundMode.VIBRATE_ONLY, selectedMode) { mode ->
            selectedMode = mode
            onMode(mode)
            currentLabel = settings.label()
        }
        Spacer(Modifier.height(12.dp))
        if (selectedMode == AlertSoundMode.RINGTONE) {
            NightButton("CHOOSE RINGTONE", onPickRingtone)
            Spacer(Modifier.height(8.dp))
        }
        if (selectedMode == AlertSoundMode.CUSTOM) {
            NightButton("CHOOSE AUDIO FILE", onPickFile)
            Spacer(Modifier.height(8.dp))
        }
        Text("Current: $currentLabel", color = Dim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Android may also ask for full-screen notifications so the alert can break through the lock screen. Allow that for Empty Set.",
            color = Dim,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = Dim, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun NightField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = TextStyle(
            color = Phosphor,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        ),
        placeholder = {
            Text(placeholder, color = Dim, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        shape = RoundedCornerShape(4.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Phosphor,
            unfocusedTextColor = Phosphor,
            cursorColor = Phosphor,
            focusedBorderColor = Phosphor,
            unfocusedBorderColor = Line,
            focusedContainerColor = Panel,
            unfocusedContainerColor = Panel,
            focusedPlaceholderColor = Dim,
            unfocusedPlaceholderColor = Dim
        )
    )
}

@Composable
private fun ModeRow(
    label: String,
    mode: AlertSoundMode,
    selectedMode: AlertSoundMode,
    onMode: (AlertSoundMode) -> Unit
) {
    val selected = selectedMode == mode
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .background(if (selected) Line else Panel, RoundedCornerShape(4.dp))
            .border(1.dp, if (selected) Phosphor else Line, RoundedCornerShape(4.dp))
            .clickable { onMode(mode) }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
        Text(if (selected) "ON" else "", color = Phosphor, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
    }
}

@Composable
private fun NightButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Panel, contentColor = Phosphor),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(label, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}
