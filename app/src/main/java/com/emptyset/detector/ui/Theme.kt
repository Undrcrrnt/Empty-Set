package com.emptyset.detector.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF050807)
internal val Phosphor = Color(0xFF7CFF9A)
internal val Dim = Color(0xFF8AA394)
internal val Alert = Color(0xFFFF4D4D)
internal val Panel = Color(0xFF0B1210)
internal val Line = Color(0xFF1C3324)

private val NightColors = darkColorScheme(
    primary = Phosphor,
    onPrimary = Ink,
    secondary = Dim,
    onSecondary = Ink,
    background = Ink,
    onBackground = Phosphor,
    surface = Panel,
    onSurface = Phosphor,
    error = Alert,
    onError = Ink
)

@Composable
fun EmptySetTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NightColors, content = content)
}
