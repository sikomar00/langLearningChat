package com.chatlearn.ui.review

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewMessagesTest {
    // 하루 새 카드 한도가 남아 있으면 저장한 문장이 오늘 바로 나오므로 "내일부터"라고 하면 틀린 안내다.
    @Test fun emptyMessageDoesNotPromiseTomorrow() {
        assertEquals("오늘 복습할 카드를 모두 끝냈어요.\n새 문장을 저장하면 복습에 나와요.", emptyReviewMessage(custom = false, newWaiting = 0))
    }

    // 기다리는 새 카드가 있으면 아래 "더 배우기"가 안내하므로 저장하라는 말은 뺀다.
    @Test fun emptyMessageLeavesWaitingCardsToLearnMore() {
        assertEquals("오늘 복습할 카드를 모두 끝냈어요.", emptyReviewMessage(custom = false, newWaiting = 3))
    }

    @Test fun customReviewWithNothingDue() {
        assertEquals("고른 문장에 복습할 카드가 없어요.", emptyReviewMessage(custom = true, newWaiting = 3))
    }
}
