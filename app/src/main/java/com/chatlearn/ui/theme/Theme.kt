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
// 주 버튼(primary)은 안내판 검정, 선택 표시(secondaryContainer: 칩·내비게이션)는 플랩 호박,
// tertiary는 읽을 수 있는 짙은 호박(핵심 표현·거의 맞음·폴더), error는 지연 빨강.
internal val LightScheme = lightColorScheme(
    primary = Ink, onPrimary = Stock,
    primaryContainer = Color(0xFFE4E7EB), onPrimaryContainer = Ink,
    secondary = InkSoft, onSecondary = Stock,
    secondaryContainer = Flap, onSecondaryContainer = Ink,
    tertiary = FlapInk, onTertiary = Stock,
    tertiaryContainer = Color(0xFFFFE7A3), onTertiaryContainer = Color(0xFF2B1D00),
    background = Concourse, onBackground = Ink,
    surface = Concourse, onSurface = Ink,
    surfaceVariant = Color(0xFFE4E7EB), onSurfaceVariant = InkSoft,
    surfaceContainerLowest = Stock,
    surfaceContainerLow = Color(0xFFF7F8F9),
    surfaceContainer = Color(0xFFECEEF1),
    surfaceContainerHigh = Color(0xFFE4E7EB),
    surfaceContainerHighest = Color(0xFFDADDE2),
    outline = Color(0xFF7D838C), outlineVariant = Rule,
    // 알림(스낵바) 동작 글자: 검은 판 위 호박. 비워 두면 M3 기본 보라가 나온다.
    inverseSurface = Board, inverseOnSurface = Stock, inversePrimary = Flap,
    error = Delayed, onError = Stock,
    errorContainer = Color(0xFFFCE1DE), onErrorContainer = Color(0xFF410E0A),
)

internal val DarkScheme = darkColorScheme(
    primary = NightInk, onPrimary = Ink,
    primaryContainer = Color(0xFF2A2F37), onPrimaryContainer = NightInk,
    secondary = NightInkSoft, onSecondary = Ink,
    secondaryContainer = Flap, onSecondaryContainer = Ink,
    tertiary = Flap, onTertiary = Ink,
    tertiaryContainer = Color(0xFF4A3500), onTertiaryContainer = Color(0xFFFFE7A3),
    background = NightGround, onBackground = NightInk,
    surface = NightGround, onSurface = NightInk,
    surfaceVariant = Color(0xFF23272E), onSurfaceVariant = NightInkSoft,
    // 줄·탑승권(Lowest)은 바탕보다 한 단 밝게: 밤에도 안내판 검정과 줄이 구분된다.
    surfaceContainerLowest = Color(0xFF1F2329),
    surfaceContainerLow = Color(0xFF1A1D21),
    surfaceContainer = Color(0xFF23272D),
    surfaceContainerHigh = Color(0xFF2A2F36),
    surfaceContainerHighest = Color(0xFF333840),
    outline = Color(0xFF7A808A), outlineVariant = NightRule,
    inverseSurface = NightInk, inverseOnSurface = Ink, inversePrimary = FlapInk,
    error = DelayedLight, onError = Color(0xFF410E0A),
    errorContainer = Color(0xFF6B1F18), onErrorContainer = Color(0xFFFFDAD5),
)

/**
 * M3 스킴에 없는 세계의 색: 맞음(정시 출발 초록), 안내판 검정과 그 위 글자, 플랩 호박(지금 할 일).
 * 안내판·플랩은 배경화면 색(동적 색)을 켜도 바뀌지 않으므로 그 위 글자색([onFlap])도 함께 고정한다.
 * [flapGraphic]은 밝은 바탕 위 호박 그림, [segmentOff]는 불빛 칸의 꺼진 칸.
 */
data class StatusColors(
    val success: Color,
    val board: Color = Board,
    val boardRaised: Color = BoardRaised,
    val onBoard: Color = Stock,
    val onBoardMuted: Color = OnBoardMuted,
    val flap: Color = Flap,
    val onFlap: Color = Ink,
    val flapGraphic: Color = FlapGraphic,
    val segmentOff: Color = Rule,
)

private val LocalStatusColors = staticCompositionLocalOf { StatusColors(OnTime) }

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
    val status = if (darkTheme) StatusColors(success = OnTimeLight, board = NightBoard, flapGraphic = Flap, segmentOff = NightSegmentOff)
    else StatusColors(success = OnTime)
    CompositionLocalProvider(LocalStatusColors provides status) {
        // Expressive 모션 스킴: 버튼·칩·내비게이션 등 컴포넌트의 상태 전환이 스프링 물리로 움직인다.
        androidx.compose.material3.MaterialExpressiveTheme(
            colorScheme = scheme, motionScheme = androidx.compose.material3.MotionScheme.expressive(),
            typography = AppTypography, shapes = AppShapes, content = content,
        )
    }
}
