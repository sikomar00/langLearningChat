package com.chatlearn.expansion

import com.chatlearn.ai.*
import com.chatlearn.data.*
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
class ExpansionServiceTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private val ai = FakeAiService()
    private fun service(batch: Int = 10) = ExpansionService(repo, ai, { 5_000L }, batch)

    @Before fun setUp() { db = inMemoryDb(); repo = EntryRepository(db) }
    @After fun tearDown() { db.close() }

    private suspend fun save(text: String, lang: Lang = Lang.EN) =
        (repo.saveEntry(EntryDraft(Direction.RECEIVED, lang, text, "한", "", null, emptyList()), 1) as SaveResult.Saved).id

    @Test fun expandsEveryEntryWithoutAnOtherLanguageCard() = runBlocking {
        save("one"); save("two"); save("hola", Lang.ES)
        val r = service().expandAll()
        assertEquals(3, r.created)
        assertEquals(6, db.dao().allCards().size)
        val items = repo.observeLibrary().first().associate { it.texts[it.entry.chatLang]!! to it }
        assertEquals("[번역] one", items["one"]!!.texts[Lang.ES])
        assertEquals("[번역] hola", items["hola"]!!.texts[Lang.EN])
    }

    @Test fun runningTwiceCreatesNoDuplicates() = runBlocking {
        save("one"); save("two")
        service().expandAll()
        val again = service().expandAll()
        assertEquals(0, again.created)
        assertEquals(4, db.dao().allCards().size)
    }

    @Test fun sentencesAreSentInBatchesGroupedByLanguage() = runBlocking {
        repeat(25) { save("en$it") }
        save("es0", Lang.ES)
        service(batch = 10).expandAll()
        assertEquals(4, ai.calls.count { it == "batch" }) // EN 10+10+5, ES 1
    }

    @Test fun aFailedBatchIsSkippedAndTheRestContinue() = runBlocking {
        repeat(12) { save("en$it") }
        var n = 0
        ai.batch = { items, _, _ -> if (n++ == 0) Result.failure(AiException(AiError.RATE_LIMIT)) else Result.success(items.map { "[t] $it" }) }
        val r = service(batch = 10).expandAll()
        assertEquals(1, r.failedBatches)
        assertEquals(2, r.created) // 두 번째 묶음(2개)만 성공
        assertEquals(10, repo.expansionTargets().size) // 실패한 10개는 다음에 다시 시도할 수 있다
    }

    @Test fun blankTranslationsAreSkippedNotSaved() = runBlocking {
        save("one"); save("two")
        ai.batch = { items, _, _ -> Result.success(items.map { if (it == "one") "  " else "dos" }) }
        val r = service().expandAll()
        assertEquals(1, r.created); assertEquals(1, r.skipped)
    }

    @Test fun expandOneTranslatesOnlyThatEntry() = runBlocking {
        val a = save("one"); save("two")
        assertTrue(service().expandOne(a))
        assertEquals(1, repo.expansionTargets().size)
        assertFalse(service().expandOne(a)) // 이미 확장됨
    }

    @Test fun expandOneReportsFailureWithoutChangingAnything() = runBlocking {
        val a = save("one")
        ai.translate = { _, _, _ -> Result.failure(AiException(AiError.OFFLINE)) }
        assertFalse(service().expandOne(a))
        assertEquals(1, db.dao().allCards().size)
    }
}
