package com.example.smartcity.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Snow,
    onPrimary = Night,
    secondary = Snow,
    onSecondary = Night,
    background = Night,
    onBackground = Snow,
    surface = Graphite,
    onSurface = Snow
)


@Composable
fun SmartCityTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colors = DarkColors

    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content
    )
}
