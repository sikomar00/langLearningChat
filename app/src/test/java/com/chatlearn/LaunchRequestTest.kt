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

    /** 바로가기(res/xml/shortcuts.xml)의 동작·target이 모두 실제 화면으로 이어지는지. */
    @Test fun everyLauncherShortcutOpensAScreen() {
        val xml = javax.xml.parsers.DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(java.io.File("src/main/res/xml/shortcuts.xml"))
        val ns = "http://schemas.android.com/apk/res/android"
        val intents = xml.getElementsByTagName("intent")
        assertEquals(4, intents.length)
        for (i in 0 until intents.length) {
            val e = intents.item(i) as org.w3c.dom.Element
            val extra = e.getElementsByTagName("extra").item(0) as org.w3c.dom.Element
            assertEquals(EXTRA_TARGET, extra.getAttributeNS(ns, "name"))
            val intent = Intent(e.getAttributeNS(ns, "action")).putExtra(EXTRA_TARGET, extra.getAttributeNS(ns, "value"))
            kotlin.test.assertNotNull(launchRequestOf(intent), "바로가기 ${extra.getAttributeNS(ns, "value")}")
        }
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
