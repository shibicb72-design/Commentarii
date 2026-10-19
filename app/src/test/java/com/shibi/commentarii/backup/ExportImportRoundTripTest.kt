package com.shibi.commentarii.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.CommentariiDatabase
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.PageRepository
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.SelfCareCheckEntity
import com.shibi.commentarii.data.SelfCareItemEntity
import com.shibi.commentarii.data.SettingsRepository
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.shibi.commentarii.data.EventItem
import com.shibi.commentarii.data.EventList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExportImportRoundTripTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val clock = Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), ZoneOffset.UTC)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dbs = mutableListOf<CommentariiDatabase>()
    private var storeCount = 0

    private class Env(val db: CommentariiDatabase, val repo: PageRepository, val settings: SettingsRepository, val io: ExportImport)

    private fun newEnv(): Env {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, CommentariiDatabase::class.java).allowMainThreadQueries().build()
        dbs += db
        val file = File(tmp.root, "prefs${storeCount++}.preferences_pb")
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        val repo = PageRepository(db, clock, ZoneOffset.UTC)
        return Env(db, repo, settings, ExportImport(db, settings, repo))
    }

    @Before
    fun setUp() = Unit

    @After
    fun tearDown() {
        dbs.forEach { it.close() }
        scope.cancel()
    }

    private suspend fun populate(e: Env) {
        val day = LocalDate.of(2026, 10, 18)
        val daily = e.repo.getOrCreateDaily(day)
        e.repo.saveTasks(
            daily.id,
            TaskSection.TODO,
            listOf(
                TaskItemEntity(pageId = daily.id, section = "", position = 0, text = "Buy mangoes", done = true),
                TaskItemEntity(pageId = daily.id, section = "", position = 1, text = "Call mum", carriedTo = day.plusDays(1)),
            ),
        )
        val sched = e.repo.getSchedule(daily.id)
        e.repo.saveSchedule(daily.id, sched.map { if (it.minuteOfDay == 9 * 60) it.copy(text = "Dentist", isCustom = true) else it })
        e.repo.saveMood(day, 4)
        val careId = e.db.selfCareDao().insertItem(SelfCareItemEntity(name = "Water", position = 0))
        e.db.selfCareDao().insertItem(SelfCareItemEntity(name = "Old habit", position = 1, active = false))
        e.db.selfCareDao().insertCheck(SelfCareCheckEntity(careId, day))

        val note = e.repo.createNote("Ideas")
        e.repo.saveBlockDocument(
            note.id,
            SectionKind.RICH_TEXT,
            BlockDocument(listOf(DocBlock(BlockType.PARAGRAPH, "Quarterly budget review"))),
        )
        val folder = e.repo.createFolder("Work")
        e.repo.moveToFolder(note.id, folder.id)
        val tag = e.repo.getOrCreateTag("urgent")
        e.repo.addTag(note.id, tag.id)
        e.repo.setPinned(note.id, true)
        e.repo.markOpened(note.id)

        e.settings.setDarkMode(DarkMode.DARK)
        e.settings.setExportReminderEnabled(false)
        e.settings.setLastOpenedDate(day)
        e.settings.setLastExportAt(Instant.parse("2026-10-01T00:00:00Z"))
    }

    private suspend fun snapshot(e: Env): List<Any> {
        val d = e.db.pageDao()
        val s = e.db.selfCareDao()
        return listOf(
            d.getAllPages(), d.getAllBlocks(), d.getAllTasks(), d.getAllSchedule(), d.getAllMoods(),
            d.getAllFolders(), d.getAllTags(), d.getAllPageTags(), s.getAllItems(), s.getAllChecks(),
        )
    }

    @Test
    fun roundTripIntoEmptyDatabase() = runBlocking {
        val a = newEnv()
        populate(a)
        val json = a.io.export()

        val b = newEnv()
        b.settings.setLockEnabled(true)
        b.io.import(json)

        assertEquals(snapshot(a), snapshot(b))
        assertEquals(json, b.io.export())
        assertEquals(listOf("Ideas"), b.repo.search("budg").map { it.title })
        assertEquals(1, b.repo.search("mang").size)
        assertEquals(DarkMode.DARK, b.settings.darkMode.first())
        assertEquals(false, b.settings.exportReminderEnabled.first())
        assertTrue(b.settings.lockEnabled.first())
        assertTrue(!json.contains("lockEnabled"))
    }

    @Test
    fun importReplacesExistingData() = runBlocking {
        val a = newEnv()
        populate(a)
        val json = a.io.export()

        val b = newEnv()
        b.repo.createNote("Leftover zebra")
        b.repo.getOrCreateDaily(LocalDate.of(2020, 1, 1))
        b.repo.createFolder("Stale")
        b.io.import(json)

        assertEquals(snapshot(a), snapshot(b))
        assertTrue(b.repo.search("zebra").isEmpty())
        assertEquals(1, b.repo.search("budg").size)
    }

    @Test
    fun badFormatVersionThrowsAndLeavesDataUntouched() = runBlocking {
        val a = newEnv()
        populate(a)
        val before = snapshot(a)
        val json = a.io.export()

        val bad = org.json.JSONObject(json).put("formatVersion", 2).toString()
        try {
            a.io.import(bad)
            fail("expected BackupFormatException")
        } catch (_: BackupFormatException) {
        }
        try {
            a.io.import("not json")
            fail("expected BackupFormatException")
        } catch (_: BackupFormatException) {
        }
        assertEquals(before, snapshot(a))
        assertEquals(1, a.repo.search("budg").size)
    }

    @Test
    fun roundTripWithWeeklyAndMonthlyPagesIsIdentical() = runBlocking {
        val a = newEnv()
        populate(a)
        val week = a.repo.getOrCreateWeekly(LocalDate.of(2026, 10, 12))
        a.repo.saveTasks(
            week.id,
            TaskSection.GOALS,
            listOf(TaskItemEntity(pageId = week.id, section = "", position = 0, text = "Marathon training", done = true)),
        )
        a.repo.saveBlockDocument(week.id, SectionKind.REVIEW_IMPROVE, BlockDocument(listOf(DocBlock(text = "Sleep earlier"))))
        val month = a.repo.getOrCreateMonthly(LocalDate.of(2026, 10, 1))
        a.repo.saveTasks(
            month.id,
            TaskSection.GOALS,
            listOf(TaskItemEntity(pageId = month.id, section = "", position = 0, text = "Ship the release")),
        )
        a.repo.saveEvents(month.id, EventList(listOf(EventItem(LocalDate.of(2026, 10, 20).toEpochDay(), "Wedding rehearsal"))))
        a.repo.saveBlockDocument(month.id, SectionKind.REVIEW, BlockDocument(listOf(DocBlock(text = "Sluggish fortnight"))))
        a.repo.setPinned(week.id, true)
        val json = a.io.export()

        val b = newEnv()
        b.io.import(json)

        assertEquals(snapshot(a), snapshot(b))
        assertEquals(json, b.io.export())
        assertEquals(listOf(month.id), b.repo.search("rehearsal").map { it.id })
        assertEquals(listOf(week.id), b.repo.search("earlier").map { it.id })
        assertEquals(listOf(week.id), b.repo.search("marathon").map { it.id })
    }

    @Test
    fun formatVersion1FileWithoutPeriodPagesStillImports() = runBlocking {
        val a = newEnv()
        populate(a)
        val json = a.io.export()
        assertEquals(1, org.json.JSONObject(json).getInt("formatVersion"))
        val b = newEnv()
        b.io.import(json)
        assertEquals(snapshot(a), snapshot(b))
    }
}
