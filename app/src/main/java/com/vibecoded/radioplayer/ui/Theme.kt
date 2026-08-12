package com.vibecoded.radioplayer.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandColor = Color(0xFF1DB954)
private val LightColors = lightColorScheme(primary = BrandColor)
private val DarkColors = darkColorScheme(primary = BrandColor)

@Composable
fun RadioPlayerTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
