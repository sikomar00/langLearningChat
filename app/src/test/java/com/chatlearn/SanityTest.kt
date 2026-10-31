package com.chatlearn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class SanityTest {
    @Test
    fun appContextHasExpectedPackage() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.chatlearn", ctx.packageName)
    }
}
