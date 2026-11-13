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

// 카드·내비게이션이 쓰는 surfaceContainer 단계까지 모두 정해 둔다. 비워 두면 M3 기본 회색이 섞여 브랜드 색과 따로 논다.
internal val LightScheme = lightColorScheme(
    primary = Grape, onPrimary = Color.White,
    primaryContainer = GrapeContainer, onPrimaryContainer = OnGrapeContainer,
    secondary = MintInk, onSecondary = Color.White,
    secondaryContainer = MintContainer, onSecondaryContainer = OnMintContainer,
    tertiary = SunInk, onTertiary = Color.White,
    tertiaryContainer = SunContainer, onTertiaryContainer = OnSunContainer,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFE7DFF5), onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F1FE),
    surfaceContainer = Color(0xFFF1EBFC),
    surfaceContainerHigh = Color(0xFFEBE4F8),
    surfaceContainerHighest = Color(0xFFE5DDF4),
    outline = Color(0xFF7B7392), outlineVariant = Color(0xFFCFC6E0),
    error = Coral, onError = Color.White,
    errorContainer = CoralContainer, onErrorContainer = OnCoralContainer,
)

internal val DarkScheme = darkColorScheme(
    primary = GrapeLight, onPrimary = OnGrapeLight,
    primaryContainer = GrapeDeep, onPrimaryContainer = GrapeContainer,
    secondary = MintLight, onSecondary = OnMintLight,
    secondaryContainer = MintDeep, onSecondaryContainer = MintContainer,
    tertiary = SunLight, onTertiary = OnSunLight,
    tertiaryContainer = SunDeep, onTertiaryContainer = SunContainer,
    background = Night, onBackground = NightInk,
    surface = Night, onSurface = NightInk,
    surfaceVariant = Color(0xFF3A3150), onSurfaceVariant = NightInkSoft,
    surfaceContainerLowest = Color(0xFF0F0B1C),
    surfaceContainerLow = Color(0xFF1C1630),
    surfaceContainer = Color(0xFF211A36),
    surfaceContainerHigh = Color(0xFF2B2342),
    surfaceContainerHighest = Color(0xFF362D4E),
    outline = Color(0xFF958DA9), outlineVariant = Color(0xFF4A4160),
    error = CoralLight, onError = OnCoralLight,
    errorContainer = CoralDeep, onErrorContainer = CoralContainer,
)

/** M3 스킴에 없는 의미 색: 성공(기억함)과 축하·연속 기록용 햇살 채우기. */
data class StatusColors(val success: Color, val sun: Color = SunFill, val onSun: Color = OnSunFill)

private val LocalStatusColors = staticCompositionLocalOf { StatusColors(MintInk) }

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
    val status = if (darkTheme) StatusColors(success = MintLight, sun = SunLight, onSun = OnSunLight) else StatusColors(success = MintInk)
    CompositionLocalProvider(LocalStatusColors provides status) {
        // Expressive 모션 스킴: 버튼·칩·내비게이션 등 컴포넌트의 상태 전환이 스프링 물리로 움직인다.
        androidx.compose.material3.MaterialExpressiveTheme(
            colorScheme = scheme, motionScheme = androidx.compose.material3.MotionScheme.expressive(),
            typography = AppTypography, shapes = AppShapes, content = content,
        )
    }
}
