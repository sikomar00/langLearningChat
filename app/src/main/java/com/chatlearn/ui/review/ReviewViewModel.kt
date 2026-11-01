package com.chatlearn.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.DueCard
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import com.chatlearn.review.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReviewUi {
    data object Loading : ReviewUi
    data object Empty : ReviewUi
    /** 복습을 시작하기 전 요약: 오늘 낼 카드 수와 그중 새 카드 수. */
    data class Ready(val total: Int, val newCards: Int) : ReviewUi
    data class Prompt(
        val item: ReviewItem,
        val answers: List<String>,
        val shownAt: Long,
        val checking: Boolean = false,
    ) : ReviewUi {
        val answer: String get() = answers.joinToString(" ").trim()
    }
    data class Graded(
        val item: ReviewItem,
        val outcome: GradeOutcome,
        val answer: String,
        val suggested: Rating,
        val responseMs: Long,
    ) : ReviewUi
    data class Done(val count: Int) : ReviewUi
}

/** [done]개를 끝냈고 이번 세션에서 낼 카드가 [total]장(다시 평가한 카드 포함). */
data class ReviewProgress(val done: Int, val total: Int)

class ReviewViewModel(private val session: ReviewSession) : ViewModel() {
    private val _state = MutableStateFlow<ReviewUi>(ReviewUi.Loading)
    val state: StateFlow<ReviewUi> = _state.asStateFlow()

    /** 라이브러리에서 고른 복습이면 그 카드 수, 평소 복습이면 null. */
    private val _custom = MutableStateFlow<Int?>(null)
    val custom: StateFlow<Int?> = _custom.asStateFlow()

    private val _progress = MutableStateFlow(ReviewProgress(0, 0))
    val progress: StateFlow<ReviewProgress> = _progress.asStateFlow()

    /** 방금 한 평가를 취소할 수 있는가. */
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private var lastGraded: ReviewUi.Graded? = null

    private val queue = ArrayDeque<DueCard>()
    private var reviewed = 0
    private var started = false
    private var rating = false

    /** 화면이 다시 그려져도(탭 이동, 회전) 진행 중인 복습을 되돌리지 않는다. */
    fun ensureStarted(): Job {
        if (started) return Job().apply { complete() }
        return start(preview = true)
    }

    /** [preview]면 첫 카드를 바로 내지 않고 요약(Ready)을 먼저 보여 준다. */
    fun start(preview: Boolean = false): Job = viewModelScope.launch {
        started = true
        _state.value = ReviewUi.Loading
        _custom.value = null
        clearUndo()
        reviewed = 0
        queue.clear()
        queue.addAll(session.loadQueue())
        if (queue.isEmpty()) {
            _progress.value = ReviewProgress(0, 0); _state.value = ReviewUi.Empty
        } else if (preview) {
            _progress.value = ReviewProgress(0, queue.size)
            _state.value = ReviewUi.Ready(queue.size, queue.count { it.card.lastReviewedAt == null })
        } else showNext()
    }

    /** 요약 화면에서 "시작"을 눌렀을 때. */
    fun begin(): Job = viewModelScope.launch {
        if (_state.value is ReviewUi.Ready) showNext()
    }

    /** 라이브러리에서 고른 문장만 복습한다. 채점과 일정 반영은 평소 복습과 같다. */
    fun startWith(entryIds: Set<Long>, answerLang: Lang?): Job = viewModelScope.launch {
        started = true
        _state.value = ReviewUi.Loading
        clearUndo()
        reviewed = 0
        queue.clear()
        queue.addAll(session.loadSelected(entryIds, answerLang))
        _custom.value = queue.size
        if (queue.isEmpty()) { _progress.value = ReviewProgress(0, 0); _state.value = ReviewUi.Empty } else showNext()
    }

    private suspend fun showNext() {
        val next = queue.removeFirstOrNull()
        _progress.value = ReviewProgress(reviewed, reviewed + queue.size + if (next == null) 0 else 1)
        _state.value = if (next == null) ReviewUi.Done(reviewed) else {
            val item = session.prepare(next)
            ReviewUi.Prompt(item, List(item.blankCount) { "" }, session.now())
        }
    }

    private fun clearUndo() { lastGraded = null; _canUndo.value = false }

    private fun refreshProgress() {
        val cur = _state.value is ReviewUi.Prompt || _state.value is ReviewUi.Graded
        _progress.value = ReviewProgress(reviewed, reviewed + queue.size + if (cur) 1 else 0)
    }

    /** 방금 한 평가를 취소하고, 그 카드를 평가 직전 화면으로 되돌려 다시 고르게 한다. */
    fun undo(): Job = viewModelScope.launch {
        val g = lastGraded ?: return@launch
        val cur = _state.value
        if (rating || (cur !is ReviewUi.Prompt && cur !is ReviewUi.Done)) return@launch
        if (!session.undoLast()) return@launch
        // 평가 뒤 새로 나온 카드는 큐 맨 앞으로 돌려놓고, "다시"로 뒤에 붙었던 같은 카드는 뺀다.
        if (cur is ReviewUi.Prompt && cur.item.card.id != g.item.card.id) queue.addFirst(DueCard(cur.item.card, cur.item.texts, cur.item.keyPhrases))
        queue.removeAll { it.card.id == g.item.card.id }
        reviewed = (reviewed - 1).coerceAtLeast(0)
        clearUndo()
        _state.value = g
        refreshProgress()
    }

    /** 복습 중 카드 메뉴: 오늘은 건너뛰기(평가 기록 없이 내일로). */
    fun skipToday(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        session.skipToday(p.item.card)
        showNext()
    }

    /** 복습 중 카드 메뉴: 이 문장을 라이브러리에서 지운다(같은 문장의 다른 언어 카드도 큐에서 뺀다). */
    fun deleteCurrent(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        session.deleteEntry(p.item.card.entryId)
        queue.removeAll { it.card.entryId == p.item.card.entryId }
        clearUndo()
        showNext()
    }

    /** 복습 중 카드 메뉴: 정답 문장과 한국어 뜻을 고친다. 같은 카드를 새 내용으로 다시 낸다. */
    fun editCurrent(answerText: String, ko: String): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        queue.addFirst(session.edit(p.item.card, answerText, ko))
        showNext()
    }

    /** 특수문자 버튼: 고른 빈칸(기본 첫 칸)의 끝에 글자를 붙인다. */
    fun appendToAnswer(text: String, index: Int = 0) {
        val p = _state.value as? ReviewUi.Prompt ?: return
        if (index !in p.answers.indices) return
        setAnswer(p.answers[index] + text, index)
    }

    /** 모르겠을 때: 정답을 보여 주고 "다시"를 권한다. 기록은 평가를 누를 때 남는다. */
    fun giveUp(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        _state.value = ReviewUi.Graded(
            p.item, GradeOutcome.SelfGrade(p.item.reference, null), "", Rating.AGAIN, session.now() - p.shownAt,
        )
    }

    /** 지금 카드를 기록 없이 큐 맨 뒤로 보낸다. */
    fun postpone(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.checking) return@launch
        queue.addLast(DueCard(p.item.card, p.item.texts, p.item.keyPhrases))
        showNext()
    }

    fun setAnswer(s: String, index: Int = 0) {
        val p = _state.value as? ReviewUi.Prompt ?: return
        if (index !in p.answers.indices) return
        _state.value = p.copy(answers = p.answers.toMutableList().also { it[index] = s })
    }

    fun speak(slow: Boolean) {
        when (val s = _state.value) {
            is ReviewUi.Prompt -> session.speak(s.item, slow)
            is ReviewUi.Graded -> session.speak(s.item, slow)
            else -> Unit
        }
    }

    fun submit(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.answers.any { it.isBlank() } || p.checking) return@launch
        _state.value = p.copy(checking = true)
        val responseMs = session.now() - p.shownAt // AI 채점 대기 시간은 제외한다
        val outcome = session.grade(p.item, p.answers)
        val suggested = when (outcome) {
            is GradeOutcome.Ai -> outcome.suggested
            is GradeOutcome.Local -> outcome.suggested
            is GradeOutcome.SelfGrade -> Rating.GOOD
        }
        _state.value = ReviewUi.Graded(p.item, outcome, p.answer, suggested, responseMs)
    }

    fun rate(r: Rating): Job {
        val g = _state.value as? ReviewUi.Graded
        // 저장이 끝나기 전의 두 번째 탭은 무시한다(같은 복습이 두 번 기록되지 않게).
        if (g == null || rating) return Job().apply { complete() }
        rating = true
        return viewModelScope.launch {
            try {
                val updated = session.rate(g.item, r, g.outcome, g.answer, g.responseMs)
                reviewed++
                lastGraded = g
                _canUndo.value = true
                // 다시: 갱신된 카드 상태로 같은 세션 끝에 한 번 더 낸다
                if (r == Rating.AGAIN) queue.addLast(DueCard(updated, g.item.texts, g.item.keyPhrases))
                showNext()
            } finally {
                rating = false
            }
        }
    }
}
