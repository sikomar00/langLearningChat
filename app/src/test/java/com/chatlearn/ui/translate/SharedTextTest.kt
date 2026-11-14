package com.chatlearn.ui.translate

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SharedTextTest {
    @Test fun shareIntentYieldsTrimmedText() {
        val i = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "  Hola, ¿qué tal?\n")
        assertEquals("Hola, ¿qué tal?", sharedTextOf(i))
    }

    @Test fun selectionMenuIntentYieldsText() {
        val i = Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT, "See you soon" as CharSequence)
        assertEquals("See you soon", sharedTextOf(i))
    }

    @Test fun blankOrMissingTextIsIgnored() {
        assertNull(sharedTextOf(Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "   ")))
        assertNull(sharedTextOf(Intent(Intent.ACTION_SEND)))
    }

    @Test fun otherIntentsAreIgnored() {
        assertNull(sharedTextOf(Intent(Intent.ACTION_MAIN).putExtra(Intent.EXTRA_TEXT, "x")))
        assertNull(sharedTextOf(null))
    }

    @Test fun onlyAnEditableSelectionCanTakeTheResultBack() {
        assertTrue(canReturnText(Intent(Intent.ACTION_PROCESS_TEXT)))
        assertFalse(canReturnText(Intent(Intent.ACTION_PROCESS_TEXT).putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)))
        assertFalse(canReturnText(Intent(Intent.ACTION_SEND)))
        assertFalse(canReturnText(null))
    }
}
