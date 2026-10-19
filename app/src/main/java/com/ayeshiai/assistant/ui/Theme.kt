package com.ayeshiai.assistant.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun AyeshiTheme(dark: Boolean, accent: Color, content: @Composable () -> Unit) {
    val scheme = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            secondary = Color(0xFF7C4DFF),
            background = Color(0xFF05070F),
            onBackground = Color(0xFFE6EDF7),
            surface = Color(0xFF0B1226),
            onSurface = Color(0xFFE6EDF7),
            surfaceVariant = Color(0xFF111A33),
            onSurfaceVariant = Color(0xFFB8C4DA),
            error = Color(0xFFFF5252)
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color.Black,
            secondary = Color(0xFF7C4DFF),
            background = Color(0xFFF4F7FC),
            onBackground = Color(0xFF0B1226),
            surface = Color.White,
            onSurface = Color(0xFF0B1226),
            surfaceVariant = Color(0xFFE3EAF7),
            onSurfaceVariant = Color(0xFF34415C),
            error = Color(0xFFD32F2F)
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
