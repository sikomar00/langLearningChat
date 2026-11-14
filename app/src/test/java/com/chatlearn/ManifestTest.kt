package com.chatlearn

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class ManifestTest {
    @Test
    fun autoBackupIsOnButTheEncryptedKeysNeverTravel() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertNotEquals(0, ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        // 새 기기에서 풀 수 없는 암호화 키 파일이 복원되면 키 저장소를 열다가 실패한다. 두 규칙 파일 모두에서 빠져야 한다.
        listOf("src/main/res/xml/data_extraction_rules.xml", "src/main/res/xml/backup_rules.xml").forEach { path ->
            val rules = File(path).readText()
            assertTrue(Regex("""exclude domain="sharedpref" path="secrets.xml"""").containsMatchIn(rules), path)
        }
    }
}
