package com.chatlearn.domain

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DraftLangTest {
    @Test fun spanishWordsOrAccentsMeanSpanish() {
        assertEquals(Lang.ES, guessDraftLang("Yo fui a la playa ayer"))
        assertEquals(Lang.ES, guessDraftLang("¿Mañana?"))
    }

    @Test fun englishWordsMeanEnglish() = assertEquals(Lang.EN, guessDraftLang("I goed to the beach yesterday"))

    @Test fun noCluesLeavesItToTheLastChoice() = assertNull(guessDraftLang("Ok ok"))
}
