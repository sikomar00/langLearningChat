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

class ReviewViewModel(private val session: ReviewSession) : ViewModel() {
    private val _state = MutableStateFlow<ReviewUi>(ReviewUi.Loading)
    val state: StateFlow<ReviewUi> = _state.asStateFlow()

    /** 라이브러리에서 고른 복습이면 그 카드 수, 평소 복습이면 null. */
    private val _custom = MutableStateFlow<Int?>(null)
    val custom: StateFlow<Int?> = _custom.asStateFlow()

    private val queue = ArrayDeque<DueCard>()
    private var reviewed = 0
    private var started = false
    private var rating = false

    /** 화면이 다시 그려져도(탭 이동, 회전) 진행 중인 복습을 되돌리지 않는다. */
    fun ensureStarted(): Job {
        if (started) return Job().apply { complete() }
        return start()
    }

    fun start(): Job = viewModelScope.launch {
        started = true
        _state.value = ReviewUi.Loading
        _custom.value = null
        reviewed = 0
        queue.clear()
        queue.addAll(session.loadQueue())
        if (queue.isEmpty()) _state.value = ReviewUi.Empty else showNext()
    }

    /** 라이브러리에서 고른 문장만 복습한다. 채점과 일정 반영은 평소 복습과 같다. */
    fun startWith(entryIds: Set<Long>, answerLang: Lang?): Job = viewModelScope.launch {
        started = true
        _state.value = ReviewUi.Loading
        reviewed = 0
        queue.clear()
        queue.addAll(session.loadSelected(entryIds, answerLang))
        _custom.value = queue.size
        if (queue.isEmpty()) _state.value = ReviewUi.Empty else showNext()
    }

    private suspend fun showNext() {
        val next = queue.removeFirstOrNull()
        _state.value = if (next == null) ReviewUi.Done(reviewed) else {
            val item = session.prepare(next)
            ReviewUi.Prompt(item, List(item.blankCount) { "" }, session.now())
        }
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
                // 다시: 갱신된 카드 상태로 같은 세션 끝에 한 번 더 낸다
                if (r == Rating.AGAIN) queue.addLast(DueCard(updated, g.item.texts))
                showNext()
            } finally {
                rating = false
            }
        }
    }
}
