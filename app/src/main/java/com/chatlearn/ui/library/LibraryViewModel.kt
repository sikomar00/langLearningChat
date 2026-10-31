package com.chatlearn.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.EntryRepository
import com.chatlearn.data.LibraryItem
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

class LibraryViewModel(private val repo: EntryRepository) : ViewModel() {
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

    fun delete(id: Long) = viewModelScope.launch { repo.deleteEntry(id) }
    fun saveNote(id: Long, note: String) = viewModelScope.launch { repo.updateNote(id, note) }
    fun saveText(id: Long, lang: Lang, text: String) = viewModelScope.launch {
        if (text.isNotBlank()) repo.putText(id, lang, text, TextOrigin.USER)
    }
}
