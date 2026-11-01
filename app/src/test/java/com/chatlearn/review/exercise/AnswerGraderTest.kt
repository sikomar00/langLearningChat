package com.chatlearn.review.exercise

import com.chatlearn.domain.Verdict
import kotlin.test.*

class AnswerGraderTest {
    @Test fun normalizeLowercasesAndDropsPunctuation() {
        assertEquals("how are you", AnswerGrader.normalize("  How are you?! "))
        assertEquals("i'm fine", AnswerGrader.normalize("I’m fine."))
    }

    @Test fun stripAccentsRemovesDiacritics() = assertEquals("como estas", AnswerGrader.stripAccents("cómo estás"))

    @Test fun clozeExactIsCorrectIgnoringCaseAndPunctuation() =
        assertEquals(Verdict.CORRECT, AnswerGrader.gradeCloze(listOf("friend"), listOf(" Friend. ")))

    @Test fun clozeAccentOnlyOrOneLetterOffIsClose() {
        assertEquals(Verdict.CLOSE, AnswerGrader.gradeCloze(listOf("mañana"), listOf("manana")))
        assertEquals(Verdict.CLOSE, AnswerGrader.gradeCloze(listOf("tomorrow"), listOf("tomorow")))
    }

    @Test fun clozeWrongWordOrAnyWrongBlankIsWrong() {
        assertEquals(Verdict.WRONG, AnswerGrader.gradeCloze(listOf("friend"), listOf("enemy")))
        assertEquals(Verdict.WRONG, AnswerGrader.gradeCloze(listOf("early", "traffic"), listOf("early", "weather")))
    }

    @Test fun clozeMixOfCorrectAndCloseIsClose() =
        assertEquals(Verdict.CLOSE, AnswerGrader.gradeCloze(listOf("early", "traffic"), listOf("early", "trafic")))

    @Test fun dictationExactIgnoringCaseAndPunctuationIsCorrect() {
        val r = AnswerGrader.gradeDictation("How are you, my friend?", "how are you my friend")
        assertEquals(Verdict.CORRECT, r.verdict)
        assertTrue(r.marks.all { it.correct })
    }

    @Test fun dictationAccentOnlyDifferenceIsClose() =
        assertEquals(Verdict.CLOSE, AnswerGrader.gradeDictation("Cómo estás", "como estas").verdict)

    @Test fun dictationUpToFifteenPercentWordErrorsIsClose() {
        // 11단어 중 1개 빠짐 = 약 9%
        val ref = "we are going to the park to play with our dog"
        val typed = "we are going to the park to play with dog"
        assertEquals(Verdict.CLOSE, AnswerGrader.gradeDictation(ref, typed).verdict)
    }

    @Test fun dictationOverFifteenPercentIsWrongAndMarksTheBadWord() {
        val r = AnswerGrader.gradeDictation("I like green apples", "I like red apples") // 4단어 중 1개 = 25%
        assertEquals(Verdict.WRONG, r.verdict)
        assertEquals(listOf(true, true, false, true), r.marks.map { it.correct })
    }

    @Test fun dictationExtraWordsCountAsErrors() =
        assertEquals(Verdict.WRONG, AnswerGrader.gradeDictation("see you soon", "see you very soon today").verdict)

    @Test fun dictationEmptyInputIsWrong() =
        assertEquals(Verdict.WRONG, AnswerGrader.gradeDictation("see you soon", "  ").verdict)
}
