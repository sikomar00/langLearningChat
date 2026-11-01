package com.chatlearn.settings

import com.chatlearn.domain.Lang
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppearanceTest {
    @Test fun themeModeOverridesTheSystemOnlyWhenChosen() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromName("??"))
    }

    @Test fun accentPicksTheLocaleTag() {
        assertEquals("en-US", VoicePrefs().tag(Lang.EN))
        assertEquals("en-GB", VoicePrefs(en = EnAccent.UK).tag(Lang.EN))
        assertEquals("es-ES", VoicePrefs().tag(Lang.ES))
        assertEquals("es-US", VoicePrefs(es = EsAccent.LATAM).tag(Lang.ES))
        assertEquals("ko-KR", VoicePrefs().tag(Lang.KO))
    }

    @Test fun voicePrefsFallBackToDefaultsForUnknownNames() {
        val v = voicePrefsOf(enAccent = "UK", esAccent = "nope", rate = 0.8f)
        assertEquals(EnAccent.UK, v.en)
        assertEquals(EsAccent.ES, v.es)
        assertEquals(0.8f, v.rate)
    }

    @Test fun backupStatusSaysWhenItWasLastDone() {
        val zone = ZoneId.of("Asia/Seoul")
        val now = LocalDateTime.of(2026, 11, 2, 9, 0).atZone(zone).toInstant().toEpochMilli()
        val day = 86_400_000L
        assertEquals("아직 백업한 적이 없어요", backupStatus(0, now, zone))
        assertEquals("마지막 백업: 오늘", backupStatus(now - 3_600_000, now, zone))
        assertEquals("마지막 백업: 어제", backupStatus(now - day, now, zone))
        assertEquals("마지막 백업: 20일 전", backupStatus(now - 20 * day, now, zone))
    }

    @Test fun backupIsOverdueOnlyWhenThereIsSomethingToLose() {
        val day = 86_400_000L
        val now = 100 * day
        assertFalse(backupOverdue(0, now, entries = 3)) // 문장이 적으면 재촉하지 않는다
        assertTrue(backupOverdue(0, now, entries = 10))
        assertFalse(backupOverdue(now - 13 * day, now, entries = 50))
        assertTrue(backupOverdue(now - 15 * day, now, entries = 50))
    }
}
