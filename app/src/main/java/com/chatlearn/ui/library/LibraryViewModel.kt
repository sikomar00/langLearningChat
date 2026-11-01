package com.chatlearn.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.EntryRepository
import com.chatlearn.data.LibraryItem
import com.chatlearn.data.RenameResult
import com.chatlearn.data.StatsInput
import com.chatlearn.expansion.ExpansionReport
import com.chatlearn.expansion.ExpansionService
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import com.chatlearn.domain.Lang
import com.chatlearn.domain.TextOrigin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

fun filterLibrary(items: List<LibraryItem>, query: String, lang: Lang?, tag: String?): List<LibraryItem> {
    val q = query.trim()
    return items.filter { item ->
        (lang == null || item.entry.chatLang == lang) &&
            (tag == null || tag in item.tags) &&
            (q.isEmpty() || item.texts.values.any { it.contains(q, ignoreCase = true) })
    }
}

class LibraryViewModel(
    private val repo: EntryRepository,
    private val expansion: ExpansionService,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    val query = MutableStateFlow("")
    val langFilter = MutableStateFlow<Lang?>(null)
    val tagFilter = MutableStateFlow<String?>(null)

    private val all = repo.observeLibrary()
    val items: StateFlow<List<LibraryItem>> =
        combine(all, query, langFilter, tagFilter) { l, q, lang, tag -> filterLibrary(l, q, lang, tag) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTags: StateFlow<List<String>> =
        all.map { list -> list.flatMap { it.tags }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 문장별 통계(복습 횟수, 기억 비율 등)를 계산할 재료. 상세 창에서 쓴다. */
    val statsInput: StateFlow<StatsInput?> =
        repo.observeStatsInput().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 반대 언어 카드까지 가진(=확장된) 문장의 id. 텍스트만 있고 카드가 없는 문장은 포함되지 않는다. */
    val expandedIds: StateFlow<Set<Long>> = repo.observeStatsInput()
        .map { si -> si.cards.groupingBy { it.entryId }.eachCount().filterValues { it >= 2 }.keys }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** 반대 언어 카드가 아직 없는 문장 수. "전체 확장" 버튼 표시에 쓴다. */
    val unexpanded: StateFlow<Int> = combine(all, expandedIds) { items, ids -> items.count { it.entry.id !in ids } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private var expandRun: Deferred<ExpansionReport>? = null
    private val _expanding = MutableStateFlow(false)
    /** 전체 확장이 진행 중인가. 화면을 벗어났다 돌아와도 유지된다. */
    val expanding: StateFlow<Boolean> = _expanding.asStateFlow()

    /** 이미 실행 중이면 그 실행을 그대로 돌려줘서 같은 번역 요청이 중복되지 않게 한다. 예외는 실패한 묶음 1개로 보고한다. */
    fun expandAll(): Deferred<ExpansionReport> {
        expandRun?.takeIf { it.isActive }?.let { return it }
        val run = viewModelScope.async {
            _expanding.value = true
            try {
                runCatching { expansion.expandAll() }.getOrElse { ExpansionReport(created = 0, skipped = 0, failedBatches = 1) }
            } finally {
                _expanding.value = false
            }
        }
        expandRun = run
        return run
    }
    fun expandOne(id: Long) = viewModelScope.async { expansion.expandOne(id) }

    /** 태그 자동완성 후보(최근 사용 순). */
    val recentTags: StateFlow<List<String>> =
        repo.observeRecentTags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // 필터로 선택해 둔 태그가 사라지면(마지막 문장에서 제거, 이름 변경 등) 필터를 풀어서 빈 목록에 갇히지 않게 한다.
        viewModelScope.launch { all.collect { l -> dropStaleTagFilter(l.flatMap { it.tags }) } }
    }

    internal fun dropStaleTagFilter(existing: List<String>) {
        val current = tagFilter.value
        if (current != null && current !in existing) tagFilter.value = null
    }

    fun saveTags(id: Long, tags: List<String>) = viewModelScope.launch { repo.setTags(id, tags, clock()) }

    fun renameTag(old: String, new: String) = viewModelScope.async {
        repo.renameTag(old, new).also { r ->
            if (r != RenameResult.Unchanged && tagFilter.value == old) tagFilter.value = new.trim()
        }
    }

    fun delete(id: Long) = viewModelScope.launch { repo.deleteEntry(id) }
    fun saveNote(id: Long, note: String) = viewModelScope.launch { repo.updateNote(id, note) }
    fun saveText(id: Long, lang: Lang, text: String) = viewModelScope.launch {
        if (text.isNotBlank()) repo.putText(id, lang, text, TextOrigin.USER)
    }
}
