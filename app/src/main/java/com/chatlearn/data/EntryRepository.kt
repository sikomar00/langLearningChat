package com.chatlearn.data

import androidx.room.withTransaction
import com.chatlearn.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

data class EntryDraft(
    val direction: Direction,
    val chatLang: Lang,
    val chatText: String,
    val koText: String,
    val note: String,
    val tone: String?,
    val tags: List<String>,
    val altText: String? = null,
    val keyPhrase: String? = null,
    val altKeyPhrase: String? = null,
    /** 알게 된 날(밀리초). null이면 저장하는 지금. */
    val learnedAt: Long? = null,
)

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data class Duplicate(val id: Long) : SaveResult
}

enum class RenameResult { Renamed, Merged, Unchanged }

data class StatsInput(val logs: List<ReviewLogEntity>, val cards: List<ReviewCardEntity>, val items: List<LibraryItem>)

data class DueCard(val card: ReviewCardEntity, val texts: Map<Lang, String>, val keyPhrases: Map<Lang, String> = emptyMap(), val note: String = "")
data class KeyPhrasePending(val entryId: Long, val lang: Lang, val text: String)
data class LibraryItem(
    val entry: EntryEntity,
    val texts: Map<Lang, String>,
    val tags: List<String>,
    val origins: Map<Lang, TextOrigin> = emptyMap(),
    val keyPhrases: Map<Lang, String> = emptyMap(),
)

internal fun <T> interleave(a: List<T>, b: List<T>): List<T> {
    if (b.isEmpty()) return a
    val step = maxOf(1, a.size / (b.size + 1))
    val out = ArrayList<T>()
    var bi = 0
    a.forEachIndexed { i, x ->
        out.add(x)
        if ((i + 1) % step == 0 && bi < b.size) out.add(b[bi++])
    }
    while (bi < b.size) out.add(b[bi++])
    return out
}

class EntryRepository(internal val db: AppDatabase) {
    private val dao get() = db.dao()

    /** 이름으로 태그를 찾아 사용 시각을 갱신하고, 없으면 새로 만든다. */
    private suspend fun tagIdFor(name: String, now: Long): Long {
        val id = dao.findTagId(name)
        if (id != null) { dao.touchTag(id, now); return id }
        return dao.insertTag(TagEntity(name = name, lastUsedAt = now))
    }

    private fun newCard(entryId: Long, lang: Lang, now: Long): ReviewCardEntity {
        val s = Scheduler.initial(now)
        return ReviewCardEntity(
            entryId = entryId, answerLang = lang, dueAt = s.dueAt, intervalDays = s.intervalDays,
            ease = s.ease, reps = s.reps, lapses = s.lapses, lastReviewedAt = s.lastReviewedAt,
        )
    }

    suspend fun saveEntry(d: EntryDraft, now: Long, expand: Boolean = true): SaveResult = db.withTransaction {
        val text = d.chatText.trim()
        dao.findEntryIdByChatText(d.chatLang, text)?.let { return@withTransaction SaveResult.Duplicate(it) }
        val id = dao.insertEntry(EntryEntity(createdAt = d.learnedAt ?: now, direction = d.direction, chatLang = d.chatLang, note = d.note, tone = d.tone))
        val (chatOrigin, koOrigin) =
            if (d.direction == Direction.RECEIVED) TextOrigin.ORIGINAL to TextOrigin.AI
            else TextOrigin.AI to TextOrigin.USER
        dao.upsertText(EntryTextEntity(id, d.chatLang, text, chatOrigin, normalizeKeyPhrase(text, d.keyPhrase)))
        dao.upsertText(EntryTextEntity(id, Lang.KO, d.koText.trim(), koOrigin))
        d.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
            dao.insertEntryTag(EntryTagEntity(id, tagIdFor(name, now)))
        }
        dao.insertCard(newCard(id, d.chatLang, now))
        val alt = d.altText?.trim().orEmpty()
        if (expand && alt.isNotEmpty()) {
            val altLang = d.chatLang.otherForeign()
            dao.upsertText(EntryTextEntity(id, altLang, alt, TextOrigin.AI, normalizeKeyPhrase(alt, d.altKeyPhrase)))
            dao.insertCard(newCard(id, altLang, now))
        }
        SaveResult.Saved(id)
    }

    fun observeLibrary(): Flow<List<LibraryItem>> =
        combine(dao.observeEntries(), dao.observeTexts(), dao.observeTagLinks()) { entries, texts, links ->
            val textsBy = texts.groupBy { it.entryId }
            val tagsBy = links.groupBy({ it.entryId }, { it.name })
            entries.map { e ->
                val mine = textsBy[e.id].orEmpty()
                LibraryItem(e, mine.associate { it.lang to it.text }, tagsBy[e.id].orEmpty().sorted(), mine.associate { it.lang to it.origin },
                    mine.mapNotNull { t -> t.keyPhrase?.takeIf { it.isNotEmpty() }?.let { t.lang to it } }.toMap())
            }
        }

    suspend fun expansionTargets(): List<ExpansionTarget> = dao.expansionTargets()

    /** 반대 언어 카드를 만든다. 이미 있으면 false. 텍스트가 없고 [aiText]도 비었으면 false. */
    suspend fun addExpansion(entryId: Long, aiText: String?, now: Long): Boolean = db.withTransaction {
        val entry = dao.getEntry(entryId) ?: return@withTransaction false
        if (entry.chatLang == Lang.KO) return@withTransaction false
        val altLang = entry.chatLang.otherForeign()
        if (dao.textsFor(entryId).none { it.lang == altLang }) {
            val t = aiText?.trim().orEmpty()
            if (t.isEmpty()) return@withTransaction false
            dao.upsertText(EntryTextEntity(entryId, altLang, t, TextOrigin.AI))
        }
        dao.insertCardIfAbsent(newCard(entryId, altLang, now)) != -1L
    }

    /** 통계 계산에 필요한 복습 기록, 카드, 라이브러리를 한 번에 관찰한다. */
    fun observeStatsInput(): Flow<StatsInput> =
        combine(dao.observeLogs(), dao.observeCards(), observeLibrary()) { logs, cards, items -> StatsInput(logs, cards, items) }

    /** 문장의 태그를 [tags] 목록으로 통째로 바꾼다. 어떤 문장에도 안 붙은 태그는 삭제한다. */
    suspend fun setTags(entryId: Long, tags: List<String>, now: Long) = db.withTransaction {
        dao.deleteEntryTags(entryId)
        tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
            dao.insertEntryTag(EntryTagEntity(entryId, tagIdFor(name, now)))
        }
        dao.deleteOrphanTags()
    }

    /** 태그 이름을 모든 문장에서 바꾼다. 바꿀 이름이 이미 있으면 두 태그를 합친다. */
    suspend fun renameTag(old: String, new: String): RenameResult = db.withTransaction {
        val newName = new.trim()
        val oldId = dao.findTagId(old.trim())
        if (oldId == null || newName.isEmpty() || newName == old.trim()) return@withTransaction RenameResult.Unchanged
        val existingId = dao.findTagId(newName)
        if (existingId == null) {
            dao.renameTag(oldId, newName)
            RenameResult.Renamed
        } else {
            dao.touchTag(existingId, maxOf(dao.tagLastUsed(existingId), dao.tagLastUsed(oldId)))
            dao.copyTagLinks(oldId, existingId)
            dao.deleteTag(oldId) // 외래 키 CASCADE로 옛 연결도 함께 지워진다
            RenameResult.Merged
        }
    }

    /** 최근에 쓴 태그 순서. 자동완성 후보로 쓴다. */
    fun observeRecentTags(): Flow<List<String>> = dao.observeTagsByRecency()

    suspend fun queue(now: Long, dayStart: Long, newPerDay: Int, maxReviews: Int = 50, extraNew: Int = 0): List<DueCard> {
        val reviews = dao.dueReviewed(now, maxReviews)
        // 더 배우기는 한도를 이미 넘겼어도 남은 한도 위에 더한다.
        val newLeft = (newPerDay - dao.introducedToday(dayStart)).coerceAtLeast(0) + extraNew
        val fresh = if (newLeft > 0) dao.dueNew(now, newLeft) else emptyList()
        return interleave(reviews, fresh).map { c ->
            dueCardOf(c)
        }
    }

    suspend fun countDueNew(now: Long): Int = dao.countDueNew(now)

    /** 라이브러리에서 고른 문장의 카드. 기한과 하루 새 카드 수 제한은 보지 않는다. */
    suspend fun cardsFor(entryIds: Set<Long>, answerLang: Lang?): List<DueCard> {
        if (entryIds.isEmpty()) return emptyList()
        return dao.cardsForEntries(entryIds.toList(), answerLang).map { c ->
            dueCardOf(c)
        }
    }

    suspend fun dueCardOf(card: ReviewCardEntity): DueCard {
        val texts = dao.textsFor(card.entryId)
        return DueCard(card, texts.associate { it.lang to it.text }, texts.mapNotNull { t -> t.keyPhrase?.takeIf { it.isNotEmpty() }?.let { t.lang to it } }.toMap(),
            dao.getEntry(card.entryId)?.note.orEmpty())
    }

    /** 텍스트를 바꾼다. 핵심 표현은 새 문장에도 들어 있을 때만 남긴다. */
    suspend fun putText(entryId: Long, lang: Lang, text: String, origin: TextOrigin) = db.withTransaction {
        val clean = text.trim()
        val old = dao.textsFor(entryId).firstOrNull { it.lang == lang }?.keyPhrase
        val kept = if (old == null) null else normalizeKeyPhrase(clean, old)
        dao.upsertText(EntryTextEntity(entryId, lang, clean, origin, kept))
    }

    /** 핵심 표현을 고친다. 문장에 있으면 저장하고 true, 없거나 비었으면 "없음"("")으로 두고 false. */
    suspend fun setKeyPhrase(entryId: Long, lang: Lang, phrase: String?): Boolean = db.withTransaction {
        val text = dao.textsFor(entryId).firstOrNull { it.lang == lang }?.text ?: return@withTransaction false
        val ok = normalizeKeyPhrase(text, phrase)
        dao.setKeyPhrase(entryId, lang, ok ?: "")
        ok != null
    }

    /** 보낼 말을 만들 때 AI에 참고로 줄, 내가 저장한 핵심 표현(최근 순)과 한국어 뜻. */
    suspend fun knownExpressions(lang: Lang, limit: Int = 40): List<com.chatlearn.ai.KnownExpression> =
        dao.knownExpressions(lang, limit).map { com.chatlearn.ai.KnownExpression(it.phrase, it.ko) }

    suspend fun keyPhraseTargets(): List<KeyPhrasePending> =
        dao.textsWithoutKeyPhrase().map { KeyPhrasePending(it.entryId, it.lang, it.text) }
    fun observeMissingKeyPhrases(): Flow<Int> = dao.observeMissingKeyPhrases()

    /** 평가를 저장하고 새 기록의 id를 돌려준다(되돌리기에 쓴다). */
    suspend fun applyReview(card: ReviewCardEntity, newState: CardState, log: ReviewLogEntity): Long =
        db.withTransaction {
            dao.updateCard(card.withState(newState))
            dao.insertLog(log)
        }

    /** [applyReview]를 취소한다: 카드를 평가 전 상태로 되돌리고 그 기록을 지운다. */
    suspend fun undoReview(before: ReviewCardEntity, logId: Long) = db.withTransaction {
        dao.updateCard(before)
        dao.deleteLog(logId)
    }

    suspend fun postponeCard(cardId: Long, dueAt: Long) = dao.setDue(cardId, dueAt)
    suspend fun cardCount(entryId: Long): Int = dao.cardCount(entryId)
    suspend fun cardsReviewedSince(since: Long): Int = dao.distinctCardsSince(since)
    suspend fun reviewedSince(since: Long): Boolean = dao.reviewsSince(since) > 0
    suspend fun textsOf(entryId: Long): Map<Lang, String> = dao.textsFor(entryId).associate { it.lang to it.text }

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)
    suspend fun deleteEntries(ids: Collection<Long>) { if (ids.isNotEmpty()) dao.deleteEntries(ids.toList()) }

    /** 알게 된 날(저장일)을 고친다. */
    suspend fun setLearnedAt(id: Long, at: Long) = dao.setCreatedAt(id, at)

    fun observeFolders(): Flow<List<FolderEntity>> = dao.observeFolders()

    /** 같은 이름의 폴더가 있으면 그 id를, 없으면 새로 만들어 돌려준다. 이름이 비면 null. */
    suspend fun createFolder(name: String, now: Long): Long? = db.withTransaction {
        val clean = name.trim()
        if (clean.isEmpty()) return@withTransaction null
        dao.findFolderId(clean) ?: dao.insertFolder(FolderEntity(name = clean, createdAt = now))
    }

    /** 폴더 이름을 바꾼다. 이미 있는 이름이면 바꾸지 않고 false. */
    suspend fun renameFolder(id: Long, name: String): Boolean = db.withTransaction {
        val clean = name.trim()
        val existing = dao.findFolderId(clean)
        if (clean.isEmpty() || (existing != null && existing != id)) return@withTransaction false
        dao.renameFolder(id, clean)
        true
    }

    /** 폴더만 지운다. 안에 있던 문장은 지워지지 않고 폴더 없는 상태로 돌아온다. */
    suspend fun deleteFolder(id: Long) = dao.deleteFolder(id)

    /** 문장들을 폴더로 옮긴다. [folderId]가 null이면 폴더에서 꺼낸다. */
    suspend fun moveToFolder(ids: Collection<Long>, folderId: Long?) { if (ids.isNotEmpty()) dao.setFolder(ids.toList(), folderId) }
    suspend fun updateNote(id: Long, note: String) = dao.updateNote(id, note)

    suspend fun exportAll(): BackupData = db.withTransaction {
        BackupData(
            entries = dao.allEntries(), texts = dao.allTexts(), cards = dao.allCards(),
            logs = dao.allLogs(), tags = dao.allTags(), entryTags = dao.allEntryTags(),
            folders = dao.allFolders(),
        )
    }

    suspend fun importAll(d: BackupData) = db.withTransaction {
        dao.deleteAllEntries()
        dao.deleteAllTags()
        dao.deleteAllFolders()
        dao.insertFolders(d.folders) // 문장이 폴더를 가리키므로 폴더가 먼저 들어가야 한다
        dao.insertEntries(d.entries)
        dao.insertTexts(d.texts)
        dao.insertCards(d.cards)
        dao.insertLogs(d.logs)
        dao.insertTags(d.tags)
        dao.insertEntryTags(d.entryTags)
    }
}
