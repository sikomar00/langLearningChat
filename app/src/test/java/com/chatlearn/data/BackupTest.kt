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
class BackupTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository

    @Before fun setUp() { db = inMemoryDb(); repo = EntryRepository(db) }
    @After fun tearDown() { db.close() }

    private val nasty = "He said \"hi\"\nthen left 😂 \\o/ ¿Qué tal? " + "x".repeat(2000)

    private suspend fun seed() {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, nasty, "한국어\n줄바꿈 🙂", "메모", "casual", listOf("Alex")), 1_000)
        val due = repo.queue(1_000, 0, 10).single()
        repo.applyReview(
            due.card, Scheduler.next(due.card.toState(), Rating.GOOD, 1_000),
            ReviewLogEntity(cardId = due.card.id, reviewedAt = 1_000, promptLang = Lang.KO, userAnswer = nasty,
                aiVerdict = Verdict.CORRECT, aiFeedback = "좋아요", rating = Rating.GOOD),
        )
    }

    @Test fun roundTripPreservesEverythingIncludingNastyText() = runBlocking {
        seed()
        val json = Backup.encode(repo.exportAll())
        repo.importAll(Backup.decode(json))
        val item = repo.observeLibrary().first().single()
        assertEquals(nasty, item.texts[Lang.EN])
        assertEquals("한국어\n줄바꿈 🙂", item.texts[Lang.KO])
        assertEquals(listOf("Alex"), item.tags)
        assertEquals(nasty, db.dao().allLogs().single().userAnswer)
        assertEquals(1, db.dao().allCards().single().reps)
    }

    @Test fun importReplacesExistingData() = runBlocking {
        seed()
        val snapshot = repo.exportAll()
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.ES, "Hola", "안녕", "", null, emptyList()), 2_000)
        repo.importAll(snapshot)
        assertEquals(1, repo.observeLibrary().first().size)
    }

    @Test fun corruptJsonThrowsAndKeepsExistingData() = runBlocking {
        seed()
        assertFailsWith<BackupFormatException> { Backup.decode("{ not json") }
        assertFailsWith<BackupFormatException> { Backup.decode("""{"version":1}""") }
        assertEquals(1, repo.observeLibrary().first().size)
    }

    @Test fun newerVersionIsRejected() {
        val d = BackupData(version = 99, entries = emptyList(), texts = emptyList(), cards = emptyList(),
            logs = emptyList(), tags = emptyList(), entryTags = emptyList())
        assertFailsWith<BackupFormatException> { Backup.decode(Backup.encode(d)) }
    }

    @Test fun failedImportRollsBack() = runBlocking {
        seed()
        val good = repo.exportAll()
        // 존재하지 않는 entryId를 가리키는 텍스트 → 외래 키 위반으로 트랜잭션 전체가 롤백되어야 한다
        val bad = good.copy(texts = good.texts + EntryTextEntity(999, Lang.EN, "orphan", TextOrigin.USER))
        assertFails { repo.importAll(bad) }
        assertEquals(1, repo.observeLibrary().first().size)
        assertEquals(nasty, repo.observeLibrary().first().single().texts[Lang.EN])
    }
}
