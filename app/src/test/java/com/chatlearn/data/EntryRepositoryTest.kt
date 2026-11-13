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

    private fun draft(text: String = "How are you?", ko: String = "어떻게 지내?", tags: List<String> = emptyList(), alt: String? = null) =
        EntryDraft(Direction.RECEIVED, Lang.EN, text, ko, "안부 인사", null, tags, alt)

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

    // 오늘 이미 한도보다 많이 소개했어도(한도를 낮췄거나 이미 더 배웠을 때) "더 배우기"는 요청한 장 수만큼 낸다.
    @Test fun extraNewCardsAreAddedOnTopOfWhatIsLeftEvenWhenTheLimitWasExceeded() = runBlocking {
        repeat(5) { repo.saveEntry(draft(text = "msg $it"), now) }
        repeat(3) {
            val c = repo.queue(now, 0, 10).first()
            repo.applyReview(c.card, Scheduler.next(c.card.toState(), Rating.GOOD, now), logFor(c.card, now))
        }
        assertEquals(0, repo.queue(now, 0, 1).size)
        assertEquals(2, repo.queue(now, 0, 1, extraNew = 5).size)
    }

    @Test fun applyReviewUpdatesCardAndWritesLog() = runBlocking {
        repo.saveEntry(draft(), now)
        val due = repo.queue(now, 0, 10).single()
        val state = Scheduler.next(due.card.toState(), Rating.GOOD, now)
        repo.applyReview(due.card, state, logFor(due.card, now))
        assertTrue(repo.queue(now, 0, 10).isEmpty())
        assertEquals(1, db.dao().allLogs().size)
        assertEquals(now + 3 * 86_400_000L, db.dao().allCards().single().dueAt)
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

    @Test fun savingWithAltTextCreatesTheOtherLanguageTextAndCard() = runBlocking {
        val id = (repo.saveEntry(draft(alt = "¿Cómo estás?"), now) as SaveResult.Saved).id
        val cards = db.dao().allCards().filter { it.entryId == id }
        assertEquals(setOf(Lang.EN, Lang.ES), cards.map { it.answerLang }.toSet())
        val item = repo.observeLibrary().first().single()
        assertEquals("¿Cómo estás?", item.texts[Lang.ES])
        assertEquals(TextOrigin.AI, item.origins[Lang.ES])
        assertEquals(2, repo.queue(now, 0, 10).size)
    }

    @Test fun expansionOffOrBlankAltTextCreatesOnlyTheOriginalCard() = runBlocking {
        repo.saveEntry(draft(text = "a", alt = "x"), now, expand = false)
        repo.saveEntry(draft(text = "b", alt = "   "), now)
        repo.saveEntry(draft(text = "c", alt = null), now)
        assertEquals(3, db.dao().allCards().size)
        assertTrue(db.dao().allTexts().none { it.lang == Lang.ES })
    }

    @Test fun expansionTargetsListOnlyEntriesWithoutTheOtherCard() = runBlocking {
        repo.saveEntry(draft(text = "a", alt = "x"), now)
        val b = (repo.saveEntry(draft(text = "b"), now) as SaveResult.Saved).id
        assertEquals(listOf(b), repo.expansionTargets().map { it.entryId })
        assertEquals("b", repo.expansionTargets().single().chatText)
    }

    @Test fun addExpansionCreatesTextAndCardOnlyOnce() = runBlocking {
        val id = (repo.saveEntry(draft(), now) as SaveResult.Saved).id
        assertTrue(repo.addExpansion(id, "¿Cómo estás?", now))
        assertFalse(repo.addExpansion(id, "otra", now)) // 이미 있음: 중복 카드 없음
        assertEquals(2, db.dao().allCards().size)
        assertEquals("¿Cómo estás?", repo.observeLibrary().first().single().texts[Lang.ES])
    }

    @Test fun addExpansionWithBlankTextDoesNothing() = runBlocking {
        val id = (repo.saveEntry(draft(), now) as SaveResult.Saved).id
        assertFalse(repo.addExpansion(id, "  ", now))
        assertFalse(repo.addExpansion(id, null, now))
        assertEquals(1, db.dao().allCards().size)
    }

    @Test fun addExpansionReusesAnAlreadyStoredOtherLanguageText() = runBlocking {
        val id = (repo.saveEntry(draft(), now) as SaveResult.Saved).id
        repo.putText(id, Lang.ES, "ya existe", TextOrigin.USER)
        assertTrue(repo.addExpansion(id, null, now))
        assertEquals("ya existe", repo.observeLibrary().first().single().texts[Lang.ES])
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

    @Test fun cardsForSelectedEntriesIgnoreDueDateAndFilterByLanguage() = runBlocking {
        val a = (repo.saveEntry(draft("Alpha one", "하나", alt = "Alfa uno"), 1_000) as SaveResult.Saved).id
        val b = (repo.saveEntry(draft("Beta two", "둘", alt = "Beta dos"), 1_000) as SaveResult.Saved).id
        repo.saveEntry(draft("Gamma three", "셋"), 1_000)
        val ids = setOf(a, b)
        val future = db.dao().allCards().map { it.copy(dueAt = 9_999_999_999L, lastReviewedAt = 5) }
        future.forEach { db.dao().updateCard(it) }
        val all = repo.cardsFor(ids, null)
        assertEquals(4, all.size)
        assertTrue(all.all { it.card.entryId in ids })
        assertTrue(all.all { it.texts.isNotEmpty() })
        val es = repo.cardsFor(ids, Lang.ES)
        assertEquals(2, es.size)
        assertTrue(es.all { it.card.answerLang == Lang.ES })
        assertTrue(repo.cardsFor(emptySet(), null).isEmpty())
    }

    @Test fun reviewedSinceIsTrueOnlyAfterALogInTheWindow() = runBlocking {
        val id = (repo.saveEntry(draft(), 1_000) as SaveResult.Saved).id
        assertFalse(repo.reviewedSince(0))
        val due = repo.queue(1_000, 0, 10).first { it.card.entryId == id }
        repo.applyReview(due.card, Scheduler.next(due.card.toState(), Rating.GOOD, 5_000),
            ReviewLogEntity(cardId = due.card.id, reviewedAt = 5_000, promptLang = Lang.KO, userAnswer = "x",
                aiVerdict = Verdict.CORRECT, aiFeedback = null, rating = Rating.GOOD))
        assertTrue(repo.reviewedSince(5_000))
        assertFalse(repo.reviewedSince(5_001))
    }

    @Test fun cardCountCountsAllLanguages() = runBlocking {
        val id = (repo.saveEntry(draft(alt = "Hola"), 1_000) as SaveResult.Saved).id
        assertEquals(2, repo.cardCount(id))
    }

    @Test fun savedKeyPhrasesAreValidatedAgainstTheirOwnText() = runBlocking {
        val id = (repo.saveEntry(
            draft("See you tomorrow", alt = "Nos vemos mañana").copy(keyPhrase = "see you", altKeyPhrase = "no está aquí"), 1_000,
        ) as SaveResult.Saved).id
        val texts = db.dao().allTexts().filter { it.entryId == id }.associateBy { it.lang }
        assertEquals("See you", texts.getValue(Lang.EN).keyPhrase) // 문장의 대소문자로 저장
        assertNull(texts.getValue(Lang.ES).keyPhrase) // 문장에 없으면 버린다
    }

    @Test fun queueCardsCarryTheirKeyPhrases() = runBlocking {
        repo.saveEntry(draft("See you tomorrow").copy(keyPhrase = "See you"), 1_000)
        val due = repo.queue(1_000, 0, 10).single()
        assertEquals("See you", due.keyPhrases[Lang.EN])
    }

    @Test fun editingATextKeepsItsKeyPhraseOnlyWhileItStillFits() = runBlocking {
        val id = (repo.saveEntry(draft("See you tomorrow").copy(keyPhrase = "See you"), 1_000) as SaveResult.Saved).id
        repo.putText(id, Lang.EN, "See you tomorrow morning", TextOrigin.USER)
        assertEquals("See you", db.dao().allTexts().first { it.entryId == id && it.lang == Lang.EN }.keyPhrase)
        repo.putText(id, Lang.EN, "Good night", TextOrigin.USER)
        assertNull(db.dao().allTexts().first { it.entryId == id && it.lang == Lang.EN }.keyPhrase)
    }

    @Test fun setKeyPhraseValidatesAndMarksNoneAsEmpty() = runBlocking {
        val id = (repo.saveEntry(draft("See you tomorrow"), 1_000) as SaveResult.Saved).id
        assertTrue(repo.setKeyPhrase(id, Lang.EN, "tomorrow"))
        assertEquals("tomorrow", db.dao().allTexts().first { it.entryId == id && it.lang == Lang.EN }.keyPhrase)
        assertFalse(repo.setKeyPhrase(id, Lang.EN, "nope"))
        assertEquals("", db.dao().allTexts().first { it.entryId == id && it.lang == Lang.EN }.keyPhrase)
    }

    @Test fun missingKeyPhraseTargetsListOnlyForeignTextsWithNullPhrase() = runBlocking {
        val id = (repo.saveEntry(draft("See you tomorrow", alt = "Nos vemos"), 1_000) as SaveResult.Saved).id
        assertEquals(2, repo.keyPhraseTargets().size)
        repo.setKeyPhrase(id, Lang.EN, "See you")
        assertEquals(listOf(Lang.ES), repo.keyPhraseTargets().map { it.lang })
    }

    @Test fun cardsReviewedSinceCountsDistinctCards() = runBlocking {
        val id = (repo.saveEntry(draft(), 1_000) as SaveResult.Saved).id
        val due = repo.queue(1_000, 0, 10).first { it.card.entryId == id }
        suspend fun log(at: Long) = repo.applyReview(due.card, Scheduler.next(due.card.toState(), Rating.AGAIN, at),
            ReviewLogEntity(cardId = due.card.id, reviewedAt = at, promptLang = Lang.KO, userAnswer = "x",
                aiVerdict = null, aiFeedback = null, rating = Rating.AGAIN))
        log(5_000); log(6_000)
        assertEquals(1, repo.cardsReviewedSince(0))
        assertEquals(0, repo.cardsReviewedSince(7_000))
    }

    @Test fun dueCardCarriesTheEntryNote() = runBlocking {
        repo.saveEntry(draft(), 1_000)
        assertEquals("안부 인사", repo.queue(1_000, 0, 10).single().note)
    }

    @Test fun knownExpressionsListSavedPhrasesWithTheirKoreanMeaningNewestFirst() = runBlocking {
        repo.saveEntry(draft("See you tomorrow", "내일 봐").copy(keyPhrase = "See you"), 1_000)
        repo.saveEntry(draft("It is up to you", "네가 정해").copy(keyPhrase = "up to you"), 2_000)
        repo.saveEntry(draft("No phrase here", "표현 없음"), 3_000)
        repo.saveEntry(draft("Blank phrase", "빈 표현"), 4_000)
        val ids = db.dao().allTexts().filter { it.lang == Lang.EN && it.text == "Blank phrase" }
        repo.setKeyPhrase(ids.single().entryId, Lang.EN, "")
        val known = repo.knownExpressions(Lang.EN)
        assertEquals(listOf("up to you" to "네가 정해", "See you" to "내일 봐"), known.map { it.phrase to it.ko })
    }

    @Test fun knownExpressionsAreLimitedAndPerLanguage() = runBlocking {
        repeat(5) { repo.saveEntry(draft("Sentence number $it", "뜻 $it").copy(keyPhrase = "number $it"), 1_000L + it) }
        assertEquals(3, repo.knownExpressions(Lang.EN, limit = 3).size)
        assertTrue(repo.knownExpressions(Lang.ES).isEmpty())
    }
}
