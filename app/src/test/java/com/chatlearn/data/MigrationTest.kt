package com.chatlearn.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chatlearn.domain.EntryKind
import com.chatlearn.domain.ExerciseKind
import com.chatlearn.domain.GrammarSource
import com.chatlearn.domain.TagKind
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Verdict
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"

    @After fun tearDown() { ctx.deleteDatabase(dbName) }

    /** Room이 내보낸 [version] 스키마 그대로 DB를 만들고 [rows]를 넣는다(실제 예전 앱이 만든 DB와 같은 모양). */
    private fun createFromSchema(version: Int, rows: List<String>) {
        val root = Json.parseToJsonElement(File("schemas/com.chatlearn.data.AppDatabase/$version.json").readText()).jsonObject
        val database = root["database"]!!.jsonObject
        val db = ctx.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        database["entities"]!!.jsonArray.forEach { e ->
            val o = e.jsonObject
            val table = o["tableName"]!!.jsonPrimitive.content
            fun sql(s: String) = s.replace("\${TABLE_NAME}", table)
            db.execSQL(sql(o["createSql"]!!.jsonPrimitive.content))
            o["indices"]?.jsonArray?.forEach { db.execSQL(sql(it.jsonObject["createSql"]!!.jsonPrimitive.content)) }
        }
        database["setupQueries"]!!.jsonArray.forEach { db.execSQL(it.jsonPrimitive.content) }
        rows.forEach(db::execSQL)
        db.version = version
        db.close()
    }

    private fun open() = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
        .addMigrations(*Migrations.ALL).allowMainThreadQueries().build()

    /** v1 DB에 실제 사용자 데이터와 비슷한 행을 넣는다. */
    private fun createV1() = createFromSchema(1, listOf(
        "INSERT INTO entry (id, createdAt, direction, chatLang, note, tone) VALUES (1, 100, 'RECEIVED', 'EN', 'n', NULL)",
        "INSERT INTO entry (id, createdAt, direction, chatLang, note, tone) VALUES (2, 300, 'SENT', 'ES', 'm', 'casual')",
        "INSERT INTO entry_text VALUES (1, 'EN', 'Hello', 'ORIGINAL')",
        "INSERT INTO entry_text VALUES (1, 'KO', '안녕', 'AI')",
        "INSERT INTO entry_text VALUES (2, 'ES', 'Hola', 'AI')",
        "INSERT INTO entry_text VALUES (2, 'KO', '안녕', 'USER')",
        "INSERT INTO review_card (id, entryId, answerLang, dueAt, intervalDays, ease, reps, lapses, lastReviewedAt) VALUES (1, 1, 'EN', 500, 3.0, 2.5, 2, 1, 400)",
        "INSERT INTO review_log (id, cardId, reviewedAt, promptLang, userAnswer, aiVerdict, aiFeedback, rating) VALUES (1, 1, 400, 'KO', 'Hello', 'CORRECT', 'ok', 'GOOD')",
        "INSERT INTO tag (id, name) VALUES (1, 'Alex')",
        "INSERT INTO tag (id, name) VALUES (2, 'orphan')",
        "INSERT INTO entry_tag VALUES (1, 1)",
        "INSERT INTO entry_tag VALUES (2, 1)",
    ))

    private fun openV2() = open()

    @Test fun upgradesV1DataWithoutLosingAnything() = runBlocking {
        createV1()
        val db = openV2()
        val dao = db.dao()
        assertEquals(2, dao.allEntries().size)
        assertEquals(4, dao.allTexts().size)
        val card = dao.allCards().single()
        assertEquals(3.0, card.intervalDays); assertEquals(2, card.reps); assertEquals(1, card.lapses); assertEquals(400L, card.lastReviewedAt)
        val log = dao.allLogs().single()
        assertEquals(Verdict.CORRECT, log.aiVerdict)
        assertNull(log.responseMs)
        assertEquals(ExerciseKind.TRANSLATE, log.exercise)
        val tags = dao.allTags().associate { it.name to it.lastUsedAt }
        assertEquals(300L, tags["Alex"]) // 붙은 문장 중 가장 최근 저장 시각
        assertEquals(0L, tags["orphan"])
        db.close()
    }

    @Test fun cardsAreNowUniquePerEntryAndAnswerLanguage() = runBlocking {
        createV1()
        val db = openV2()
        val dao = db.dao()
        fun card(lang: Lang) = ReviewCardEntity(entryId = 1, answerLang = lang, dueAt = 0, intervalDays = 0.0,
            ease = 2.5, reps = 0, lapses = 0, lastReviewedAt = null)
        dao.insertCard(card(Lang.ES)) // 같은 문장, 다른 언어 → 허용
        assertFails { runBlocking { dao.insertCard(card(Lang.ES)) } } // 같은 문장, 같은 언어 → 거부
        assertEquals(2, dao.allCards().size)
        db.close()
    }

    private fun createV2() = createFromSchema(2, listOf(
        "INSERT INTO entry (id, createdAt, direction, chatLang, note, tone) VALUES (1, 100, 'RECEIVED', 'EN', 'n', NULL)",
        "INSERT INTO entry_text VALUES (1, 'EN', 'Hello', 'ORIGINAL')",
        "INSERT INTO entry_text VALUES (1, 'KO', '안녕', 'AI')",
        "INSERT INTO review_card (id, entryId, answerLang, dueAt, intervalDays, ease, reps, lapses, lastReviewedAt) VALUES (1, 1, 'EN', 500, 3.0, 2.5, 2, 1, 400)",
    ))

    @Test fun v2UpgradesToV3KeepingTextsAndLeavingKeyPhrasesEmpty() = runBlocking {
        createV2()
        val db = open()
        val texts = db.dao().allTexts()
        assertEquals(2, texts.size)
        assertTrue(texts.all { it.keyPhrase == null })
        assertEquals("Hello", texts.first { it.lang == Lang.EN }.text)
        assertEquals(1, db.dao().allCards().size)
        db.close()
    }

    private fun createV3() = createFromSchema(3, listOf(
        "INSERT INTO entry (id, createdAt, direction, chatLang, note, tone) VALUES (1, 100, 'RECEIVED', 'EN', 'n', NULL)",
        "INSERT INTO entry_text (entryId, lang, text, origin) VALUES (1, 'EN', 'Hello', 'ORIGINAL')",
        "INSERT INTO entry_text (entryId, lang, text, origin) VALUES (1, 'KO', '안녕', 'AI')",
    ))

    @Test fun v3UpgradesToV4WithEntriesOutsideAnyFolderAndFolderDeleteKeepsEntries() = runBlocking {
        createV3()
        val db = open()
        val dao = db.dao()
        val e = dao.allEntries().single()
        assertEquals(100L, e.createdAt)
        assertNull(e.folderId)
        val folder = dao.insertFolder(FolderEntity(name = "여행", createdAt = 1))
        dao.setFolder(listOf(e.id), folder)
        assertEquals(folder, dao.allEntries().single().folderId)
        dao.deleteFolder(folder) // 폴더만 지워지고 문장은 남아야 한다
        assertNull(dao.allEntries().single().folderId)
        assertEquals(1, dao.allEntries().size)
        db.close()
    }

    private fun createV4() = createFromSchema(4, listOf(
        "INSERT INTO entry (id, createdAt, direction, chatLang, note, tone, folderId) VALUES (1, 100, 'RECEIVED', 'EN', 'n', NULL, NULL)",
        "INSERT INTO entry_text (entryId, lang, text, origin, keyPhrase) VALUES (1, 'EN', 'Hello there', 'ORIGINAL', 'Hello')",
        "INSERT INTO entry_text (entryId, lang, text, origin, keyPhrase) VALUES (1, 'KO', '안녕', 'AI', NULL)",
        "INSERT INTO review_card (id, entryId, answerLang, dueAt, intervalDays, ease, reps, lapses, lastReviewedAt) VALUES (1, 1, 'EN', 500, 10.0, 2.5, 3, 0, 400)",
        "INSERT INTO review_card (id, entryId, answerLang, dueAt, intervalDays, ease, reps, lapses, lastReviewedAt) VALUES (2, 1, 'ES', 0, 0.0, 2.5, 0, 0, NULL)",
        "INSERT INTO review_card (id, entryId, answerLang, dueAt, intervalDays, ease, reps, lapses, lastReviewedAt) VALUES (3, 1, 'KO', 900, 2.0, 1.3, 1, 4, 800)",
        "INSERT INTO review_log (id, cardId, reviewedAt, promptLang, userAnswer, aiVerdict, aiFeedback, rating, responseMs, exercise) VALUES (1, 1, 400, 'KO', 'Hello', 'CORRECT', 'ok', 'GOOD', 1200, 'CLOZE')",
        "INSERT INTO tag (id, name, lastUsedAt) VALUES (1, 'Alex', 100)",
        "INSERT INTO entry_tag VALUES (1, 1)",
    ))

    @Test fun v4UpgradesToV5KeepingDataAndSeedingFsrsFromTheOldSchedule() = runBlocking {
        createV4()
        val db = open()
        val dao = db.dao()
        val e = dao.allEntries().single()
        assertEquals(EntryKind.SENTENCE, e.kind)
        assertNull(e.contextText)
        assertNull(e.notionPageId)
        assertEquals("Hello", dao.allTexts().first { it.lang == Lang.EN }.keyPhrase)
        assertEquals(TagKind.TOPIC, dao.allTags().single().kind)
        val cards = dao.allCards().associateBy { it.id }
        // 복습한 카드: 지금 간격을 안정도로, ease를 난이도(1~10)로 옮긴다. 기한은 그대로.
        assertEquals(10.0, cards.getValue(1).stability); assertEquals(5.0, cards.getValue(1).difficulty); assertEquals(500L, cards.getValue(1).dueAt)
        assertEquals(10.0, cards.getValue(3).difficulty) // ease 1.3(가장 어려움) → 10
        // 아직 안 본 카드는 첫 복습 때 정한다.
        assertNull(cards.getValue(2).stability); assertNull(cards.getValue(2).difficulty)
        assertEquals(ExerciseKind.CLOZE, dao.allLogs().single().exercise)
        db.close()
    }

    @Test fun v5HasGrammarTablesAndTheNewIndexes() = runBlocking {
        createV4()
        val db = open()
        val dao = db.dao()
        dao.insertGrammarEvent(GrammarEventEntity(lang = Lang.ES, pointKey = "es.ser_estar", source = GrammarSource.ATTEMPT, isError = true, entryId = 1, at = 5))
        dao.insertEntryGrammar(listOf(EntryGrammarEntity(1, "es.ser_estar")))
        dao.deleteEntry(1)
        assertNull(dao.allGrammarEvents().single().entryId) // 문장이 지워져도 약점 기록은 남는다
        assertTrue(dao.allEntryGrammar().isEmpty())
        val names = db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0)) }
        }
        assertTrue(names.containsAll(listOf("index_review_log_reviewedAt", "index_entry_text_lang_text", "index_entry_createdAt")))
        db.close()
    }

    private fun createV5() = createFromSchema(5, listOf(
        "INSERT INTO entry (id, createdAt, direction, chatLang, note, tone, folderId, kind, contextText, notionPageId) VALUES (1, 100, 'RECEIVED', 'EN', '', NULL, NULL, 'SENTENCE', NULL, NULL)",
        "INSERT INTO review_card (id, entryId, answerLang, dueAt, intervalDays, ease, reps, lapses, lastReviewedAt, stability, difficulty) VALUES (1, 1, 'EN', 500, 3.0, 2.5, 2, 0, 400, 3.0, 5.0)",
        "INSERT INTO review_card (id, entryId, answerLang, dueAt, intervalDays, ease, reps, lapses, lastReviewedAt, stability, difficulty) VALUES (2, 1, 'ES', 0, 0.0, 2.5, 0, 0, NULL, NULL, NULL)",
    ))

    @Test fun v5UpgradesToV6KeepingEveryEntryInReviewAndStatsUntilMarkedKnown() = runBlocking {
        createV5()
        val db = open()
        val dao = db.dao()
        val e = dao.allEntries().single()
        assertEquals(false, e.known)
        assertEquals(false, e.excludeFromStats)
        assertEquals(1, dao.dueReviewed(1_000, 10).size)
        assertEquals(1, dao.countDueNew(1_000))
        dao.setKnown(listOf(1), true)
        // 이미 알아요: 복습 차례·새 카드 수·예보에서 빠지고, 직접 고르면 여전히 나온다.
        assertTrue(dao.dueReviewed(1_000, 10).isEmpty())
        assertTrue(dao.dueNew(1_000, 10).isEmpty())
        assertEquals(0, dao.countDueNew(1_000))
        assertTrue(dao.reviewDueTimes(10_000).isEmpty())
        assertEquals(2, dao.cardsForEntries(listOf(1), null).size)
        db.close()
    }
}
