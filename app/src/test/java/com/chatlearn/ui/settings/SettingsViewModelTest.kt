package com.chatlearn.ui.settings

import com.chatlearn.ai.FakeAiService
import com.chatlearn.data.EntryRepository
import com.chatlearn.data.inMemoryDb
import com.chatlearn.settings.DataStoreSettingsRepository
import com.chatlearn.settings.testDataStore
import com.chatlearn.settings.SecretStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SettingsViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    @Before fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun backupTimeIsRecordedOnlyAfterTheFileIsWritten() = runBlocking {
        val db = inMemoryDb()
        val settings = DataStoreSettingsRepository(
            testDataStore(tmp.root),
            object : SecretStore {
                private var v = ""
                override fun get() = v
                override fun set(v: String) { this.v = v }
            },
        )
        val vm = SettingsViewModel(settings, EntryRepository(db), FakeAiService(), clock = { 42L })

        assertTrue(vm.export { throw java.io.IOException("disk full") }.isFailure)
        assertEquals(0L, settings.settings.first().lastBackupAt)

        var written = ""
        assertTrue(vm.export { written = it }.isSuccess)
        assertTrue(written.isNotEmpty())
        assertEquals(42L, settings.settings.first().lastBackupAt)
        db.close()
    }
}
