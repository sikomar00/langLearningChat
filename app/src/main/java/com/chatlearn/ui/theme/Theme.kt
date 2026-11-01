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

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 배경화면 기반 색은 Android 12(API 31) 이상에서만 지원된다.
    val useDynamic = dynamicColor && android.os.Build.VERSION.SDK_INT >= 31
    val scheme: ColorScheme = when {
        useDynamic && darkTheme -> androidx.compose.material3.dynamicDarkColorScheme(ctx)
        useDynamic -> androidx.compose.material3.dynamicLightColorScheme(ctx)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val status = StatusColors(success = if (darkTheme) SuccessDark else SuccessLight)
    CompositionLocalProvider(LocalStatusColors provides status) {
        // Expressive 모션 스킴: 버튼·칩·내비게이션 등 컴포넌트의 상태 전환이 스프링 물리로 움직인다.
        androidx.compose.material3.MaterialExpressiveTheme(
            colorScheme = scheme, motionScheme = androidx.compose.material3.MotionScheme.expressive(),
            typography = AppTypography, shapes = AppShapes, content = content,
        )
    }
}
