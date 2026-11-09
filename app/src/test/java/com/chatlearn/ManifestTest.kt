package com.chatlearn

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import com.chatlearn.data.AutoBackup
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 지키려는 것: 암호화된 API 키 저장소(Keystore에 묶임)가 새 폰으로 복원되어 "고아"가 되는 일이 없어야 한다.
 * 예전에는 자동 백업을 통째로 꺼서 막았다. 지금은 스냅샷 폴더만 백업하되, 그 규칙을 이 테스트가 직접 검사한다.
 */
@RunWith(RobolectricTestRunner::class)
class ManifestTest {
    private data class Rule(val section: String, val kind: String, val domain: String, val path: String)

    private fun rules(resId: Int): List<Rule> {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val parser = ctx.resources.getXml(resId)
        val sections = setOf("full-backup-content", "cloud-backup", "device-transfer")
        var section = ""
        val out = mutableListOf<Rule>()
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when (val tag = parser.name) {
                in sections -> section = tag
                "include", "exclude" ->
                    out += Rule(section, tag, parser.getAttributeValue(null, "domain").orEmpty(), parser.getAttributeValue(null, "path").orEmpty())
            }
        }
        return out
    }

    /**
     * 한 구역(예: cloud-backup)의 규칙이 "스냅샷 폴더 포함" 하나뿐인지.
     * <include>가 있으면 그 밖의 파일(DB, shared_prefs의 암호화 키 저장소)은 백업되지 않는다.
     * 그래서 다른 include가 하나라도 생기면(예: sharedpref) 키 저장소가 따라갈 수 있으므로 여기서 막는다.
     */
    private fun assertOnlySnapshotTravels(all: List<Rule>, section: String) {
        assertEquals(
            listOf(Rule(section, "include", "file", AutoBackup.DIR_NAME)),
            all.filter { it.section == section },
            "$section: 규칙은 스냅샷 폴더 포함 하나뿐이어야 한다",
        )
    }

    @Test fun autoBackupIsOnButOnlyForTheSnapshotRules() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(ctx.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
    }

    @Test fun androidElevenAndOlderRulesCarryOnlyTheSnapshot() {
        assertOnlySnapshotTravels(rules(R.xml.backup_rules), "full-backup-content")
    }

    @Test fun androidTwelveAndNewerRulesCarryOnlyTheSnapshotForCloudBackupAndDeviceTransfer() {
        val all = rules(R.xml.data_extraction_rules)
        assertOnlySnapshotTravels(all, "cloud-backup")
        assertOnlySnapshotTravels(all, "device-transfer")
    }

    @Test fun theManifestPointsAtBothRuleFiles() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
        assertTrue(manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""))
    }
}
