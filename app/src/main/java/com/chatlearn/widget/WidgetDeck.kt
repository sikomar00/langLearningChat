package com.chatlearn.widget

import com.chatlearn.data.DueCard
import com.chatlearn.domain.Lang
import com.chatlearn.domain.Rating
import com.chatlearn.domain.otherForeign
import com.chatlearn.review.GradeOutcome
import com.chatlearn.review.ReviewItem
import com.chatlearn.review.ReviewSession

/** 위젯 한 장 복습: [prompt](보통 한국어 뜻)를 보고 [answer]를 떠올린다. [left]는 이 카드를 포함한 오늘 남은 수. */
data class WidgetCard(val cardId: Long, val prompt: String, val answer: String, val answerLang: Lang, val left: Int)

/** 오늘 복습 큐의 첫 카드. 다 했으면 null. */
suspend fun nextWidgetCard(session: ReviewSession): WidgetCard? {
    val queue = session.loadQueue()
    val due = queue.firstOrNull() ?: return null
    return WidgetCard(due.card.id, due.texts[promptLangOf(due)].orEmpty(), due.texts.getValue(due.card.answerLang), due.card.answerLang, queue.size)
}

/** 위젯에서 스스로 매긴 평가를 저장한다. 그사이 앱에서 복습해 큐에 없으면 false. */
suspend fun rateWidgetCard(session: ReviewSession, cardId: Long, rating: Rating): Boolean {
    val due = session.loadQueue().firstOrNull { it.card.id == cardId } ?: return false
    val item = ReviewItem(due.card, due.texts, promptLangOf(due), keyPhrases = due.keyPhrases, note = due.note, context = due.context, kind = due.kind)
    return session.rate(item, rating, GradeOutcome.SelfGrade(item.reference, null), answer = "") != null
}

// 한국어 뜻이 비어 있고 다른 외국어 문장이 있으면 그것을 보여 준다.
private fun promptLangOf(due: DueCard): Lang {
    val other = due.card.answerLang.otherForeign()
    return if (due.texts[Lang.KO].isNullOrBlank() && !due.texts[other].isNullOrBlank()) other else Lang.KO
}
