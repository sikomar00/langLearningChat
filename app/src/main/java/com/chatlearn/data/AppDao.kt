package com.chatlearn.data

import androidx.room.*
import com.chatlearn.domain.Lang
import kotlinx.coroutines.flow.Flow

data class TagLink(val entryId: Long, val name: String)
data class KnownExpressionRow(val phrase: String, val ko: String)
data class KeyPhraseTarget(val entryId: Long, val lang: Lang, val text: String)
data class ExpansionTarget(val entryId: Long, val chatLang: Lang, val chatText: String)

@Dao
interface AppDao {
    @Insert suspend fun insertEntry(e: EntryEntity): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertText(t: EntryTextEntity)
    @Insert suspend fun insertCard(c: ReviewCardEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCardIfAbsent(c: ReviewCardEntity): Long
    @Query("SELECT * FROM entry WHERE id = :id") suspend fun getEntry(id: Long): EntryEntity?
    @Query(
        "SELECT e.id AS entryId, e.chatLang AS chatLang, t.text AS chatText FROM entry e " +
            "JOIN entry_text t ON t.entryId = e.id AND t.lang = e.chatLang " +
            "WHERE NOT EXISTS (SELECT 1 FROM review_card c WHERE c.entryId = e.id AND c.answerLang <> e.chatLang) " +
            "ORDER BY e.id"
    )
    suspend fun expansionTargets(): List<ExpansionTarget>
    @Update suspend fun updateCard(c: ReviewCardEntity)
    @Query("SELECT * FROM review_card WHERE id = :id") suspend fun getCard(id: Long): ReviewCardEntity?
    @Insert suspend fun insertLog(l: ReviewLogEntity): Long
    @Query("UPDATE entry_text SET keyPhrase = :phrase WHERE entryId = :entryId AND lang = :lang")
    suspend fun setKeyPhrase(entryId: Long, lang: Lang, phrase: String?)
    @Query(
        "SELECT t.keyPhrase AS phrase, k.text AS ko FROM entry_text t " +
            "JOIN entry_text k ON k.entryId = t.entryId AND k.lang = 'KO' " +
            "JOIN entry e ON e.id = t.entryId " +
            "WHERE t.lang = :lang AND t.keyPhrase IS NOT NULL AND t.keyPhrase != '' ORDER BY e.createdAt DESC, e.id DESC LIMIT :limit"
    )
    suspend fun knownExpressions(lang: Lang, limit: Int): List<KnownExpressionRow>
    @Query("SELECT * FROM entry_text WHERE lang != 'KO' AND keyPhrase IS NULL") suspend fun textsWithoutKeyPhrase(): List<EntryTextEntity>
    @Query("SELECT COUNT(*) FROM entry_text WHERE lang != 'KO' AND keyPhrase IS NULL") fun observeMissingKeyPhrases(): Flow<Int>
    @Query("SELECT COUNT(DISTINCT cardId) FROM review_log WHERE reviewedAt >= :since") suspend fun distinctCardsSince(since: Long): Int
    @Query("DELETE FROM review_log WHERE id = :id") suspend fun deleteLog(id: Long)
    @Query("SELECT COUNT(*) FROM review_card WHERE entryId = :entryId") suspend fun cardCount(entryId: Long): Int
    @Query("SELECT COUNT(*) FROM review_log WHERE reviewedAt >= :since") suspend fun reviewsSince(since: Long): Int
    @Query("UPDATE review_card SET dueAt = :dueAt WHERE id = :id") suspend fun setDue(id: Long, dueAt: Long)
    @Insert suspend fun insertTag(t: TagEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertEntryTag(et: EntryTagEntity)

    @Query("SELECT id FROM tag WHERE name = :name") suspend fun findTagId(name: String): Long?
    @Query("UPDATE tag SET lastUsedAt = :at WHERE id = :id") suspend fun touchTag(id: Long, at: Long)
    @Query("SELECT lastUsedAt FROM tag WHERE id = :id") suspend fun tagLastUsed(id: Long): Long
    @Query("DELETE FROM entry_tag WHERE entryId = :entryId") suspend fun deleteEntryTags(entryId: Long)
    @Query("DELETE FROM tag WHERE id NOT IN (SELECT DISTINCT tagId FROM entry_tag)") suspend fun deleteOrphanTags()
    @Query("UPDATE tag SET name = :name WHERE id = :id") suspend fun renameTag(id: Long, name: String)
    // 두 태그를 합칠 때: from이 붙은 문장에 to를 붙인다(이미 붙어 있으면 무시). from 태그 삭제는 호출한 쪽에서 한다.
    @Query("INSERT OR IGNORE INTO entry_tag (entryId, tagId) SELECT entryId, :toId FROM entry_tag WHERE tagId = :fromId")
    suspend fun copyTagLinks(fromId: Long, toId: Long)
    @Query("DELETE FROM tag WHERE id = :id") suspend fun deleteTag(id: Long)

    @Query(
        "SELECT t.entryId FROM entry_text t JOIN entry e ON e.id = t.entryId " +
            "WHERE t.lang = e.chatLang AND t.lang = :lang AND t.text = :text LIMIT 1"
    )
    suspend fun findEntryIdByChatText(lang: Lang, text: String): Long?

    @Query("SELECT * FROM entry ORDER BY createdAt DESC") fun observeEntries(): Flow<List<EntryEntity>>
    @Query("SELECT * FROM entry_text") fun observeTexts(): Flow<List<EntryTextEntity>>
    @Query("SELECT et.entryId AS entryId, t.name AS name FROM entry_tag et JOIN tag t ON t.id = et.tagId")
    fun observeTagLinks(): Flow<List<TagLink>>

    @Query("SELECT name FROM tag WHERE id IN (SELECT tagId FROM entry_tag) ORDER BY lastUsedAt DESC, id DESC")
    fun observeTagsByRecency(): Flow<List<String>>

    @Query("SELECT * FROM review_log") fun observeLogs(): Flow<List<ReviewLogEntity>>
    @Query("SELECT * FROM review_card") fun observeCards(): Flow<List<ReviewCardEntity>>

    @Query("SELECT * FROM entry_text WHERE entryId = :id") suspend fun textsFor(id: Long): List<EntryTextEntity>
    @Query("SELECT * FROM entry_text WHERE entryId IN (:ids)") suspend fun textsForEntries(ids: List<Long>): List<EntryTextEntity>
    @Query("SELECT * FROM entry WHERE id IN (:ids)") suspend fun entriesByIds(ids: List<Long>): List<EntryEntity>
    @Query("SELECT * FROM review_card WHERE entryId IN (:ids) AND (:lang IS NULL OR answerLang = :lang) ORDER BY dueAt, id")
    suspend fun cardsForEntries(ids: List<Long>, lang: Lang?): List<ReviewCardEntity>
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

    /** 아직 한 번도 복습하지 않았고 기한이 된 새 카드 수(오늘 한도와 상관없이). */
    @Query("SELECT COUNT(*) FROM review_card WHERE lastReviewedAt IS NULL AND dueAt <= :now")
    suspend fun countDueNew(now: Long): Int

    // 폴더
    @Insert suspend fun insertFolder(f: FolderEntity): Long
    @Query("SELECT id FROM folder WHERE name = :name") suspend fun findFolderId(name: String): Long?
    @Query("SELECT * FROM folder ORDER BY name COLLATE NOCASE") fun observeFolders(): Flow<List<FolderEntity>>
    @Query("UPDATE folder SET name = :name WHERE id = :id") suspend fun renameFolder(id: Long, name: String)
    @Query("DELETE FROM folder WHERE id = :id") suspend fun deleteFolder(id: Long)
    @Query("UPDATE entry SET folderId = :folderId WHERE id IN (:ids)") suspend fun setFolder(ids: List<Long>, folderId: Long?)
    @Query("DELETE FROM entry WHERE id IN (:ids)") suspend fun deleteEntries(ids: List<Long>)
    @Query("UPDATE entry SET createdAt = :at WHERE id = :id") suspend fun setCreatedAt(id: Long, at: Long)
    @Query("SELECT * FROM folder") suspend fun allFolders(): List<FolderEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFolders(l: List<FolderEntity>)
    @Query("DELETE FROM folder") suspend fun deleteAllFolders()

    @Query("UPDATE entry SET note = :note WHERE id = :id") suspend fun updateNote(id: Long, note: String)
    @Query("DELETE FROM entry WHERE id = :id") suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM entry") suspend fun allEntries(): List<EntryEntity>
    @Query("SELECT * FROM entry_text") suspend fun allTexts(): List<EntryTextEntity>
    @Query("SELECT * FROM review_card") suspend fun allCards(): List<ReviewCardEntity>
    @Query("SELECT * FROM review_log") suspend fun allLogs(): List<ReviewLogEntity>
    @Query("SELECT reviewedAt FROM review_log") suspend fun reviewTimes(): List<Long>
    @Query("SELECT COUNT(*) FROM review_card") suspend fun countCards(): Int
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
