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

// surfaceContainer 단계까지 모두 정해 둔다. 비워 두면 M3 기본색(보라 기운)이 섞인다.
// 주 버튼(primary)은 먹색, tertiary는 형광펜, error는 빨간 펜. 선택 표시(secondaryContainer)는 먹으로 진하게(종이와 대비).
internal val LightScheme = lightColorScheme(
    primary = Ink, onPrimary = Paper,
    primaryContainer = Color(0xFFE9E2D5), onPrimaryContainer = Ink,
    secondary = Pencil, onSecondary = Paper,
    secondaryContainer = Color(0xFF3A3732), onSecondaryContainer = Paper,
    tertiary = Highlighter, onTertiary = Color.White,
    tertiaryContainer = HighlighterContainer, onTertiaryContainer = OnHighlighterContainer,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFEDE7DC), onSurfaceVariant = Pencil,
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFF5F1E8),
    surfaceContainer = PaperDeep,
    surfaceContainerHigh = Color(0xFFEAE4D8),
    surfaceContainerHighest = Color(0xFFE4DDD0),
    outline = Color(0xFF8A847A), outlineVariant = Rule,
    inverseSurface = Color(0xFF2E2C29), inverseOnSurface = Paper,
    error = RedPen, onError = Color.White,
    errorContainer = RedPenContainer, onErrorContainer = OnRedPenContainer,
)

internal val DarkScheme = darkColorScheme(
    primary = Chalk, onPrimary = Carbon,
    primaryContainer = Color(0xFF34302B), onPrimaryContainer = Chalk,
    secondary = ChalkSoft, onSecondary = Carbon,
    secondaryContainer = Chalk, onSecondaryContainer = Carbon,
    tertiary = HighlighterLight, onTertiary = Color(0xFF2A1B00),
    tertiaryContainer = HighlighterDeep, onTertiaryContainer = HighlighterContainer,
    background = Carbon, onBackground = Chalk,
    surface = Carbon, onSurface = Chalk,
    surfaceVariant = Color(0xFF2D2A27), onSurfaceVariant = ChalkSoft,
    surfaceContainerLowest = Color(0xFF121110),
    surfaceContainerLow = Color(0xFF1E1C1A),
    surfaceContainer = Color(0xFF23211E),
    surfaceContainerHigh = Color(0xFF2D2A27),
    surfaceContainerHighest = Color(0xFF383430),
    outline = Color(0xFF8F897F), outlineVariant = CarbonRule,
    inverseSurface = Chalk, inverseOnSurface = Carbon,
    error = RedPenLight, onError = OnRedPenLight,
    errorContainer = RedPenDeep, onErrorContainer = Color(0xFFFFDBCC),
)

/** M3 스킴에 없는 의미 색: 성공(기억함)과 빨간 펜 도장(목표 달성) 색. */
data class StatusColors(val success: Color, val accent: Color = RedPen, val onAccent: Color = Color.White)

private val LocalStatusColors = staticCompositionLocalOf { StatusColors(Leaf) }

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
    val status = if (darkTheme) StatusColors(success = LeafLight, accent = RedPenLight, onAccent = OnRedPenLight) else StatusColors(success = Leaf)
    CompositionLocalProvider(LocalStatusColors provides status) {
        // Expressive 모션 스킴: 버튼·칩·내비게이션 등 컴포넌트의 상태 전환이 스프링 물리로 움직인다.
        androidx.compose.material3.MaterialExpressiveTheme(
            colorScheme = scheme, motionScheme = androidx.compose.material3.MotionScheme.expressive(),
            typography = AppTypography, shapes = AppShapes, content = content,
        )
    }
}
