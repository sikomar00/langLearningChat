package com.chatlearn.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.chatlearn.R

/** 배우는 외국어 문장에만 쓰는 세리프 Literata(OFL, 라틴 글자). 한글은 이 서체에 없어 시스템 글꼴로 대신 그려진다. */
val Literata = FontFamily(
    Font(R.font.literata_regular, FontWeight.Normal),
    Font(R.font.literata_semibold, FontWeight.SemiBold),
)

/** 외국어 문장: 화면 글자(고딕)와 구분해 문장이 주인공이 되게 한다. */
fun TextStyle.sentence(): TextStyle = copy(fontFamily = Literata)

// 숫자는 자릿수가 바뀌어도 흔들리지 않게 폭이 같은 숫자(tnum).
private const val TABULAR = "tnum"

// 화면 글자는 시스템 글꼴(한글 가독성). 본문 16sp, 행간 1.5배 안팎.
val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 80.sp, lineHeight = 84.sp, letterSpacing = (-2).sp, fontFeatureSettings = TABULAR),
    displayMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-1).sp, fontFeatureSettings = TABULAR),
    displaySmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TABULAR),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    // 평가 버튼 아래 "3일" 같은 보조 글자: 기본 11sp는 작아 12sp로 올린다.
    labelSmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TABULAR),
)
