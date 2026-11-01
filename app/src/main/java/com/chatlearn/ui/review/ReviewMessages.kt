package com.chatlearn.ui.review

/**
 * 복습할 카드가 없을 때의 안내. 하루 새 카드 한도가 남아 있으면 저장한 문장이 오늘 바로 나오므로 "내일부터"라고 하지 않는다.
 * 기다리는 새 카드가 있으면 아래 "더 배우기"가 안내하므로 저장하라는 말은 뺀다.
 */
fun emptyReviewMessage(custom: Boolean, newWaiting: Int): String = when {
    custom -> "고른 문장에 복습할 카드가 없어요."
    newWaiting > 0 -> "오늘 복습할 카드를 모두 끝냈어요."
    else -> "오늘 복습할 카드를 모두 끝냈어요.\n새 문장을 저장하면 복습에 나와요."
}
