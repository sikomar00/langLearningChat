package com.chatlearn.data

import com.chatlearn.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class AutoBackupTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository
    private lateinit var backup: AutoBackup
    private lateinit var dir: File
    private var now = 1_000_000_000L

    @Before fun setUp() {
        db = inMemoryDb()
        repo = EntryRepository(db)
        dir = File(tmp.root, AutoBackup.DIR_NAME)
        backup = AutoBackup(dir, repo, { now })
    }

    @After fun tearDown() { db.close() }

    private suspend fun save(text: String) {
        repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, text, "한국어", "", null, emptyList()), 1_000)
    }

    private suspend fun wipe() {
        repo.importAll(BackupData(entries = emptyList(), texts = emptyList(), cards = emptyList(), logs = emptyList(), tags = emptyList(), entryTags = emptyList()))
    }

    @Test fun writesASnapshotWhenNoneExistsAndTheLibraryHasEntries() = runBlocking {
        save("See you soon")
        assertTrue(backup.snapshotIfStale())
        assertTrue(backup.file.exists())
        assertEquals(1, backup.restorableEntryCount())
    }

    @Test fun writesNothingWhenTheLibraryIsEmpty() = runBlocking {
        assertFalse(backup.snapshotIfStale())
        assertFalse(backup.file.exists())
    }

    @Test fun anEmptyLibraryNeverOverwritesAnExistingSnapshot() = runBlocking {
        save("See you soon")
        assertTrue(backup.snapshotIfStale())
        wipe()
        now += 3 * 24 * 60 * 60 * 1000L // 오래됐더라도
        assertFalse(backup.snapshotIfStale())
        assertEquals(1, backup.restorableEntryCount())
    }

    @Test fun aFreshSnapshotIsNotRewrittenUntilItIsStale() = runBlocking {
        save("See you soon")
        assertTrue(backup.snapshotIfStale())
        save("How are you")
        now += 60 * 60 * 1000L // 1시간 뒤: 아직 새것
        assertFalse(backup.snapshotIfStale())
        assertEquals(1, backup.restorableEntryCount())
        now += AutoBackup.SNAPSHOT_MAX_AGE_MS // 오래됨
        assertTrue(backup.snapshotIfStale())
        assertEquals(2, backup.restorableEntryCount())
    }

    @Test fun leavesOnlyTheSnapshotFileInTheBackupFolder() = runBlocking {
        save("See you soon")
        backup.snapshotIfStale()
        now += AutoBackup.SNAPSHOT_MAX_AGE_MS
        backup.snapshotIfStale()
        assertEquals(listOf(AutoBackup.FILE_NAME), dir.list()!!.toList())
    }

    @Test fun offersRestoreOnlyWhenTheLibraryIsEmpty() = runBlocking {
        save("See you soon")
        backup.snapshotIfStale()
        assertEquals(0, backup.pendingRestoreCount()) // 문장이 있으면 묻지 않는다
        wipe()
        assertEquals(1, backup.pendingRestoreCount())
    }

    @Test fun restoreFillsAnEmptyLibrary() = runBlocking {
        save("See you soon")
        backup.snapshotIfStale()
        wipe()
        assertEquals(1, backup.restore().getOrThrow())
        assertEquals(1, repo.observeLibrary().first().size)
        assertEquals(0, backup.pendingRestoreCount())
    }

    @Test fun restoreRefusesToOverwriteAnExistingLibrary() = runBlocking {
        save("See you soon")
        backup.snapshotIfStale()
        save("How are you")
        assertTrue(backup.restore().isFailure)
        assertEquals(2, repo.entryCount())
    }

    @Test fun aCorruptSnapshotIsNotOffered() = runBlocking {
        dir.mkdirs()
        backup.file.writeText("{ this is not a backup")
        assertEquals(0, backup.pendingRestoreCount())
        assertTrue(backup.restore().isFailure)
    }

    @Test fun discardRemovesTheSnapshot() = runBlocking {
        save("See you soon")
        backup.snapshotIfStale()
        wipe()
        backup.discard()
        assertFalse(backup.file.exists())
        assertEquals(0, backup.pendingRestoreCount())
    }
}
