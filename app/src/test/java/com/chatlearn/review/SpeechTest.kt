package com.chatlearn.review

import com.chatlearn.domain.Lang
import com.chatlearn.domain.Verdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechTest {
    @Test fun recognizerLanguageFollowsTheAnswerLanguage() {
        assertEquals("en-US", speechLanguageTag(Lang.EN))
        assertEquals("es-ES", speechLanguageTag(Lang.ES))
    }

    @Test fun firstNonBlankResultIsUsed() {
        assertEquals("see you tomorrow", spokenText(listOf("  ", " see you tomorrow ")))
        assertNull(spokenText(emptyList()))
        assertNull(spokenText(null))
    }

    @Test fun spokenWordsFillAnEmptyAnswerOrAreAppended() {
        assertEquals("see you", appendSpoken("  ", "see you"))
        assertEquals("I will see you", appendSpoken("I will ", "see you"))
    }

    @Test fun shadowingIgnoresCaseAndPunctuationLikeDictation() {
        assertEquals(Verdict.CORRECT, shadowingResult("See you tomorrow!", "see you tomorrow").verdict)
        assertEquals(Verdict.WRONG, shadowingResult("See you tomorrow!", "banana").verdict)
    }
}
