package com.shibi.commentarii.ui.settings

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStateTest {
    @Test fun fileName() = assertEquals("commentarii-backup-2026-10-18.json", backupFileName(LocalDate.of(2026, 10, 18)))
    @Test fun moveDown() = assertEquals(listOf(2L, 1L, 3L), movedIds(listOf(1, 2, 3), 1, 1))
    @Test fun moveUp() = assertEquals(listOf(1L, 3L, 2L), movedIds(listOf(1, 2, 3), 3, -1))
    @Test fun moveOutOfRange() = assertEquals(listOf(1L, 2L), movedIds(listOf(1, 2), 1, -1))
    @Test fun unknownId() = assertEquals(listOf(1L, 2L), movedIds(listOf(1, 2), 9, 1))
}
