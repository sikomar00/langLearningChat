package com.chatlearn.review

import com.chatlearn.data.ReviewCardEntity

/** 오늘 복습한 카드 수([done])와 하루 목표([goal]). 목표 0은 끔. */
data class GoalProgress(val done: Int, val goal: Int) {
    val enabled: Boolean get() = goal > 0
    val reached: Boolean get() = enabled && done >= goal
    val fraction: Float get() = if (goal <= 0) 0f else (done.toFloat() / goal).coerceIn(0f, 1f)
}

const val LEECH_LAPSES = 3

/** 여러 번 틀려서 따로 챙겨 줄 만한 카드. */
fun isLeech(card: ReviewCardEntity): Boolean = card.lapses >= LEECH_LAPSES
