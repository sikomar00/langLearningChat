package com.chatlearn.domain

const val MAX_STAGE = 5

/** 복습 간격(일) → 익힌 단계 1~5. */
fun stageOf(intervalDays: Double): Int = when {
    intervalDays < 1 -> 1
    intervalDays < 3 -> 2
    intervalDays < 7 -> 3
    intervalDays < 21 -> 4
    else -> MAX_STAGE
}

/** 익힌 단계 0~5의 이름. 라이브러리 필터·문장 상세·성장이 같은 이름을 쓴다. */
fun stageName(stage: Int): String = when {
    stage <= 0 -> "새 문장"
    stage <= 2 -> "익히는 중"
    stage < MAX_STAGE -> "거의"
    else -> "익힘"
}

fun langLabel(l: Lang): String = when (l) { Lang.KO -> "한국어"; Lang.EN -> "영어"; Lang.ES -> "스페인어" }
