package com.chatlearn.ui.translate

import kotlin.test.Test
import kotlin.test.assertEquals

class TagSuggestTest {
    private val recent = listOf("Alex", "Sofía", "여행", "약속")

    @Test fun emptyInputShowsAllInRecentOrder() =
        assertEquals(recent, suggestTags(recent, ""))

    @Test fun limitsTheNumberOfSuggestions() =
        assertEquals(listOf("Alex", "Sofía"), suggestTags(recent, "", limit = 2))

    @Test fun filtersByTypedTextIgnoringCase() {
        assertEquals(listOf("Sofía"), suggestTags(recent, "so"))
        assertEquals(listOf("Alex"), suggestTags(recent, "ALE"))
        assertEquals(listOf("여행"), suggestTags(recent, "여"))
    }

    @Test fun onlyTheSegmentAfterTheLastCommaIsTheQuery() {
        assertEquals(listOf("Sofía", "여행", "약속"), suggestTags(recent, "Alex, "))
        assertEquals(listOf("Sofía"), suggestTags(recent, "Alex, s"))
    }

    @Test fun alreadyChosenTagsAreNotSuggestedAgain() =
        assertEquals(emptyList(), suggestTags(listOf("Alex"), "Alex, "))

    @Test fun applyReplacesTheCurrentSegmentAndAddsSeparator() {
        assertEquals("Alex, ", applyTagSuggestion("", "Alex"))
        assertEquals("Alex, ", applyTagSuggestion("Al", "Alex"))
        assertEquals("Alex, Sofía, ", applyTagSuggestion("Alex, s", "Sofía"))
    }

    @Test fun parseTagsSplitsTrimsAndDropsEmptyAndDuplicates() {
        assertEquals(listOf("Alex", "약속"), parseTags("Alex, , 약속 ,Alex"))
        assertEquals(emptyList(), parseTags("  , "))
    }
}
