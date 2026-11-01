package com.chatlearn.domain

private const val MIN_MS = 60_000L
private const val HOUR_MS = 60 * MIN_MS
private const val DAY_MS = 24 * HOUR_MS

/** 복습 간격을 "10분", "3일"처럼 짧게 표시한다. */
fun intervalLabel(ms: Long): String = when {
    ms < HOUR_MS -> "${maxOf(1L, Math.round(ms.toDouble() / MIN_MS))}분"
    ms < DAY_MS -> "${Math.round(ms.toDouble() / HOUR_MS)}시간"
    ms < 30 * DAY_MS -> "${Math.round(ms.toDouble() / DAY_MS)}일"
    else -> "${Math.round(ms.toDouble() / (30 * DAY_MS))}개월"
}

/** 각 평가를 누르면 다음 복습이 얼마 뒤인지(지터 없이). 평가 버튼 아래에 보여 준다. */
fun ratingPreviews(card: CardState, now: Long): Map<Rating, String> =
    Rating.entries.associateWith { intervalLabel(Scheduler.next(card, it, now).dueAt - now) }
