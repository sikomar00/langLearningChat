package com.chatlearn.ui.translate

import java.time.LocalDate
import java.time.ZoneId

/**
 * 번역 화면에서 고른 "알게 된 날"을 저장 시각(밀리초)으로 바꾼다.
 * 오늘이거나 아직 안 골랐으면 null(저장하는 지금 시각을 쓴다). 미래 날짜는 오늘로 본다.
 * 지난 날짜는 그날 낮 12시로 해서 시간대 경계에서 날짜가 하루 어긋나지 않게 한다.
 */
fun learnedAtMillis(date: LocalDate?, today: LocalDate, zone: ZoneId): Long? {
    if (date == null || !date.isBefore(today)) return null
    return date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}

/** 번역 화면 칩에 보일 날짜: 오늘, 어제, 올해면 "10월 28일", 다른 해면 연도까지. */
fun learnedLabel(date: LocalDate?, today: LocalDate): String = when {
    date == null || !date.isBefore(today) -> "오늘"
    date == today.minusDays(1) -> "어제"
    date.year == today.year -> "${date.monthValue}월 ${date.dayOfMonth}일"
    else -> "${date.year}년 ${date.monthValue}월 ${date.dayOfMonth}일"
}
