package com.chatlearn

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
class ManifestTest {
    @Test
    fun autoBackupIsOffSoARestoredPhoneNeverFindsOrphanedEncryptedPrefs() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(0, ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
    }
}
