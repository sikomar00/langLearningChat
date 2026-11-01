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

    @Test fun recentTagsAreOrderedByMostRecentUse() = runBlocking {
        repo.saveEntry(draft(text = "a", tags = listOf("Alex", "여행")), 1)
        repo.saveEntry(draft(text = "b", tags = listOf("Sofía")), 2)
        repo.saveEntry(draft(text = "c", tags = listOf("Alex")), 3)
        assertEquals(listOf("Alex", "Sofía", "여행"), repo.observeRecentTags().first())
    }

    private suspend fun tagsOf(id: Long) = repo.observeLibrary().first().single { it.entry.id == id }.tags

    @Test fun setTagsAddsRemovesAndReplaces() = runBlocking {
        val id = (repo.saveEntry(draft(tags = listOf("Alex", "여행")), now) as SaveResult.Saved).id
        repo.setTags(id, listOf("Alex", "약속"), now)
        assertEquals(listOf("Alex", "약속"), tagsOf(id))
        repo.setTags(id, emptyList(), now)
        assertEquals(emptyList(), tagsOf(id))
    }

    @Test fun setTagsIgnoresBlankAndDuplicateNames() = runBlocking {
        val id = (repo.saveEntry(draft(), now) as SaveResult.Saved).id
        repo.setTags(id, listOf(" Alex ", "", "Alex", "  "), now)
        assertEquals(listOf("Alex"), tagsOf(id))
    }

    @Test fun tagsNoLongerUsedByAnyEntryAreDeleted() = runBlocking {
        val a = (repo.saveEntry(draft(text = "a", tags = listOf("Alex", "여행")), now) as SaveResult.Saved).id
        repo.saveEntry(draft(text = "b", tags = listOf("Alex")), now + 1)
        repo.setTags(a, listOf("Alex"), now) // 여행은 아무 문장에도 안 남는다
        assertEquals(listOf("Alex"), repo.observeRecentTags().first())
        assertEquals(1, db.dao().allTags().size)
    }

    @Test fun renameTagChangesItOnEveryEntry() = runBlocking {
        val a = (repo.saveEntry(draft(text = "a", tags = listOf("Alex")), now) as SaveResult.Saved).id
        val b = (repo.saveEntry(draft(text = "b", tags = listOf("Alex", "여행")), now + 1) as SaveResult.Saved).id
        assertEquals(RenameResult.Renamed, repo.renameTag("Alex", "알렉스"))
        assertEquals(listOf("알렉스"), tagsOf(a))
        assertEquals(listOf("알렉스", "여행"), tagsOf(b).sorted())
    }

    @Test fun renamingToAnExistingTagMergesThem() = runBlocking {
        val a = (repo.saveEntry(draft(text = "a", tags = listOf("Alex")), now) as SaveResult.Saved).id
        val b = (repo.saveEntry(draft(text = "b", tags = listOf("알렉스")), now + 1) as SaveResult.Saved).id
        val both = (repo.saveEntry(draft(text = "c", tags = listOf("Alex", "알렉스")), now + 2) as SaveResult.Saved).id
        assertEquals(RenameResult.Merged, repo.renameTag("Alex", "알렉스"))
        assertEquals(listOf("알렉스"), tagsOf(a))
        assertEquals(listOf("알렉스"), tagsOf(b))
        assertEquals(listOf("알렉스"), tagsOf(both)) // 둘 다 붙어 있던 문장도 중복 없이 하나
        assertEquals(1, db.dao().allTags().size)
    }

    @Test fun renameWithBlankOrSameNameChangesNothing() = runBlocking {
        val id = (repo.saveEntry(draft(tags = listOf("Alex")), now) as SaveResult.Saved).id
        assertEquals(RenameResult.Unchanged, repo.renameTag("Alex", "  "))
        assertEquals(RenameResult.Unchanged, repo.renameTag("Alex", "Alex"))
        assertEquals(RenameResult.Unchanged, repo.renameTag("없는태그", "새이름"))
        assertEquals(listOf("Alex"), tagsOf(id))
    }

    @Test fun savingWithATagBumpsItsLastUsedTime() = runBlocking {
        repo.saveEntry(draft(text = "a", tags = listOf("Alex")), 1)
        repo.saveEntry(draft(text = "b", tags = listOf("Sofía")), 2)
        assertEquals(listOf("Sofía", "Alex"), repo.observeRecentTags().first())
        repo.saveEntry(draft(text = "c", tags = listOf("Alex")), 3)
        assertEquals(listOf("Alex", "Sofía"), repo.observeRecentTags().first())
    }

    @Test fun editingTagsOnAnOldSentenceMakesThemMostRecent() = runBlocking {
        val a = (repo.saveEntry(draft(text = "a", tags = listOf("Alex")), 1) as SaveResult.Saved).id
        repo.saveEntry(draft(text = "b", tags = listOf("Sofía")), 2)
        repo.setTags(a, listOf("Alex", "여행"), now = 10)
        // 둘 다 10: 나중에 만든 태그가 먼저
        assertEquals(listOf("여행", "Alex", "Sofía"), repo.observeRecentTags().first())
    }

    @Test fun mergingTagsKeepsTheNewerLastUsedTime() = runBlocking {
        repo.saveEntry(draft(text = "a", tags = listOf("Alex")), 5)
        repo.saveEntry(draft(text = "b", tags = listOf("알렉스")), 1)
        repo.renameTag("Alex", "알렉스")
        assertEquals(5L, db.dao().allTags().single().lastUsedAt)
    }

    @Test fun noTagsMeansNoSuggestions() = runBlocking {
        repo.saveEntry(draft(), 1)
        assertTrue(repo.observeRecentTags().first().isEmpty())
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
