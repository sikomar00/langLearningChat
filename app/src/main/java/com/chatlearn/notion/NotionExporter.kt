package com.chatlearn.notion

import com.chatlearn.data.EntryRepository
import com.chatlearn.data.LibraryItem
import com.chatlearn.domain.Direction
import com.chatlearn.domain.EntryKind
import com.chatlearn.domain.Lang
import com.chatlearn.domain.TagKind
import com.chatlearn.domain.grammarPoint
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/** 보낸 수, 실패한 수, 멈춘 이유(토큰·연결 문제처럼 계속해도 소용없는 오류). */
data class NotionReport(val sent: Int, val failed: Int, val error: NotionError?)

/** 라이브러리 문장을 노션 데이터베이스에 한 줄씩 보낸다. 이미 보낸 문장은 같은 페이지를 고친다. */
class NotionExporter(
    private val repo: EntryRepository,
    private val client: NotionClient,
    private val databaseLink: suspend () -> String,
) {
    suspend fun export(ids: Collection<Long>): NotionReport {
        val dbId = notionIdOf(databaseLink()) ?: return NotionReport(0, ids.size, NotionError.NOT_SHARED)
        val target = client.connect(dbId).getOrElse { return NotionReport(0, ids.size, kindOf(it)) }
        val wanted = ids.toSet()
        // shortcut: 한 문장을 보낼 때도 라이브러리 전체를 읽는다. 문장이 수천 개가 되면 id로 골라 읽는 조회로 바꾼다.
        val items = repo.observeLibrary().first().filter { it.entry.id in wanted }
        var sent = 0
        for ((i, item) in items.withIndex()) {
            val page = pageOf(item)
            // 노션에서 지운 페이지면 고칠 수 없으니 새로 만든다.
            val result = item.entry.notionPageId?.let { old ->
                client.upsert(target, old, page).recoverCatching { e -> if (kindOf(e) == NotionError.NOT_SHARED) client.upsert(target, null, page).getOrThrow() else throw e }
            } ?: client.upsert(target, null, page)
            val pageId = result.getOrElse { e ->
                val kind = kindOf(e)
                // 계속해도 같은 오류가 날 것: 멈추고 알린다(한도는 잠시 뒤 다시 누르면 된다).
                if (kind == NotionError.BAD_TOKEN || kind == NotionError.OFFLINE || kind == NotionError.RATE_LIMIT) return NotionReport(sent, items.size - i, kind)
                null
            } ?: continue
            repo.setNotionPageId(item.entry.id, pageId)
            sent++
        }
        return NotionReport(sent, items.size - sent, null)
    }

    private suspend fun pageOf(item: LibraryItem): NotionPage {
        val e = item.entry
        fun tagsOf(kind: TagKind) = item.tags.filter { (item.tagKinds[it] ?: TagKind.TOPIC) == kind }
        return NotionPage(
            sentence = item.texts[e.chatLang].orEmpty(),
            meaning = item.texts[Lang.KO].orEmpty(),
            lang = if (e.chatLang == Lang.ES) "스페인어" else "영어",
            kind = when { e.kind == EntryKind.WORD -> "단어"; e.direction == Direction.SENT -> "보낸 말"; else -> "받은 말" },
            people = tagsOf(TagKind.PERSON), countries = tagsOf(TagKind.COUNTRY), tags = tagsOf(TagKind.TOPIC),
            grammar = repo.entryGrammar(e.id).mapNotNull { grammarPoint(it)?.nameKo },
            note = e.note,
            savedOn = Instant.ofEpochMilli(e.createdAt).atZone(ZoneId.systemDefault()).toLocalDate(),
        )
    }

    private fun kindOf(e: Throwable) = (e as? NotionException)?.kind ?: NotionError.SERVER
}
