package com.chatlearn.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Purple40, onPrimary = Color.White,
    primaryContainer = Purple90, onPrimaryContainer = Purple10,
    secondary = Slate40, onSecondary = Color.White,
    secondaryContainer = Slate90, onSecondaryContainer = Purple10,
    background = LightBackground, onBackground = LightOnSurface,
    surface = LightBackground, onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant, onSurfaceVariant = Slate40,
    outline = LightOutline,
    error = ErrorLight, onError = Color.White,
    errorContainer = ErrorContainerLight, onErrorContainer = Color(0xFF410002),
)

private val DarkScheme = darkColorScheme(
    primary = Purple80, onPrimary = Purple20,
    primaryContainer = Purple30, onPrimaryContainer = Purple90,
    secondary = Slate80, onSecondary = Slate30,
    secondaryContainer = Slate30, onSecondaryContainer = Slate90,
    background = DarkBackground, onBackground = DarkOnSurface,
    surface = DarkBackground, onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant, onSurfaceVariant = Slate80,
    outline = DarkOutline,
    error = ErrorDark, onError = Color(0xFF690005),
    errorContainer = ErrorContainerDark, onErrorContainer = Color(0xFFFFDAD6),
)

/** M3 스킴에 없는 의미 색(성공). */
data class StatusColors(val success: Color)

private val LocalStatusColors = staticCompositionLocalOf { StatusColors(SuccessLight) }

val MaterialTheme.statusColors: StatusColors
    @Composable get() = LocalStatusColors.current

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (darkTheme) DarkScheme else LightScheme
    val status = StatusColors(success = if (darkTheme) SuccessDark else SuccessLight)
    CompositionLocalProvider(LocalStatusColors provides status) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
