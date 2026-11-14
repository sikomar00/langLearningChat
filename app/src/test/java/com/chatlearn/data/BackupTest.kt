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

    private val v1Json = """{"version":1,"entries":[{"id":1,"createdAt":1,"direction":"RECEIVED","chatLang":"EN","note":"n","tone":null}],""" +
        """"texts":[{"entryId":1,"lang":"EN","text":"Hello","origin":"ORIGINAL"},{"entryId":1,"lang":"KO","text":"안녕","origin":"AI"}],""" +
        """"cards":[{"id":1,"entryId":1,"answerLang":"EN","dueAt":5,"intervalDays":1.0,"ease":2.5,"reps":1,"lapses":0,"lastReviewedAt":4}],""" +
        """"logs":[{"id":1,"cardId":1,"reviewedAt":4,"promptLang":"KO","userAnswer":"Hello","aiVerdict":"CORRECT","aiFeedback":"ok","rating":"GOOD"}],""" +
        """"tags":[{"id":1,"name":"Alex"}],"entryTags":[{"entryId":1,"tagId":1}]}"""

    @Test fun aV1BackupFileStillImportsWithDefaultsForNewFields() = runBlocking {
        repo.importAll(Backup.decode(v1Json))
        val log = db.dao().allLogs().single()
        assertEquals(ExerciseKind.TRANSLATE, log.exercise)
        assertNull(log.responseMs)
        assertEquals(0L, db.dao().allTags().single().lastUsedAt)
        assertEquals(listOf("Alex"), repo.observeLibrary().first().single().tags)
    }

    @Test fun anOldExportWithoutAVersionKeyStillDecodes() {
        val noVersion = v1Json.replace("\"version\":1,", "")
        assertEquals(1, Backup.decode(noVersion).entries.size)
    }

    @Test fun exportedFileDeclaresVersion6() = runBlocking {
        assertTrue(Backup.encode(repo.exportAll()).contains("\"version\":6"))
    }

    @Test fun versions2To6AreAcceptedAndVersion7IsRejected() {
        val empty = BackupData(version = 2, entries = emptyList(), texts = emptyList(), cards = emptyList(),
            logs = emptyList(), tags = emptyList(), entryTags = emptyList())
        assertEquals(2, Backup.decode(Backup.encode(empty)).version)
        assertEquals(3, Backup.decode(Backup.encode(empty.copy(version = 3))).version)
        assertEquals(4, Backup.decode(Backup.encode(empty.copy(version = 4))).version)
        assertEquals(5, Backup.decode(Backup.encode(empty.copy(version = 5))).version)
        assertEquals(6, Backup.decode(Backup.encode(empty.copy(version = 6))).version)
        assertFailsWith<BackupFormatException> { Backup.decode(Backup.encode(empty.copy(version = 7))) }
    }

    @Test fun keyPhrasesSurviveARoundTripAndOldFilesHaveNone() = runBlocking {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "See you tomorrow", "내일 봐", "", null, emptyList(),
            keyPhrase = "See you"), 1_000)
        repo.importAll(Backup.decode(Backup.encode(repo.exportAll())))
        assertEquals("See you", db.dao().allTexts().first { it.lang == Lang.EN }.keyPhrase)
        repo.importAll(Backup.decode(v1Json))
        assertNull(db.dao().allTexts().first { it.lang == Lang.EN }.keyPhrase)
    }

    @Test fun newFieldsSurviveARoundTrip() = runBlocking {
        seed()
        val card = db.dao().allCards().single()
        db.dao().insertLog(ReviewLogEntity(cardId = card.id, reviewedAt = 2, promptLang = Lang.KO, userAnswer = "x",
            aiVerdict = Verdict.WRONG, aiFeedback = null, rating = Rating.AGAIN, responseMs = 4321, exercise = ExerciseKind.DICTATION))
        db.dao().touchTag(db.dao().allTags().single().id, 777)
        repo.importAll(Backup.decode(Backup.encode(repo.exportAll())))
        val last = db.dao().allLogs().maxByOrNull { it.id }!!
        assertEquals(ExerciseKind.DICTATION, last.exercise)
        assertEquals(4321L, last.responseMs)
        assertEquals(777L, db.dao().allTags().single().lastUsedAt)
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

    @Test fun v5RoundTripKeepsGrammarRecordsWordCardsAndTagKinds() = runBlocking {
        val id = (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.ES, "Estoy cansado", "피곤해", "", null, listOf("Ana")), 1_000) as SaveResult.Saved).id
        db.dao().insertGrammarEvent(GrammarEventEntity(lang = Lang.ES, pointKey = "es.ser_estar", source = GrammarSource.REVIEW, isError = true, entryId = id, at = 2_000))
        db.dao().insertEntryGrammar(listOf(EntryGrammarEntity(id, "es.ser_estar")))
        repo.importAll(Backup.decode(Backup.encode(repo.exportAll())))
        assertEquals("es.ser_estar", db.dao().allGrammarEvents().single().pointKey)
        assertEquals(listOf(EntryGrammarEntity(id, "es.ser_estar")), db.dao().allEntryGrammar())
        assertEquals(EntryKind.SENTENCE, db.dao().allEntries().single().kind)
    }

    @Test fun aVersion4FileWithoutTheNewFieldsStillLoads() {
        val v4 = """{"version":4,"entries":[{"id":1,"createdAt":1,"direction":"RECEIVED","chatLang":"EN","note":"","tone":null}],""" +
            """"texts":[],"cards":[{"id":1,"entryId":1,"answerLang":"EN","dueAt":0,"intervalDays":3.0,"ease":2.5,"reps":2,"lapses":0,"lastReviewedAt":5}],""" +
            """"logs":[],"tags":[{"id":1,"name":"Alex","lastUsedAt":0}],"entryTags":[]}"""
        val d = Backup.decode(v4)
        assertEquals(EntryKind.SENTENCE, d.entries.single().kind)
        assertNull(d.cards.single().stability)
        assertEquals(TagKind.TOPIC, d.tags.single().kind)
        assertTrue(d.grammarEvents.isEmpty())
    }

    @Test fun endStatesSurviveABackupRoundTripAndOldFilesReadAsActive() = runBlocking {
        val id = (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, "Known one", "한", "", null, emptyList()), 1) as SaveResult.Saved).id
        db.dao().setKnown(listOf(id), true)
        db.dao().setExcludeFromStats(listOf(id), true)
        val back = Backup.decode(Backup.encode(repo.exportAll())).entries.single { it.id == id }
        assertTrue(back.known && back.excludeFromStats)
        val old = Backup.decode(v1Json).entries.single()
        assertTrue(!old.known && !old.excludeFromStats)
    }
}
