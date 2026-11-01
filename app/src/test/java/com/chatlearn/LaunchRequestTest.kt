package com.chatlearn

import android.content.Intent
import com.chatlearn.ui.translate.TranslateMode
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
class LaunchRequestTest {
    private fun open(target: String) = Intent(ACTION_OPEN).putExtra(EXTRA_TARGET, target)

    @Test fun reminderAndShortcutTargetsMapToRequests() {
        assertEquals(LaunchRequest.Review, launchRequestOf(open("review")))
        assertEquals(LaunchRequest.PasteTranslate, launchRequestOf(open("paste")))
        assertEquals(LaunchRequest.Compose(TranslateMode.SENT), launchRequestOf(open("sent")))
        assertEquals(LaunchRequest.Compose(TranslateMode.CORRECT), launchRequestOf(open("correct")))
    }

    @Test fun sharedTextWins() {
        val i = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, " ¿Qué tal? ")
        assertEquals(LaunchRequest.Shared("¿Qué tal?"), launchRequestOf(i))
    }

    @Test fun plainLaunchOrUnknownTargetIsNothing() {
        assertNull(launchRequestOf(Intent(Intent.ACTION_MAIN)))
        assertNull(launchRequestOf(open("nope")))
        assertNull(launchRequestOf(null))
    }
}
