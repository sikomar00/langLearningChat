package com.chatlearn.data

import androidx.room.*
import com.chatlearn.domain.Lang
import kotlinx.coroutines.flow.Flow

data class TagLink(val entryId: Long, val name: String)

@Dao
interface AppDao {
    @Insert suspend fun insertEntry(e: EntryEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertText(t: EntryTextEntity)
    @Insert suspend fun insertCard(c: ReviewCardEntity): Long
    @Update suspend fun updateCard(c: ReviewCardEntity)
    @Insert suspend fun insertLog(l: ReviewLogEntity): Long
    @Insert suspend fun insertTag(t: TagEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEntryTag(et: EntryTagEntity)

    @Query("SELECT id FROM tag WHERE name = :name") suspend fun findTagId(name: String): Long?

    @Query(
        "SELECT t.entryId FROM entry_text t JOIN entry e ON e.id = t.entryId " +
            "WHERE t.lang = e.chatLang AND t.lang = :lang AND t.text = :text LIMIT 1"
    )
    suspend fun findEntryIdByChatText(lang: Lang, text: String): Long?

    @Query("SELECT * FROM entry ORDER BY createdAt DESC") fun observeEntries(): Flow<List<EntryEntity>>
    @Query("SELECT * FROM entry_text") fun observeTexts(): Flow<List<EntryTextEntity>>
    @Query("SELECT et.entryId AS entryId, t.name AS name FROM entry_tag et JOIN tag t ON t.id = et.tagId")
    fun observeTagLinks(): Flow<List<TagLink>>

    // 태그가 붙은 문장 중 가장 최근에 저장된 시각 순서(같으면 나중에 만든 태그 먼저)
    @Query(
        "SELECT t.name FROM tag t JOIN entry_tag et ON et.tagId = t.id JOIN entry e ON e.id = et.entryId " +
            "GROUP BY t.id ORDER BY MAX(e.createdAt) DESC, t.id DESC"
    )
    fun observeTagsByRecency(): Flow<List<String>>

    @Query("SELECT * FROM entry_text WHERE entryId = :id") suspend fun textsFor(id: Long): List<EntryTextEntity>
    @Query("SELECT * FROM review_card WHERE lastReviewedAt IS NOT NULL AND dueAt <= :now ORDER BY dueAt LIMIT :limit")
    suspend fun dueReviewed(now: Long, limit: Int): List<ReviewCardEntity>
    @Query("SELECT * FROM review_card WHERE lastReviewedAt IS NULL AND dueAt <= :now ORDER BY dueAt LIMIT :limit")
    suspend fun dueNew(now: Long, limit: Int): List<ReviewCardEntity>

    // 오늘 처음 복습한 카드 수: 모든 로그가 dayStart 이후인 카드
    @Query(
        "SELECT COUNT(*) FROM review_card c WHERE c.lastReviewedAt >= :dayStart " +
            "AND NOT EXISTS (SELECT 1 FROM review_log l WHERE l.cardId = c.id AND l.reviewedAt < :dayStart)"
    )
    suspend fun introducedToday(dayStart: Long): Int

    @Query("UPDATE entry SET note = :note WHERE id = :id") suspend fun updateNote(id: Long, note: String)
    @Query("DELETE FROM entry WHERE id = :id") suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM entry") suspend fun allEntries(): List<EntryEntity>
    @Query("SELECT * FROM entry_text") suspend fun allTexts(): List<EntryTextEntity>
    @Query("SELECT * FROM review_card") suspend fun allCards(): List<ReviewCardEntity>
    @Query("SELECT * FROM review_log") suspend fun allLogs(): List<ReviewLogEntity>
    @Query("SELECT * FROM tag") suspend fun allTags(): List<TagEntity>
    @Query("SELECT * FROM entry_tag") suspend fun allEntryTags(): List<EntryTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEntries(l: List<EntryEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTexts(l: List<EntryTextEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCards(l: List<ReviewCardEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertLogs(l: List<ReviewLogEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTags(l: List<TagEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertEntryTags(l: List<EntryTagEntity>)
    @Query("DELETE FROM entry") suspend fun deleteAllEntries()
    @Query("DELETE FROM tag") suspend fun deleteAllTags()
}
