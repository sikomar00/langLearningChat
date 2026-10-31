package com.chatlearn.ui.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
) : ViewModel() {
    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    fun setMode(m: TranslateMode) = _state.update {
        it.copy(mode = m, received = null, candidates = emptyList(), error = null, notice = null)
    }
    fun setInput(s: String) = _state.update { it.copy(input = s) }
    fun setTag(s: String) = _state.update { it.copy(tag = s) }
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
        val draft = EntryDraft(Direction.RECEIVED, lang, s.source, r.koTranslation, r.note, null, listOf(s.tag))
        report(repo.saveEntry(draft, clock()))
    }

    fun onCandidateCopied(c: SentCandidate): Job = viewModelScope.launch {
        val s = _state.value
        val draft = EntryDraft(Direction.SENT, s.targetLang, c.text, s.source, c.toneNote, null, listOf(s.tag))
        report(repo.saveEntry(draft, clock()))
    }

    private fun report(r: SaveResult) = _state.update {
        it.copy(notice = if (r is SaveResult.Saved) Notice.Saved else Notice.Duplicate)
    }

    private fun Throwable.toUiError() = TranslateError.Ai((this as? AiException)?.kind ?: AiError.SERVER)
}
