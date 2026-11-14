package com.chatlearn.ui.theme

import com.chatlearn.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * 안내판 글자: 앱에 넣은 Roboto Condensed(OFL). 시간·편(EN/ES)·상태·큰 숫자에만 쓴다.
 * 기기 글꼴(sans-serif-condensed)은 제조사마다 달라 직접 넣었다. 한글은 시스템 글꼴로 대신 그려진다.
 */
val Condensed = FontFamily(
    Font(R.font.roboto_condensed_regular, FontWeight.Normal),
    Font(R.font.roboto_condensed_medium, FontWeight.Medium),
    Font(R.font.roboto_condensed_bold, FontWeight.Bold),
)

// 숫자는 자릿수가 바뀌어도 흔들리지 않게 폭이 같은 숫자(tnum).
private const val TABULAR = "tnum"

/** 안내판 표기: 응축체, 폭이 같은 숫자, 살짝 벌린 자간. 라틴 글자는 부르는 쪽에서 대문자로 넘긴다. */
fun TextStyle.board(): TextStyle = copy(fontFamily = Condensed, fontFeatureSettings = TABULAR, letterSpacing = 0.04.em)

// 화면 글자는 시스템 산세리프 한 가족(한글 가독성), 큰 숫자만 안내판 응축체. 크기 단계는 몇 개만.
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 76.sp, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 50.sp, fontFeatureSettings = TABULAR),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    // 평가 버튼 아래 "3일" 같은 보조 글자: 기본 11sp는 작아 12sp로 올린다.
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR),
)
