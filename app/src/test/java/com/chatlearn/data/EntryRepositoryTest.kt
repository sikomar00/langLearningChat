package com.chatlearn.data

import com.chatlearn.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class EntryRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private val now = 1_000_000L

    @Before fun setUp() { db = inMemoryDb(); repo = EntryRepository(db) }
    @After fun tearDown() { db.close() }

    private fun draft(text: String = "How are you?", ko: String = "어떻게 지내?", tags: List<String> = emptyList()) =
        EntryDraft(Direction.RECEIVED, Lang.EN, text, ko, "안부 인사", null, tags)

    @Test fun saveCreatesEntryTextsAndDueCard() = runBlocking {
        val r = repo.saveEntry(draft(tags = listOf("Alex")), now)
        assertIs<SaveResult.Saved>(r)
        val item = repo.observeLibrary().first().single()
        assertEquals("How are you?", item.texts[Lang.EN])
        assertEquals("어떻게 지내?", item.texts[Lang.KO])
        assertEquals(listOf("Alex"), item.tags)
        val q = repo.queue(now, dayStart = 0, newPerDay = 10)
        assertEquals(1, q.size)
        assertEquals(Lang.EN, q[0].card.answerLang)
    }

    @Test fun duplicateSavedTextIsRejectedWithoutNewCard() = runBlocking {
        val first = repo.saveEntry(draft(), now) as SaveResult.Saved
        val second = repo.saveEntry(draft(text = "  How are you?  "), now + 1)
        assertEquals(SaveResult.Duplicate(first.id), second)
        assertEquals(1, repo.observeLibrary().first().size)
        assertEquals(1, repo.queue(now + 1, 0, 10).size)
    }

    @Test fun newCardLimitCountsCardsIntroducedToday() = runBlocking {
        repeat(3) { repo.saveEntry(draft(text = "msg $it"), now) }
        assertEquals(2, repo.queue(now, dayStart = 0, newPerDay = 2).size)
        val first = repo.queue(now, 0, 2).first()
        repo.applyReview(first.card, Scheduler.next(first.card.toState(), Rating.GOOD, now), logFor(first.card, now))
        // 오늘 1장을 소개했으므로 남은 새 카드 한도는 1장
        assertEquals(1, repo.queue(now, dayStart = 0, newPerDay = 2).size)
    }

    @Test fun applyReviewUpdatesCardAndWritesLog() = runBlocking {
        repo.saveEntry(draft(), now)
        val due = repo.queue(now, 0, 10).single()
        val state = Scheduler.next(due.card.toState(), Rating.GOOD, now)
        repo.applyReview(due.card, state, logFor(due.card, now))
        assertTrue(repo.queue(now, 0, 10).isEmpty())
        assertEquals(1, db.dao().allLogs().size)
        assertEquals(now + 86_400_000L, db.dao().allCards().single().dueAt)
    }

    @Test fun deletingEntryCascadesToCardsAndTexts() = runBlocking {
        val id = (repo.saveEntry(draft(), now) as SaveResult.Saved).id
        repo.deleteEntry(id)
        assertTrue(db.dao().allCards().isEmpty())
        assertTrue(db.dao().allTexts().isEmpty())
    }

    @Test fun interleaveSpreadsNewCardsAmongReviews() {
        assertEquals(listOf(1, 2, 9, 3, 4), interleave(listOf(1, 2, 3, 4), listOf(9)))
        assertEquals(listOf(9), interleave(emptyList(), listOf(9)))
        assertEquals(listOf(1, 9, 8), interleave(listOf(1), listOf(9, 8)))
    }

    private fun logFor(card: ReviewCardEntity, at: Long) =
        ReviewLogEntity(cardId = card.id, reviewedAt = at, promptLang = Lang.KO, userAnswer = "x",
            aiVerdict = null, aiFeedback = null, rating = Rating.GOOD)
}
