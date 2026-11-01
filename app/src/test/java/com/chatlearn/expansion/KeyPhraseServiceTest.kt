package com.chatlearn.expansion

import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class KeyPhraseServiceTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private val ai = FakeAiService()

    @Before fun setUp() { db = inMemoryDb(); repo = EntryRepository(db) }
    @After fun tearDown() { db.close() }

    private suspend fun save(text: String, lang: Lang = Lang.EN, alt: String? = null) =
        (repo.saveEntry(EntryDraft(Direction.RECEIVED, lang, text, "한", "", null, emptyList(), alt), 1) as SaveResult.Saved).id

    private fun phrase(id: Long, lang: Lang) = db.dao().let { runBlocking { it.allTexts() } }.first { it.entryId == id && it.lang == lang }.keyPhrase

    @Test fun fillsEveryForeignTextThatHasNoKeyPhraseYet() = runBlocking {
        val a = save("See you tomorrow morning", alt = "Nos vemos mañana por la mañana")
        ai.keyPhrases = { items, _ -> Result.success(items.map { if (it.startsWith("See")) "See you" else "mañana" }) }
        val r = KeyPhraseService(repo, ai).fillAll()
        assertEquals(2, r.filled)
        assertEquals("See you", phrase(a, Lang.EN))
        assertEquals("mañana", phrase(a, Lang.ES))
        assertNull(phrase(a, Lang.KO))
    }

    @Test fun aPhraseThatIsNotInTheSentenceIsStoredAsNoneAndNotRetried() = runBlocking {
        val a = save("See you soon")
        ai.keyPhrases = { items, _ -> Result.success(items.map { "something else" }) }
        val r = KeyPhraseService(repo, ai).fillAll()
        assertEquals(0, r.filled); assertEquals(1, r.none)
        assertEquals("", phrase(a, Lang.EN))
        val again = KeyPhraseService(repo, ai).fillAll()
        assertEquals(0, again.filled + again.none) // 다시 묻지 않는다
    }

    @Test fun aFailedBatchLeavesTextsForNextTime() = runBlocking {
        val a = save("See you soon")
        ai.keyPhrases = { _, _ -> Result.failure(AiException(AiError.OVERLOADED)) }
        val r = KeyPhraseService(repo, ai).fillAll()
        assertEquals(1, r.failedBatches)
        assertNull(phrase(a, Lang.EN))
    }

    @Test fun batchesAreSplitByLanguageAndSize() = runBlocking {
        repeat(3) { save("sentence number $it") }
        save("hola amigo", Lang.ES)
        KeyPhraseService(repo, ai, batchSize = 2).fillAll()
        assertEquals(3, ai.calls.count { it == "keyphrases" }) // EN 2+1, ES 1
    }
}
