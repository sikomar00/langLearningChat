package com.chatlearn.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatlearn.data.DueCard
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
    data class Prompt(val item: ReviewItem, val answer: String = "", val checking: Boolean = false) : ReviewUi
    data class Graded(val item: ReviewItem, val outcome: GradeOutcome, val answer: String, val suggested: Rating) : ReviewUi
    data class Done(val count: Int) : ReviewUi
}

class ReviewViewModel(private val session: ReviewSession) : ViewModel() {
    private val _state = MutableStateFlow<ReviewUi>(ReviewUi.Loading)
    val state: StateFlow<ReviewUi> = _state.asStateFlow()

    private val queue = ArrayDeque<DueCard>()
    private var reviewed = 0

    fun start(): Job = viewModelScope.launch {
        _state.value = ReviewUi.Loading
        reviewed = 0
        queue.clear()
        queue.addAll(session.loadQueue())
        if (queue.isEmpty()) _state.value = ReviewUi.Empty else showNext()
    }

    private suspend fun showNext() {
        val next = queue.removeFirstOrNull()
        _state.value = if (next == null) ReviewUi.Done(reviewed) else ReviewUi.Prompt(session.prepare(next))
    }

    fun setAnswer(s: String) {
        (_state.value as? ReviewUi.Prompt)?.let { _state.value = it.copy(answer = s) }
    }

    fun submit(): Job = viewModelScope.launch {
        val p = _state.value as? ReviewUi.Prompt ?: return@launch
        if (p.answer.isBlank() || p.checking) return@launch
        _state.value = p.copy(checking = true)
        val outcome = session.grade(p.item, p.answer)
        val suggested = (outcome as? GradeOutcome.Ai)?.suggested ?: Rating.GOOD
        _state.value = ReviewUi.Graded(p.item, outcome, p.answer, suggested)
    }

    fun rate(r: Rating): Job = viewModelScope.launch {
        val g = _state.value as? ReviewUi.Graded ?: return@launch
        val updated = session.rate(g.item, r, g.outcome, g.answer)
        reviewed++
        // 다시: 갱신된 카드 상태로 같은 세션 끝에 한 번 더 낸다
        if (r == Rating.AGAIN) queue.addLast(DueCard(updated, g.item.texts))
        showNext()
    }
}
