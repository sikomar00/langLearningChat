package com.chatlearn.ui.translate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.ai.*
import com.chatlearn.data.*
import com.chatlearn.domain.Direction
import com.chatlearn.domain.Lang
import com.chatlearn.domain.normalizeKeyPhrase
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

sealed interface LookupState {
    val word: String
    data class Loading(override val word: String) : LookupState
    data class Done(override val word: String, val info: WordInfo) : LookupState
    data class Failed(override val word: String, val kind: AiError) : LookupState
}

sealed interface Notice {
    data class Saved(val cards: Int) : Notice
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
    val keyPhrase: String = "", // 받은 말의 핵심 표현(저장 전에 고칠 수 있다)
    val lookup: LookupState? = null, // 눌러 본 단어의 뜻
)

class TranslateViewModel(
    private val ai: AiService,
    private val repo: EntryRepository,
    private val clock: () -> Long,
    private val useSaved: suspend () -> Boolean = { true },
    private val autoExpand: suspend () -> Boolean = { true },
) : ViewModel() {
    private val _state = MutableStateFlow(TranslateUiState())
    val state: StateFlow<TranslateUiState> = _state.asStateFlow()

    fun setMode(m: TranslateMode) = _state.update {
        it.copy(mode = m, received = null, lookup = null, candidates = emptyList(), error = null, notice = null)
    }
    fun setInput(s: String) = _state.update { it.copy(input = s) }

    /** 입력과 결과, 안내 문구를 모두 비운다. 태그와 모드는 그대로 둔다. */
    fun clearInput() = _state.update {
        it.copy(input = "", source = "", received = null, lookup = null, candidates = emptyList(), error = null, notice = null)
    }

    /** 붙여넣기: 입력을 통째로 바꾸고, 이전 입력의 결과는 지운다. */
    fun replaceInput(text: String) = _state.update {
        it.copy(input = text.trim(), source = "", received = null, lookup = null, candidates = emptyList(), error = null, notice = null)
    }
    /** 다른 앱에서 공유한 메시지: 받은 말 모드로 바꾸고 바로 번역한다. */
    fun receiveShared(text: String): Job {
        _state.update {
            it.copy(mode = TranslateMode.RECEIVED, input = text.trim(), source = "", received = null, lookup = null, candidates = emptyList(), error = null, notice = null)
        }
        return translate()
    }
    /** 받은 말의 단어를 눌렀을 때: 문장 맥락에서의 뜻을 AI에 묻는다. */
    fun lookupWord(chunk: String): Job = viewModelScope.launch {
        val word = cleanWord(chunk) ?: return@launch
        val s = _state.value
        val lang = s.received?.detectedLang ?: return@launch
        if ((s.lookup as? LookupState.Loading)?.word == word) return@launch
        _state.update { it.copy(lookup = LookupState.Loading(word)) }
        val result = ai.lookupWord(word, s.source, lang)
        // 그 사이 다른 번역·저장으로 화면이 바뀌었으면 결과를 버린다.
        if (_state.value.source != s.source) return@launch
        _state.update {
            it.copy(lookup = result.fold(
                onSuccess = { info -> LookupState.Done(word, info) },
                onFailure = { e -> LookupState.Failed(word, (e as? AiException)?.kind ?: AiError.SERVER) },
            ))
        }
    }

    fun dismissLookup() = _state.update { it.copy(lookup = null) }
    fun useAsKeyPhrase(word: String) = _state.update { it.copy(keyPhrase = word) }
    fun setKeyPhrase(s: String) = _state.update { it.copy(keyPhrase = s) }
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
        _state.update { it.copy(loading = true, source = text, error = null, notice = null, received = null, lookup = null, candidates = emptyList()) }
        if (s.mode == TranslateMode.RECEIVED) {
            ai.translateReceived(text).fold(
                onSuccess = { r ->
                    if (r.detectedLang == null) _state.update { it.copy(loading = false, error = TranslateError.UnsupportedLang) }
                    else _state.update { it.copy(loading = false, received = r, keyPhrase = normalizeKeyPhrase(text, r.keyPhrase).orEmpty()) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.toUiError()) } },
            )
        } else {
            ai.suggestSent(text, s.targetLang, if (useSaved()) repo.knownExpressions(s.targetLang) else emptyList()).fold(
                onSuccess = { list -> _state.update { it.copy(loading = false, candidates = list) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.toUiError()) } },
            )
        }
    }

    fun saveReceived(): Job = viewModelScope.launch {
        val s = _state.value
        val r = s.received ?: return@launch
        val lang = r.detectedLang ?: return@launch
        val draft = EntryDraft(Direction.RECEIVED, lang, s.source, r.koTranslation, r.note, null, parseTags(s.tag), r.altText,
            keyPhrase = s.keyPhrase.ifBlank { null }, altKeyPhrase = r.altKeyPhrase)
        report(repo.saveEntry(draft, clock(), autoExpand()))
    }

    fun onCandidateCopied(c: SentCandidate): Job = viewModelScope.launch {
        val s = _state.value
        val draft = EntryDraft(Direction.SENT, s.targetLang, c.text, s.source, c.toneNote, null, parseTags(s.tag), c.altText,
            keyPhrase = c.keyPhrase, altKeyPhrase = c.altKeyPhrase)
        report(repo.saveEntry(draft, clock(), autoExpand()))
    }

    // 저장에 성공하면 다음 표현을 바로 입력할 수 있게 입력과 결과를 비운다. 중복이면 그대로 둔다.
    private suspend fun report(r: SaveResult) {
        val cards = if (r is SaveResult.Saved) repo.cardCount(r.id) else 0
        _state.update {
            if (r is SaveResult.Saved) {
                it.copy(input = "", source = "", received = null, lookup = null, candidates = emptyList(), error = null, notice = Notice.Saved(cards), keyPhrase = "")
            } else {
                it.copy(notice = Notice.Duplicate)
            }
        }
    }

    private fun Throwable.toUiError() = TranslateError.Ai((this as? AiException)?.kind ?: AiError.SERVER)
}
