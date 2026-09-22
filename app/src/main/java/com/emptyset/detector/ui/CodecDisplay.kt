package com.emptyset.detector.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import com.emptyset.detector.alert.AlertSettings
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

fun channelToDisplayMhz(channel: Int?): Float = when {
    channel == null -> 2412f
    channel <= 14 -> 2407f + channel * 5f
    else -> 5000f + channel * 5f
}

@Composable
fun CodecFrequencyDisplay(
    modifier: Modifier = Modifier,
    channel: Int? = null,
    compact: Boolean = false,
    headline: String = AlertSettings.DEFAULT_MESSAGE,
    senderName: String = AlertSettings.DEFAULT_SENDER
) {
    val targetMhz = channelToDisplayMhz(channel)
    var shown by remember(targetMhz) { mutableStateOf(targetMhz) }
    var locked by remember(targetMhz) { mutableStateOf(false) }
    LaunchedEffect(targetMhz) {
        locked = false
        repeat(24) {
            shown = targetMhz + Random.nextInt(-80, 81)
            delay(45)
        }
        shown = targetMhz
        locked = true
    }

    val motion = rememberInfiniteTransition(label = "codec")
    val scan by motion.animateFloat(
        initialValue = 0f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "scan"
    )
    val noise by motion.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(120, easing = LinearEasing), RepeatMode.Restart),
        label = "noise"
    )
    val blink by motion.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "blink"
    )
    val wave by motion.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
        label = "wave"
    )

    val freqText = "%07.2f".format(shown)
    Box(
        modifier = modifier.background(Color(0xFF020403))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val green = Color(0xFF7CFF9A)
            val dim = Color(0xFF1B3A24)
            val panel = Color(0xFF07140C)
            val pad = w * 0.04f
            val portraitW = w * 0.28f
            val portraitH = h * 0.42f
            val top = h * 0.12f
            val left = Offset(pad, top)
            val right = Offset(w - pad - portraitW, top)

            drawRect(Color(0xFF020403))

            fun portrait(origin: Offset, glyph: String) {
                drawRoundRect(
                    color = dim,
                    topLeft = origin,
                    size = Size(portraitW, portraitH),
                    cornerRadius = CornerRadius(8f, 8f),
                    style = Stroke(width = 3f)
                )
                drawRoundRect(
                    color = panel,
                    topLeft = origin + Offset(3f, 3f),
                    size = Size(portraitW - 6f, portraitH - 6f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                val cx = origin.x + portraitW / 2f
                val cy = origin.y + portraitH / 2f
                if (glyph == "0") {
                    drawCircle(
                        color = green.copy(alpha = 0.85f),
                        radius = portraitW * 0.22f,
                        center = Offset(cx, cy),
                        style = Stroke(width = 8f)
                    )
                } else {
                    drawLine(
                        color = green.copy(alpha = 0.85f),
                        start = Offset(cx, cy - portraitH * 0.18f),
                        end = Offset(cx, cy + portraitH * 0.18f),
                        strokeWidth = 10f,
                        cap = StrokeCap.Round
                    )
                }
            }
            portrait(left, "0")
            portrait(right, "1")

            val midY = top + portraitH / 2f
            val path = Path()
            val startX = left.x + portraitW + 8f
            val endX = right.x - 8f
            path.moveTo(startX, midY)
            val steps = 28
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val x = startX + (endX - startX) * t
                val y = midY + sin(wave + t * 10f) * (h * 0.035f) +
                    sin(noise * 0.07f + t * 18f) * 3f
                path.lineTo(x, y)
            }
            drawPath(path, green.copy(alpha = 0.7f), style = Stroke(width = 2.4f))

            clipRect {
                var y = scan % 4f
                while (y < h) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.35f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.2f
                    )
                    y += 4f
                }
            }
            val rng = Random((noise * 10).toInt())
            repeat(if (compact) 40 else 90) {
                drawCircle(
                    color = green.copy(alpha = rng.nextFloat() * 0.18f),
                    radius = rng.nextFloat() * 1.8f,
                    center = Offset(rng.nextFloat() * w, rng.nextFloat() * h)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (compact) 6.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                headline.uppercase(),
                color = Alert.copy(alpha = blink),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 11.sp else 14.sp
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (compact) 8.dp else 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "FREQ.",
                color = Dim,
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 10.sp else 12.sp
            )
            Text(
                freqText,
                color = Phosphor,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 36.sp else 52.sp
            )
            Text(
                if (locked) "MHz   CH ${channel ?: "--"}" else "TUNING…",
                color = if (locked) Phosphor else Dim,
                fontFamily = FontFamily.Monospace,
                fontSize = if (compact) 11.sp else 14.sp
            )
            Spacer(Modifier.height(4.dp))
            if (!compact) {
                Text(
                    "${senderName.uppercase()}  ·  RECEIVE ONLY",
                    color = Dim,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                )
            }
        }
    }
}
