package com.chatlearn.ui.library

import com.chatlearn.data.EntryEntity
import com.chatlearn.data.LibraryItem
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryFilterTest {
    private fun item(id: Long, chat: Lang, en: String, ko: String, tags: List<String> = emptyList()) =
        LibraryItem(EntryEntity(id, 0, Direction.RECEIVED, chat, "", null), mapOf(chat to en, Lang.KO to ko), tags)

    private val a = item(1, Lang.EN, "How are you?", "어떻게 지내?", listOf("Alex"))
    private val b = item(2, Lang.ES, "¿Cómo estás?", "어떻게 지내?", listOf("Sofía"))
    private val c = item(3, Lang.EN, "See you soon", "곧 보자")

    @Test fun noFilterReturnsAll() = assertEquals(listOf(a, b, c), filterLibrary(listOf(a, b, c), "", null, null))
    @Test fun queryMatchesAnyLanguageCaseInsensitive() {
        assertEquals(listOf(a), filterLibrary(listOf(a, b, c), "HOW", null, null))
        assertEquals(listOf(b), filterLibrary(listOf(a, b, c), "cómo", null, null))
        assertEquals(listOf(c), filterLibrary(listOf(a, b, c), "곧", null, null))
    }
    @Test fun queryAlsoFindsFriendNames() = assertEquals(listOf(b), filterLibrary(listOf(a, b, c), "sof", null, null))
    @Test fun langFilterUsesChatLang() = assertEquals(listOf(b), filterLibrary(listOf(a, b, c), "", Lang.ES, null))
    @Test fun tagFilter() = assertEquals(listOf(a), filterLibrary(listOf(a, b, c), "", null, "Alex"))
    @Test fun filtersCombine() = assertEquals(emptyList(), filterLibrary(listOf(a, b, c), "see", null, "Alex"))
    @Test fun blankQueryWithSpacesIsIgnored() = assertEquals(3, filterLibrary(listOf(a, b, c), "   ", null, null).size)
}
