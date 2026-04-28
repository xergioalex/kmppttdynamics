package com.xergioalex.kmppttdynamics.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.xergioalex.kmppttdynamics.settings.ThemeMode

// PTT brand: clean black-and-white wordmark on a warm background. The
// accent is a muted magenta that recalls the dotted separators in the
// "PER · T · T" lockup.
private val PttPrimary       = Color(0xFF1C1C1E)
private val PttOnPrimary     = Color(0xFFFFFFFF)
private val PttSecondary     = Color(0xFFE0457B) // accent
private val PttOnSecondary   = Color(0xFFFFFFFF)
private val PttBackground    = Color(0xFFFAF9F6)
private val PttOnBackground  = Color(0xFF1C1C1E)
private val PttSurface       = Color(0xFFFFFFFF)
private val PttOnSurface     = Color(0xFF1C1C1E)

private val LightScheme = lightColorScheme(
    primary       = PttPrimary,
    onPrimary     = PttOnPrimary,
    secondary     = PttSecondary,
    onSecondary   = PttOnSecondary,
    background    = PttBackground,
    onBackground  = PttOnBackground,
    surface       = PttSurface,
    onSurface     = PttOnSurface,
)

private val DarkScheme = darkColorScheme(
    primary       = Color(0xFFEDEDED),
    onPrimary     = Color(0xFF111113),
    secondary     = Color(0xFFFF6FA0),
    onSecondary   = Color(0xFF111113),
    background    = Color(0xFF111113),
    onBackground  = Color(0xFFEDEDED),
    surface       = Color(0xFF1A1A1D),
    onSurface     = Color(0xFFEDEDED),
)

@Composable
fun AppTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        content = content,
    )
}
