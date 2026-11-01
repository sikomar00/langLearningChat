package com.chatlearn.review.exercise

import com.chatlearn.data.ReviewCardEntity
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.domain.Lang
import kotlin.test.Test
import kotlin.test.assertEquals

class ExercisePolicyTest {
    private fun card(reps: Int, interval: Double = 1.0, last: Long? = 1L) =
        ReviewCardEntity(1, 1, Lang.EN, 0, interval, 2.5, reps, 0, last)

    private fun pick(c: ReviewCardEntity, roll: Double = 0.0, blank: Boolean = true, tts: Boolean = true) =
        ExercisePolicy.choose(c, blank, tts, roll)

    @Test fun neverReviewedAndRelearningCardsGetCloze() {
        assertEquals(ExerciseKind.CLOZE, pick(card(0, last = null), roll = 0.99))
        assertEquals(ExerciseKind.CLOZE, pick(card(0), roll = 0.99)) // 다시를 받은 직후
    }

    @Test fun earlyRepsSplitClozeAndTranslateAtHalf() {
        assertEquals(ExerciseKind.CLOZE, pick(card(1), roll = 0.49))
        assertEquals(ExerciseKind.TRANSLATE, pick(card(2), roll = 0.5))
    }

    @Test fun laterRepsSplitTranslateAndDictationAtSixty() {
        assertEquals(ExerciseKind.TRANSLATE, pick(card(3), roll = 0.59))
        assertEquals(ExerciseKind.DICTATION, pick(card(5), roll = 0.6))
    }

    @Test fun matureCardsSplitAtHalfAndBoundaryIs21Days() {
        assertEquals(ExerciseKind.TRANSLATE, pick(card(5, interval = 21.0), roll = 0.49))
        assertEquals(ExerciseKind.DICTATION, pick(card(5, interval = 21.0), roll = 0.5))
        // 20.9일은 아직 익숙함이 아니므로 60/40 구간: 0.5 → 번역
        assertEquals(ExerciseKind.TRANSLATE, pick(card(5, interval = 20.9), roll = 0.5))
    }

    @Test fun clozeFallsBackToTranslateWhenNothingCanBeBlanked() =
        assertEquals(ExerciseKind.TRANSLATE, pick(card(0, last = null), blank = false))

    @Test fun dictationFallsBackToTranslateWithoutTts() =
        assertEquals(ExerciseKind.TRANSLATE, pick(card(5), roll = 0.99, tts = false))
}
