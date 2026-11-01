package com.chatlearn.data

import com.chatlearn.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
class FolderTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: EntryRepository

    @Before fun setUp() { db = inMemoryDb(); repo = EntryRepository(db) }
    @After fun tearDown() { db.close() }

    private suspend fun save(text: String, learnedAt: Long? = null, now: Long = 1_000) =
        (repo.saveEntry(EntryDraft(Direction.RECEIVED, Lang.EN, text, "뜻", "", null, emptyList(), learnedAt = learnedAt), now) as SaveResult.Saved).id

    @Test fun sameFolderNameReturnsTheExistingFolderAndBlankIsRejected() = runBlocking {
        val a = repo.createFolder(" 여행 ", 1)!!
        assertEquals(a, repo.createFolder("여행", 2))
        assertNull(repo.createFolder("   ", 3))
        assertEquals(1, repo.observeFolders().first().size)
    }

    @Test fun movingEntriesAndTakingThemOut() = runBlocking {
        val f = repo.createFolder("여행", 1)!!
        val a = save("one"); val b = save("two"); val c = save("three")
        repo.moveToFolder(listOf(a, b), f)
        fun folderOf(id: Long) = runBlocking { db.dao().getEntry(id)!!.folderId }
        assertEquals(f, folderOf(a)); assertEquals(f, folderOf(b)); assertNull(folderOf(c))
        repo.moveToFolder(listOf(a), null)
        assertNull(folderOf(a))
    }

    @Test fun deletingAFolderKeepsItsEntries() = runBlocking {
        val f = repo.createFolder("여행", 1)!!
        val a = save("one")
        repo.moveToFolder(listOf(a), f)
        repo.deleteFolder(f)
        assertNotNull(db.dao().getEntry(a))
        assertNull(db.dao().getEntry(a)!!.folderId)
    }

    @Test fun renameRefusesADuplicateName() = runBlocking {
        val a = repo.createFolder("여행", 1)!!
        val b = repo.createFolder("음식", 2)!!
        assertFalse(repo.renameFolder(b, "여행"))
        assertTrue(repo.renameFolder(b, "맛집"))
        assertTrue(repo.renameFolder(a, "여행")) // 자기 이름 그대로는 허용
    }

    @Test fun bulkDeleteRemovesOnlyTheChosenEntries() = runBlocking {
        val a = save("one"); val b = save("two"); val c = save("three")
        repo.deleteEntries(listOf(a, c))
        assertNull(db.dao().getEntry(a)); assertNull(db.dao().getEntry(c)); assertEquals(b, db.dao().getEntry(b)?.id)
    }

    @Test fun learnedAtBecomesTheSavedDateAndCanBeEditedLater() = runBlocking {
        val past = save("old", learnedAt = 5_000, now = 90_000)
        val today = save("new", now = 90_000)
        assertEquals(5_000L, db.dao().getEntry(past)!!.createdAt)
        assertEquals(90_000L, db.dao().getEntry(today)!!.createdAt)
        repo.setLearnedAt(today, 7_000)
        assertEquals(7_000L, db.dao().getEntry(today)!!.createdAt)
    }

    @Test fun backupRoundTripKeepsFoldersAndMembership() = runBlocking {
        val f = repo.createFolder("여행", 1)!!
        val a = save("one"); repo.moveToFolder(listOf(a), f)
        val json = Backup.encode(repo.exportAll())
        repo.deleteFolder(f); repo.deleteEntries(listOf(a))
        repo.importAll(Backup.decode(json))
        assertEquals(listOf("여행"), repo.observeFolders().first().map { it.name })
        assertEquals(f, db.dao().getEntry(a)!!.folderId)
    }
}
