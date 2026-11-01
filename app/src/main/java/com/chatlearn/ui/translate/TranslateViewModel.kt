package com.chatlearn.ui.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TranslateMode { RECEIVED, SENT }

sealed interface TranslateError {
    data object UnsupportedLang : TranslateError
    data class Ai(val kind: AiError) : TranslateError
}

sealed interface Notice {
    data object Saved : Notice
    data object Duplicate : Notice
}

data class TranslateUiState(
    val mode: TranslateMode = TranslateMode.RECEIVED,
    val input: String = "",
    val source: String = "", // 번역을 요청한 시점의 입력. 저장은 이 값으로 한다.
    val tag: String = "",
    val targetLang: Lang = Lang.EN,
    val loading: Boolean = false,
    val received: ReceivedResult? = null,
    val candidates: List<SentCandidate> = emptyList(),
    val error: TranslateError? = null,
    val notice: Notice? = null,
)

class TranslateViewModel(
    private val ai: AiService,
    private val repo: EntryRepository,
    private val clock: () -> Long,
    private val autoExpand: suspend () -> Boolean = { true },
) : ViewModel() {
    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    fun setMode(m: TranslateMode) = _state.update {
        it.copy(mode = m, received = null, candidates = emptyList(), error = null, notice = null)
    }
    fun setInput(s: String) = _state.update { it.copy(input = s) }

    /** 입력과 결과, 안내 문구를 모두 비운다. 태그와 모드는 그대로 둔다. */
    fun clearInput() = _state.update {
        it.copy(input = "", source = "", received = null, candidates = emptyList(), error = null, notice = null)
    }

    /** 붙여넣기: 입력을 통째로 바꾸고, 이전 입력의 결과는 지운다. */
    fun replaceInput(text: String) = _state.update {
        it.copy(input = text.trim(), source = "", received = null, candidates = emptyList(), error = null, notice = null)
    }
    /** 다른 앱에서 공유한 메시지: 받은 말 모드로 바꾸고 바로 번역한다. */
    fun receiveShared(text: String): Job {
        _state.update {
            it.copy(mode = TranslateMode.RECEIVED, input = text.trim(), source = "", received = null, candidates = emptyList(), error = null, notice = null)
        }
        return translate()
    }
    fun setTag(s: String) = _state.update { it.copy(tag = s) }
    fun pickTag(tag: String) = _state.update { it.copy(tag = applyTagSuggestion(it.tag, tag)) }

    /** 입력창 포커스 시 보여 줄 자동완성 후보(최근 사용 순, 입력한 글자로 걸러짐). */
    val tagSuggestions: StateFlow<List<String>> =
        combine(repo.observeRecentTags(), _state.map { it.tag }.distinctUntilChanged()) { recent, typed ->
            suggestTags(recent, typed)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun setTarget(l: Lang) = _state.update { it.copy(targetLang = l, candidates = emptyList()) }

    fun translate(): Job = viewModelScope.launch {
        val s = _state.value
        val text = s.input.trim()
        if (text.isEmpty() || s.loading) return@launch
        _state.update { it.copy(loading = true, source = text, error = null, notice = null, received = null, candidates = emptyList()) }
        if (s.mode == TranslateMode.RECEIVED) {
            ai.translateReceived(text).fold(
                onSuccess = { r ->
                    if (r.detectedLang == null) _state.update { it.copy(loading = false, error = TranslateError.UnsupportedLang) }
                    else _state.update { it.copy(loading = false, received = r) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.toUiError()) } },
            )
        } else {
            ai.suggestSent(text, s.targetLang).fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, candidates = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.toUiError()) } },
            )
        }
    }

    fun saveReceived(): Job = viewModelScope.launch {
        val s = _state.value
        val r = s.received ?: return@launch
        val lang = r.detectedLang ?: return@launch
        val draft = EntryDraft(Direction.RECEIVED, lang, s.source, r.koTranslation, r.note, null, parseTags(s.tag), r.altText)
        report(repo.saveEntry(draft, clock(), autoExpand()))
    }

    fun onCandidateCopied(c: SentCandidate): Job = viewModelScope.launch {
        val s = _state.value
        val draft = EntryDraft(Direction.SENT, s.targetLang, c.text, s.source, c.toneNote, null, parseTags(s.tag), c.altText)
        report(repo.saveEntry(draft, clock(), autoExpand()))
    }

    // 저장에 성공하면 다음 표현을 바로 입력할 수 있게 입력과 결과를 비운다. 중복이면 그대로 둔다.
    private fun report(r: SaveResult) = _state.update {
        if (r is SaveResult.Saved) {
            it.copy(input = "", source = "", received = null, candidates = emptyList(), error = null, notice = Notice.Saved)
        } else {
            it.copy(notice = Notice.Duplicate)
        }
    }

    private fun Throwable.toUiError() = TranslateError.Ai((this as? AiException)?.kind ?: AiError.SERVER)
}
