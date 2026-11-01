package com.chatlearn.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.EntryRepository
import com.chatlearn.data.LibraryItem
import com.chatlearn.data.RenameResult
import com.chatlearn.data.StatsInput
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
