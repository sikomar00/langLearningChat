package com.chatlearn.review

import com.chatlearn.domain.Lang
import kotlin.test.Test
import kotlin.test.assertTrue

class SpecialCharsTest {
    @Test fun spanishOffersAccentsAndInvertedMarks() {
        val c = specialChars(Lang.ES)
        assertTrue(c.containsAll(listOf("á", "é", "í", "ó", "ú", "ñ", "¿", "¡")))
    }

    @Test fun englishAndKoreanNeedNone() {
        assertTrue(specialChars(Lang.EN).isEmpty())
        assertTrue(specialChars(Lang.KO).isEmpty())
    }
}
