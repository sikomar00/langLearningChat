package com.chatlearn.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KeyPhraseTest {
    private val s = "Lo pensé por un momento que me dirías eso"

    @Test fun exactPhraseIsKept() = assertEquals("por un momento", normalizeKeyPhrase(s, "por un momento"))
    @Test fun matchIsCaseInsensitiveAndReturnsTheSentencesOwnCasing() = assertEquals("por un momento", normalizeKeyPhrase(s, "POR UN MOMENTO"))
    @Test fun surroundingSpacesAreTrimmed() = assertEquals("por un momento", normalizeKeyPhrase(s, "  por un momento "))
    @Test fun aPhraseNotInTheSentenceIsRejected() = assertNull(normalizeKeyPhrase(s, "de repente"))
    @Test fun blankIsRejected() { assertNull(normalizeKeyPhrase(s, "   ")); assertNull(normalizeKeyPhrase(s, null)) }
    @Test fun theWholeSentenceIsNotAUsefulBlank() = assertNull(normalizeKeyPhrase(s, s))
    @Test fun trailingPunctuationFromTheModelIsIgnored() = assertEquals("por un momento", normalizeKeyPhrase(s, "por un momento."))
}
