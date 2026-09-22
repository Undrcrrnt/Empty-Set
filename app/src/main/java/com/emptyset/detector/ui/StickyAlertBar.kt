package com.emptyset.detector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StickyAlertBar(
    title: String,
    detail: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFB71C1C))
            .clickable(onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            title.uppercase(),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        if (detail.isNotBlank()) {
            Text(
                detail,
                color = Color(0xFFFFCDD2),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp
            )
        }
        Text(
            "TAP TO DISMISS",
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
