package com.chatlearn.review.exercise

import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.domain.ExerciseKind

object ExercisePolicy {
    const val MATURE_DAYS = com.chatlearn.domain.Scheduler.MATURE_DAYS
    const val EARLY_CLOZE_SHARE = 0.5   // reps 1~2: 빈칸 채우기 비율
    const val MID_TRANSLATE_SHARE = 0.6 // reps 3+: 쓰기 번역 비율
    const val MATURE_TRANSLATE_SHARE = 0.5

    /** [roll]은 0 이상 1 미만의 난수. 같은 입력과 roll이면 같은 결과다. */
    fun choose(card: ReviewCardEntity, canBlank: Boolean, ttsAvailable: Boolean, roll: Double): ExerciseKind {
        val wanted = when {
            card.lastReviewedAt == null || card.reps == 0 -> ExerciseKind.CLOZE
            card.intervalDays >= MATURE_DAYS ->
                if (roll < MATURE_TRANSLATE_SHARE) ExerciseKind.TRANSLATE else ExerciseKind.DICTATION
            card.reps >= 3 ->
                if (roll < MID_TRANSLATE_SHARE) ExerciseKind.TRANSLATE else ExerciseKind.DICTATION
            else -> if (roll < EARLY_CLOZE_SHARE) ExerciseKind.CLOZE else ExerciseKind.TRANSLATE
        }
        return when {
            wanted == ExerciseKind.CLOZE && !canBlank -> ExerciseKind.TRANSLATE
            wanted == ExerciseKind.DICTATION && !ttsAvailable -> ExerciseKind.TRANSLATE
            else -> wanted
        }
    }
}
