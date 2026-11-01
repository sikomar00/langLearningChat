package com.chatlearn.ui.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryNoticeTest {
    @Test fun aFolderMoveNoticeGoesAwayByItself() {
        val n = folderMoveNotice(2, "Travel")
        assertEquals("2개를 'Travel' 폴더로 옮겼어요.", n.text)
        assertFalse(n.sticky)
        assertEquals("1개를 폴더에서 꺼냈어요.", folderMoveNotice(1, null).text)
    }

    @Test fun anAiFailureStaysUntilTapped() {
        assertEquals(LibraryNotice(AI_FAILED_HINT, sticky = true), expandNotice(created = 0, failedBatches = 2))
        assertEquals(LibraryNotice(AI_FAILED_HINT, sticky = true), keyPhraseNotice(filled = 0, none = 0, failedBatches = 1))
    }

    // 일부 묶음만 실패하면 "다시 눌러 보세요"를 읽을 수 있게 남긴다.
    @Test fun aPartialFailureStaysSoTheRetryHintCanBeRead() {
        val n = expandNotice(created = 3, failedBatches = 1)
        assertEquals("다른 언어 카드 3개를 만들었어요. (실패한 묶음 1개는 다시 눌러 보세요)", n.text)
        assertTrue(n.sticky)
    }

    @Test fun aFullSuccessGoesAwayByItself() {
        assertEquals(LibraryNotice("다른 언어 카드 3개를 만들었어요."), expandNotice(created = 3, failedBatches = 0))
        assertEquals(LibraryNotice("핵심 표현 2개를 찾았어요."), keyPhraseNotice(filled = 2, none = 1, failedBatches = 0))
    }
}
