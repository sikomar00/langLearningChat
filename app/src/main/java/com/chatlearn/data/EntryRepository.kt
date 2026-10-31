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
)

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data class Duplicate(val id: Long) : SaveResult
}

data class DueCard(val card: ReviewCardEntity, val texts: Map<Lang, String>)
data class LibraryItem(val entry: EntryEntity, val texts: Map<Lang, String>, val tags: List<String>)

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

    suspend fun saveEntry(d: EntryDraft, now: Long): SaveResult = db.withTransaction {
        val text = d.chatText.trim()
        dao.findEntryIdByChatText(d.chatLang, text)?.let { return@withTransaction SaveResult.Duplicate(it) }
        val id = dao.insertEntry(EntryEntity(createdAt = now, direction = d.direction, chatLang = d.chatLang, note = d.note, tone = d.tone))
        val (chatOrigin, koOrigin) =
            if (d.direction == Direction.RECEIVED) TextOrigin.ORIGINAL to TextOrigin.AI
            else TextOrigin.AI to TextOrigin.USER
        dao.upsertText(EntryTextEntity(id, d.chatLang, text, chatOrigin))
        dao.upsertText(EntryTextEntity(id, Lang.KO, d.koText.trim(), koOrigin))
        d.tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { name ->
            val tagId = dao.findTagId(name) ?: dao.insertTag(TagEntity(name = name))
            dao.insertEntryTag(EntryTagEntity(id, tagId))
        }
        val s = Scheduler.initial(now)
        dao.insertCard(
            ReviewCardEntity(
                entryId = id, answerLang = d.chatLang, dueAt = s.dueAt, intervalDays = s.intervalDays,
                ease = s.ease, reps = s.reps, lapses = s.lapses, lastReviewedAt = s.lastReviewedAt,
            )
        )
        SaveResult.Saved(id)
    }

    fun observeLibrary(): Flow<List<LibraryItem>> =
        combine(dao.observeEntries(), dao.observeTexts(), dao.observeTagLinks()) { entries, texts, links ->
            val textsBy = texts.groupBy { it.entryId }
            val tagsBy = links.groupBy({ it.entryId }, { it.name })
            entries.map { e ->
                LibraryItem(e, textsBy[e.id].orEmpty().associate { it.lang to it.text }, tagsBy[e.id].orEmpty().sorted())
            }
        }

    suspend fun queue(now: Long, dayStart: Long, newPerDay: Int, maxReviews: Int = 50): List<DueCard> {
        val reviews = dao.dueReviewed(now, maxReviews)
        val newLeft = (newPerDay - dao.introducedToday(dayStart)).coerceAtLeast(0)
        val fresh = if (newLeft > 0) dao.dueNew(now, newLeft) else emptyList()
        return interleave(reviews, fresh).map { c ->
            DueCard(c, dao.textsFor(c.entryId).associate { it.lang to it.text })
        }
    }

    suspend fun putText(entryId: Long, lang: Lang, text: String, origin: TextOrigin) =
        dao.upsertText(EntryTextEntity(entryId, lang, text.trim(), origin))

    suspend fun applyReview(card: ReviewCardEntity, newState: CardState, log: ReviewLogEntity) =
        db.withTransaction {
            dao.updateCard(card.withState(newState))
            dao.insertLog(log)
        }

    suspend fun deleteEntry(id: Long) = dao.deleteEntry(id)
    suspend fun updateNote(id: Long, note: String) = dao.updateNote(id, note)

    suspend fun exportAll(): BackupData = db.withTransaction {
        BackupData(
            entries = dao.allEntries(), texts = dao.allTexts(), cards = dao.allCards(),
            logs = dao.allLogs(), tags = dao.allTags(), entryTags = dao.allEntryTags(),
        )
    }

    suspend fun importAll(d: BackupData) = db.withTransaction {
        dao.deleteAllEntries()
        dao.deleteAllTags()
        dao.insertEntries(d.entries)
        dao.insertTexts(d.texts)
        dao.insertCards(d.cards)
        dao.insertLogs(d.logs)
        dao.insertTags(d.tags)
        dao.insertEntryTags(d.entryTags)
    }
}
