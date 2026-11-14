package com.chatlearn.data

import androidx.room.withTransaction
import com.chatlearn.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

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
    /** 함께 대화한 사람·그 사람의 나라. 같은 이름의 태그로 저장되고 종류만 다르다. */
    val people: List<String> = emptyList(),
    val countries: List<String> = emptyList(),
    /** 이 문장 앞뒤 대화. */
    val contextText: String? = null,
    val kind: EntryKind = EntryKind.SENTENCE,
    /** 이 문장에 쓰인 문법 항목 키. 복습에서 틀리면 이 항목들의 약점으로 센다. */
    val grammar: List<String> = emptyList(),
)

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data class Duplicate(val id: Long) : SaveResult
}

enum class RenameResult { Renamed, Merged, Unchanged }

data class StatsInput(val logs: List<ReviewLogEntity>, val cards: List<ReviewCardEntity>, val items: List<LibraryItem>)

data class DueCard(
    val card: ReviewCardEntity, val texts: Map<Lang, String>, val keyPhrases: Map<Lang, String> = emptyMap(), val note: String = "",
    /** 이 문장을 저장할 때 함께 둔 앞뒤 대화. */
    val context: String? = null,
    val kind: EntryKind = EntryKind.SENTENCE,
)
data class KeyPhrasePending(val entryId: Long, val lang: Lang, val text: String)
data class LibraryItem(
    val entry: EntryEntity,
    val texts: Map<Lang, String>,
    val tags: List<String>,
    val origins: Map<Lang, TextOrigin> = emptyMap(),
    val keyPhrases: Map<Lang, String> = emptyMap(),
    /** 태그 이름별 종류(사람·나라·주제). */
    val tagKinds: Map<String, TagKind> = emptyMap(),
) {
    /** 이 종류의 태그. 종류를 모르는 태그는 주제로 본다. */
    fun tagsOf(kind: TagKind): List<String> = tags.filter { (tagKinds[it] ?: TagKind.TOPIC) == kind }
}

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

    /**
     * 이름으로 태그를 찾아 사용 시각을 갱신하고, 없으면 새로 만든다.
     * 주제 태그를 사람·나라로 쓰면 그 종류로 바꾼다(반대로는 바꾸지 않는다: 상세 화면에서 태그를 고쳐도 사람이 주제로 돌아가지 않게).
     */
    private suspend fun tagIdFor(name: String, now: Long, kind: TagKind = TagKind.TOPIC): Long {
        val tag = dao.findTag(name) ?: return dao.insertTag(TagEntity(name = name, lastUsedAt = now, kind = kind))
        dao.touchTag(tag.id, now)
        if (kind != TagKind.TOPIC && tag.kind == TagKind.TOPIC) dao.setTagKind(tag.id, kind)
        return tag.id
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
        val id = dao.insertEntry(EntryEntity(
            createdAt = d.learnedAt ?: now, direction = d.direction, chatLang = d.chatLang, note = d.note, tone = d.tone,
            contextText = d.contextText?.trim()?.ifEmpty { null }, kind = d.kind,
        ))
        if (d.grammar.isNotEmpty()) dao.insertEntryGrammar(d.grammar.distinct().map { EntryGrammarEntity(id, it) })
        val (chatOrigin, koOrigin) =
            if (d.direction == Direction.RECEIVED) TextOrigin.ORIGINAL to TextOrigin.AI
            else TextOrigin.AI to TextOrigin.USER
        dao.upsertText(EntryTextEntity(id, d.chatLang, text, chatOrigin, normalizeKeyPhrase(text, d.keyPhrase)))
        dao.upsertText(EntryTextEntity(id, Lang.KO, d.koText.trim(), koOrigin))
        listOf(TagKind.TOPIC to d.tags, TagKind.PERSON to d.people, TagKind.COUNTRY to d.countries).forEach { (kind, names) ->
            names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
                dao.insertEntryTag(EntryTagEntity(id, tagIdFor(name, now, kind)))
            }
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

    // 묶고 맞추는 계산은 문장 수에 비례하므로 화면 스레드가 아닌 곳에서 한다.
    fun observeLibrary(): Flow<List<LibraryItem>> =
        combine(dao.observeEntries(), dao.observeTexts(), dao.observeTagLinks()) { entries, texts, links ->
            val textsBy = texts.groupBy { it.entryId }
            val tagsBy = links.groupBy({ it.entryId }, { it.name })
            val kindOf = links.associate { it.name to it.kind }
            entries.map { e ->
                val mine = textsBy[e.id].orEmpty()
                LibraryItem(e, mine.associate { it.lang to it.text }, tagsBy[e.id].orEmpty().sorted(), mine.associate { it.lang to it.origin },
                    mine.mapNotNull { t -> t.keyPhrase?.takeIf { it.isNotEmpty() }?.let { t.lang to it } }.toMap(),
                    tagsBy[e.id].orEmpty().associateWith { kindOf.getValue(it) })
            }
        }.flowOn(Dispatchers.Default)

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
    fun observeStatsInput(library: Flow<List<LibraryItem>> = observeLibrary()): Flow<StatsInput> =
        combine(dao.observeLogs(), dao.observeCards(), library) { logs, cards, items -> StatsInput(logs, cards, items) }

    suspend fun reviewTimes(): List<Long> = dao.reviewTimes()
    suspend fun countCards(): Int = dao.countCards()
    suspend fun reviewDueTimes(until: Long): List<Long> = dao.reviewDueTimes(until)

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

    /** [kind] 종류의 태그만 최근 사용 순으로. 저장할 때 사람·나라 칩에 쓴다. */
    fun observeRecentTags(kind: TagKind): Flow<List<String>> = dao.observeTagsByRecency(kind)

    suspend fun queue(now: Long, dayStart: Long, newPerDay: Int, maxReviews: Int = Scheduler.DEFAULT_MAX_REVIEWS, extraNew: Int = 0): List<DueCard> {
        val reviews = dao.dueReviewed(now, maxReviews)
        // 더 배우기는 한도를 이미 넘겼어도 남은 한도 위에 더한다.
        val newLeft = (newPerDay - dao.introducedToday(dayStart)).coerceAtLeast(0) + extraNew
        val fresh = if (newLeft > 0) dao.dueNew(now, newLeft) else emptyList()
        return dueCardsOf(interleave(reviews, fresh))
    }

    suspend fun countDueNew(now: Long): Int = dao.countDueNew(now)

    /** 라이브러리에서 고른 문장의 카드. 기한과 하루 새 카드 수 제한은 보지 않는다. */
    suspend fun cardsFor(entryIds: Set<Long>, answerLang: Lang?): List<DueCard> {
        if (entryIds.isEmpty()) return emptyList()
        val cards = entryIds.toList().chunked(500).flatMap { dao.cardsForEntries(it, answerLang) }.sortedWith(compareBy({ it.dueAt }, { it.id }))
        return dueCardsOf(cards)
    }

    suspend fun dueCardOf(card: ReviewCardEntity): DueCard = dueCardsOf(listOf(card)).single()

    /** 카드마다 따로 조회하지 않고 텍스트와 메모를 한 번에 읽는다. SQLite 변수 한도 때문에 나눠 읽는다. */
    private suspend fun dueCardsOf(cards: List<ReviewCardEntity>): List<DueCard> {
        val ids = cards.map { it.entryId }.distinct().chunked(500)
        val texts = ids.flatMap { dao.textsForEntries(it) }.groupBy { it.entryId }
        val entries = ids.flatMap { dao.entriesByIds(it) }.associateBy { it.id }
        return cards.map { card ->
            val mine = texts[card.entryId].orEmpty()
            val e = entries[card.entryId]
            DueCard(card, mine.associate { it.lang to it.text }, mine.mapNotNull { t -> t.keyPhrase?.takeIf { it.isNotEmpty() }?.let { t.lang to it } }.toMap(),
                e?.note.orEmpty(), e?.contextText, e?.kind ?: EntryKind.SENTENCE)
        }
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
    /** 카드가 그사이 삭제됐으면(문장 삭제·백업 가져오기) 아무것도 쓰지 않고 null. */
    suspend fun applyReview(card: ReviewCardEntity, newState: CardState, log: ReviewLogEntity): Long? =
        db.withTransaction {
            if (dao.getCard(card.id) == null) return@withTransaction null
            dao.updateCard(card.withState(newState))
            dao.insertLog(log)
        }

    suspend fun entryExists(id: Long): Boolean = dao.getEntry(id) != null

    /** [applyReview]를 취소한다: 카드를 평가 전 상태로 되돌리고 그 기록을 지운다. */
    suspend fun undoReview(before: ReviewCardEntity, logId: Long) = db.withTransaction {
        dao.updateCard(before)
        dao.deleteLog(logId)
    }

    suspend fun postponeCard(cardId: Long, dueAt: Long) = dao.setDue(cardId, dueAt)
    suspend fun cardCount(entryId: Long): Int = dao.cardCount(entryId)
    suspend fun cardsReviewedSince(since: Long): Int = dao.distinctCardsSince(since)
    suspend fun reviewedSince(since: Long): Boolean = dao.reviewsSince(since) > 0

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
    suspend fun setNotionPageId(id: Long, pageId: String?) = dao.setNotionPageId(id, pageId)

    suspend fun exportAll(): BackupData = db.withTransaction {
        BackupData(
            entries = dao.allEntries(), texts = dao.allTexts(), cards = dao.allCards(),
            logs = dao.allLogs(), tags = dao.allTags(), entryTags = dao.allEntryTags(),
            folders = dao.allFolders(), grammarEvents = dao.allGrammarEvents(), entryGrammar = dao.allEntryGrammar(),
        )
    }

    suspend fun importAll(d: BackupData) = db.withTransaction {
        dao.deleteAllGrammarEvents() // 문장을 지워도 남는 기록이라 따로 비운다(먼저 지워 쓸데없는 SET NULL을 피한다)
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
        dao.insertGrammarEvents(d.grammarEvents)
        dao.insertEntryGrammar(d.entryGrammar)
    }

    // ---- 문법 약점 ----

    suspend fun recordGrammar(events: List<GrammarEventEntity>): List<Long> = if (events.isEmpty()) emptyList() else dao.insertGrammarEvents(events)
    suspend fun deleteGrammarEvents(ids: List<Long>) { if (ids.isNotEmpty()) dao.deleteGrammarEvents(ids) }
    suspend fun entryGrammar(entryId: Long): List<String> = dao.grammarKeysFor(entryId)
    fun observeGrammarEvents(): Flow<List<GrammarEventEntity>> = dao.observeGrammarEvents()

    /** 끈질기게 틀리는 카드를 처음 보는 카드처럼 되돌린다(틀린 횟수·일정 모두). 기록(review_log)은 남긴다. */
    suspend fun restartCard(card: ReviewCardEntity, now: Long): ReviewCardEntity =
        newCard(card.entryId, card.answerLang, now).copy(id = card.id).also { dao.updateCard(it) }
}
