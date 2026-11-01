package com.chatlearn.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private var now = 1_700_000_000_000L
    private lateinit var repo: DataStoreSettingsRepository

    private class MemSecret : SecretStore {
        private var v = ""
        override fun get() = v
        override fun set(v: String) { this.v = v }
    }

    @Before fun setUp() {
        val ds = PreferenceDataStoreFactory.create { tmp.newFile("s.preferences_pb") }
        repo = DataStoreSettingsRepository(ds, MemSecret()) { now }
    }

    @Test fun defaults() = runBlocking {
        val s = repo.settings.first()
        assertEquals("", s.apiKey)
        assertEquals("gemini-flash-latest", s.model)
        assertEquals(10, s.newPerDay)
        assertEquals(0, s.aiCallsToday)
        assertTrue(s.autoExpand)
    }

    @Test fun savesApiKeyModelAndLimit() = runBlocking {
        repo.setApiKey("abc"); repo.setModel("m1"); repo.setNewPerDay(5)
        val s = repo.settings.first()
        assertEquals("abc", s.apiKey); assertEquals("m1", s.model); assertEquals(5, s.newPerDay)
        assertEquals("abc", repo.currentApiKey()); assertEquals("m1", repo.currentModel())
    }

    @Test fun callCounterAccumulatesWithinADayAndResetsNextDay() = runBlocking {
        repo.recordAiCall(); repo.recordAiCall()
        assertEquals(2, repo.settings.first().aiCallsToday)
        now += 86_400_000L * 2
        assertEquals(0, repo.settings.first().aiCallsToday)
        repo.recordAiCall()
        assertEquals(1, repo.settings.first().aiCallsToday)
    }

    @Test fun apiKeyDropsWhitespaceAndInvisibleCharacters() = runBlocking {
        repo.setApiKey("  AIza​sy Key\n9 ")
        assertEquals("AIzasyKey9", repo.currentApiKey())
        assertEquals("AIzasyKey9", repo.settings.first().apiKey)
    }

    @Test fun autoExpandCanBeTurnedOffAndOnAgain() = runBlocking {
        repo.setAutoExpand(false)
        assertFalse(repo.settings.first().autoExpand)
        repo.setAutoExpand(true)
        assertTrue(repo.settings.first().autoExpand)
    }

    @Test fun newPerDayRejectsNegative() = runBlocking {
        repo.setNewPerDay(-3)
        assertEquals(0, repo.settings.first().newPerDay)
    }

    @Test fun reminderDefaultsToOffAtEightPm() = runBlocking {
        val s = repo.settings.first()
        assertFalse(s.reminderEnabled)
        assertEquals(20, s.reminderHour)
    }

    @Test fun reminderIsSavedAndHourIsClamped() = runBlocking {
        repo.setReminder(true, 7)
        assertEquals(true to 7, repo.settings.first().let { it.reminderEnabled to it.reminderHour })
        repo.setReminder(true, 99)
        assertEquals(23, repo.settings.first().reminderHour)
        repo.setReminder(false, -3)
        assertEquals(0, repo.settings.first().reminderHour)
    }
}
