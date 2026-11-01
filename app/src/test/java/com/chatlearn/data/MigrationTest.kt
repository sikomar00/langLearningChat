package com.chatlearn.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.chatlearn.domain.ExerciseKind
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

    /** Room이 내보낸 v1 스키마 그대로 DB를 만들고, 실제 사용자 데이터와 비슷한 행을 넣는다. */
    private fun createV1() {
        val root = Json.parseToJsonElement(File("schemas/com.chatlearn.data.AppDatabase/1.json").readText()).jsonObject
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
        listOf(
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
        ).forEach(db::execSQL)
        db.version = 1
        db.close()
    }

    private fun openV2() = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
        .addMigrations(Migrations.MIGRATION_1_2, Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4)
        .allowMainThreadQueries()
        .build()

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

    private fun createV2() {
        val root = Json.parseToJsonElement(File("schemas/com.chatlearn.data.AppDatabase/2.json").readText()).jsonObject
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
        listOf(
            "INSERT INTO entry (id, createdAt, direction, chatLang, note, tone) VALUES (1, 100, 'RECEIVED', 'EN', 'n', NULL)",
            "INSERT INTO entry_text VALUES (1, 'EN', 'Hello', 'ORIGINAL')",
            "INSERT INTO entry_text VALUES (1, 'KO', '안녕', 'AI')",
            "INSERT INTO review_card (id, entryId, answerLang, dueAt, intervalDays, ease, reps, lapses, lastReviewedAt) VALUES (1, 1, 'EN', 500, 3.0, 2.5, 2, 1, 400)",
        ).forEach(db::execSQL)
        db.version = 2
        db.close()
    }

    @Test fun v2UpgradesToV3KeepingTextsAndLeavingKeyPhrasesEmpty() = runBlocking {
        createV2()
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(Migrations.MIGRATION_2_3, Migrations.MIGRATION_3_4).allowMainThreadQueries().build()
        val texts = db.dao().allTexts()
        assertEquals(2, texts.size)
        assertTrue(texts.all { it.keyPhrase == null })
        assertEquals("Hello", texts.first { it.lang == Lang.EN }.text)
        assertEquals(1, db.dao().allCards().size)
        db.close()
    }

    private fun createV3() {
        val root = Json.parseToJsonElement(File("schemas/com.chatlearn.data.AppDatabase/3.json").readText()).jsonObject
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
        listOf(
            "INSERT INTO entry (id, createdAt, direction, chatLang, note, tone) VALUES (1, 100, 'RECEIVED', 'EN', 'n', NULL)",
            "INSERT INTO entry_text (entryId, lang, text, origin) VALUES (1, 'EN', 'Hello', 'ORIGINAL')",
            "INSERT INTO entry_text (entryId, lang, text, origin) VALUES (1, 'KO', '안녕', 'AI')",
        ).forEach(db::execSQL)
        db.version = 3
        db.close()
    }

    @Test fun v3UpgradesToV4WithEntriesOutsideAnyFolderAndFolderDeleteKeepsEntries() = runBlocking {
        createV3()
        val db = Room.databaseBuilder(ctx, AppDatabase::class.java, dbName)
            .addMigrations(Migrations.MIGRATION_3_4).allowMainThreadQueries().build()
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
}
