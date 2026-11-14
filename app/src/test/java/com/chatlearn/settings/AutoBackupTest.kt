package com.chatlearn.settings

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoBackupTest {
    private val day = 86_400_000L
    private val now = 1_790_000_000_000L

    @Test fun dueOnlyWhenAFolderIsSetAndAWeekHasPassed() {
        assertFalse(autoBackupDue(folderSet = false, lastBackupAt = 0, now = now))
        assertTrue(autoBackupDue(folderSet = true, lastBackupAt = 0, now = now))
        assertFalse(autoBackupDue(folderSet = true, lastBackupAt = now - 6 * day, now = now))
        assertTrue(autoBackupDue(folderSet = true, lastBackupAt = now - 7 * day, now = now))
    }

    @Test fun fileNamesCarryTheDateAndOnlyTheNewestFourAreKept() {
        assertEquals("chatlearn-auto-2026-11-14.json", autoBackupName(LocalDate.of(2026, 11, 14)))
        val names = listOf(
            "chatlearn-auto-2026-10-01.json", "chatlearn-auto-2026-10-08.json", "my-notes.json",
            "chatlearn-auto-2026-10-15.json", "chatlearn-auto-2026-10-22.json", "chatlearn-auto-2026-10-29.json",
        )
        assertEquals(listOf("chatlearn-auto-2026-10-01.json"), staleAutoBackups(names, keep = 4))
    }
}
