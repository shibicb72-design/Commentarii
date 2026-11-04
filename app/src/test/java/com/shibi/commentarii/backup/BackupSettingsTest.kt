package com.shibi.commentarii.backup

import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.TestEnv
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Checklist #38, #253, #257: what an import does and does not touch, on stores of their own. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupSettingsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), ZoneOffset.UTC)
    private val envs = mutableListOf<TestEnv>()

    private fun newEnv() = TestEnv(tmp, clock).also { envs += it }

    @After
    fun tearDown() = envs.forEach { it.close() }

    private suspend fun exportWithLock(lock: Boolean): String {
        val source = newEnv()
        source.pages.createNote("Anything")
        source.settings.setLockEnabled(lock)
        return source.io.export()
    }

    @Test
    fun item038_lockIsNeverExportedAndImportLeavesItAlone() = runBlocking {
        for (lock in listOf(true, false)) {
            val json = exportWithLock(lock)
            assertFalse("lock=$lock leaked into the file", json.contains("lockEnabled") || json.contains("lock_enabled"))
            val settingKeys = JSONObject(json).getJSONObject("settings").keys().asSequence().toList()
            assertTrue(settingKeys.toString(), "darkMode" in settingKeys && settingKeys.none { it.contains("lock", ignoreCase = true) })

            val target = newEnv()
            target.settings.setLockEnabled(!lock)
            target.io.import(json)
            assertEquals(listOf("Anything"), target.pages.search("anything").map { it.title })
            assertEquals(!lock, target.settings.lockEnabled.first())
        }
    }

    @Test
    fun item038_item253_tamperedBackupCannotChangeLockOrTheme() = runBlocking {
        for (lock in listOf(true, false)) {
            val root = JSONObject(exportWithLock(false))
            root.getJSONObject("settings").put("lockEnabled", !lock).put("lock_enabled", !lock)
                .put("themeId", "tampered_theme").put("theme_id", "tampered_theme").put("darkMode", "DARK")

            val target = newEnv()
            target.settings.setLockEnabled(lock)
            target.settings.setThemeId("washi_planner")
            target.io.import(root.toString())

            assertEquals(DarkMode.DARK, target.settings.darkMode.first())
            assertEquals(lock, target.settings.lockEnabled.first())
            assertEquals("washi_planner", target.settings.themeId.first())
        }
    }

    @Test
    fun item253_darkModeAndReminderRevertToTheBackupButThemeAndLockDoNot() = runBlocking {
        val env = newEnv()
        env.settings.setDarkMode(DarkMode.DARK)
        env.settings.setExportReminderEnabled(false)
        val json = env.io.export()
        val exported = JSONObject(json).getJSONObject("settings")
        assertEquals("DARK", exported.getString("darkMode"))
        assertFalse(exported.getBoolean("exportReminderEnabled"))

        env.settings.setDarkMode(DarkMode.LIGHT)
        env.settings.setExportReminderEnabled(true)
        env.settings.setThemeId("washi_planner")
        env.settings.setThemeAnimated("washi_planner", false)
        env.settings.setLockEnabled(true)
        env.io.import(json)

        assertEquals(DarkMode.DARK, env.settings.darkMode.first())
        assertFalse(env.settings.exportReminderEnabled.first())
        assertEquals("washi_planner", env.settings.themeId.first())
        assertEquals(setOf("washi_planner"), env.settings.staticThemeIds.first())
        assertTrue(env.settings.lockEnabled.first())
    }

    @Test
    fun item257_importRebuildsTheSearchIndexFromTheFile() = runBlocking {
        val source = newEnv()
        val note = source.pages.createNote("Imported title")
        source.pages.saveBlockDocument(note.id, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(BlockType.PARAGRAPH, "the walrus note"))))
        val day = source.pages.getOrCreateDaily(LocalDate.of(2026, 10, 18))
        source.pages.saveTasks(day.id, TaskSection.TODO, listOf(TaskItemEntity(pageId = day.id, section = "", position = 0, text = "buy mangoes")))
        val rows = source.pages.getSchedule(day.id)
        source.pages.saveSchedule(day.id, rows.map { if (it.minuteOfDay == 15 * 60) it.copy(text = "gym session") else it })
        val json = source.io.export()
        assertFalse("the index is not stored", JSONObject(json).has("page_search"))

        val target = newEnv()
        val leftover = target.pages.createNote("Leftover zebra")
        assertEquals(listOf(leftover.id), target.pages.search("zebra").map { it.id })
        target.io.import(json)

        assertEquals(listOf(note.id), target.pages.search("walrus").map { it.id })
        assertEquals(listOf(note.id), target.pages.search("imported").map { it.id })
        assertEquals(listOf(day.id), target.pages.search("mango").map { it.id })
        assertEquals(listOf(day.id), target.pages.search("gym").map { it.id })
        assertEquals(emptyList<Long>(), target.pages.search("zebra").map { it.id })
    }
}
