package com.chatlearn.widget

import com.chatlearn.ai.FakeAiService
import com.chatlearn.data.EntryDraft
import com.chatlearn.data.EntryRepository
import com.chatlearn.data.inMemoryDb
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import com.chatlearn.review.ReviewSession
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class WidgetDeckTest {
    private val now = 1_790_000_000_000L
    private val repo = EntryRepository(inMemoryDb())
    private val session = ReviewSession(repo, FakeAiService(), { now }, { 10 }, dailyGoal = { 10 })

    private suspend fun save(en: String, ko: String) =
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, en, ko, "", null, emptyList()), now - 1, expand = false)

    @Test fun showsTheKoreanMeaningAndHidesTheSentenceToRecall() = runBlocking {
        save("See you soon", "곧 봐")
        val card = nextWidgetCard(session)!!
        assertEquals("곧 봐", card.prompt)
        assertEquals("See you soon", card.answer)
        assertEquals(Lang.EN, card.answerLang)
        assertEquals(1, card.left)
    }

    @Test fun ratingSavesTheReviewAndMovesToTheNextCard() = runBlocking {
        save("See you soon", "곧 봐"); save("Thanks a lot", "정말 고마워")
        val first = nextWidgetCard(session)!!
        assertTrue(rateWidgetCard(session, first.cardId, Rating.GOOD))
        val second = nextWidgetCard(session)!!
        assertTrue(second.cardId != first.cardId)
        assertEquals(1, second.left)
        assertEquals(1, session.todayProgress().done)
    }

    @Test fun aCardAlreadyReviewedInTheAppIsNotRatedTwice() = runBlocking {
        save("See you soon", "곧 봐")
        val card = nextWidgetCard(session)!!
        assertTrue(rateWidgetCard(session, card.cardId, Rating.GOOD))
        assertFalse(rateWidgetCard(session, card.cardId, Rating.AGAIN))
        assertNull(nextWidgetCard(session))
    }
}
