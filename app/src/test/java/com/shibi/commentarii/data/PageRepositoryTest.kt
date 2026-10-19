package com.shibi.commentarii.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlinx.coroutines.flow.first

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageRepositoryTest {
    private lateinit var db: CommentariiDatabase
    private lateinit var repo: PageRepository
    private val clock = Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CommentariiDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = PageRepository(db, clock, ZoneOffset.UTC)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun dailyPageIsUniquePerDateAndSeeded() = runBlocking {
        val date = LocalDate.of(2026, 10, 18)
        val a = repo.getOrCreateDaily(date)
        val b = repo.getOrCreateDaily(date)
        assertEquals(a.id, b.id)
        val schedule = repo.getSchedule(a.id)
        assertEquals(17, schedule.size)
        assertEquals(6 * 60, schedule.first().minuteOfDay)
        assertEquals(22 * 60, schedule.last().minuteOfDay)
        assertTrue(schedule.none { it.isCustom })
        assertEquals(8, db.pageDao().getBlocks(a.id).size)
        assertTrue(repo.getOrCreateDaily(date.plusDays(1)).id != a.id)
    }

    @Test
    fun ftsFindsDailyAndNote() = runBlocking {
        val daily = repo.getOrCreateDaily(LocalDate.of(2026, 10, 18))
        repo.saveTasks(daily.id, TaskSection.TODO, listOf(TaskItemEntity(pageId = daily.id, section = "", position = 0, text = "Buy mangoes")))
        val note = repo.createNote("Ideas")
        repo.saveBlockDocument(
            note.id,
            SectionKind.RICH_TEXT,
            BlockDocument(listOf(DocBlock(BlockType.PARAGRAPH, "Quarterly budget review"))),
        )
        assertEquals(listOf(daily.id), repo.search("mang").map { it.id })
        assertEquals(listOf(note.id), repo.search("Budg rev").map { it.id })
        assertEquals(listOf(note.id), repo.search("ideas").map { it.id })
        assertTrue(repo.search("\"* OR").isEmpty())
        assertTrue(repo.search("zzzz").isEmpty())
    }

    @Test
    fun pinnedResultsComeFirst() = runBlocking {
        val first = repo.createNote("alpha one")
        val second = repo.createNote("alpha two")
        repo.setPinned(first.id, true)
        assertEquals(listOf(first.id, second.id), repo.search("alpha").map { it.id })
        repo.setPinned(first.id, false)
        repo.setPinned(second.id, true)
        assertEquals(second.id, repo.search("alpha").first().id)
    }

    @Test
    fun foldersAndTags() = runBlocking {
        val note = repo.createNote("Trip")
        val other = repo.createNote("Misc")
        val folder = repo.createFolder("Work")
        repo.moveToFolder(note.id, folder.id)
        assertEquals(listOf(note.id), repo.getPagesInFolder(folder.id).map { it.id })

        val tag = repo.getOrCreateTag("urgent")
        assertEquals(tag.id, repo.getOrCreateTag("Urgent").id)
        repo.addTag(note.id, tag.id)
        repo.addTag(note.id, tag.id)
        assertEquals(listOf(note.id), repo.getPagesWithTag(tag.id).map { it.id })
        assertEquals(listOf("urgent"), repo.getTagsForPage(note.id).map { it.name })
        repo.removeTag(note.id, tag.id)
        assertTrue(repo.getPagesWithTag(tag.id).isEmpty())

        repo.deleteFolder(folder.id)
        assertEquals(null, repo.getPage(note.id)?.folderId)
        assertEquals("Misc", repo.getPage(other.id)?.title)
    }

    private val day = LocalDate.of(2026, 10, 18)

    @Test
    fun hasUserDataFalseWhenEmptyOrOnlyBlankDailyPage() = runBlocking {
        assertFalse(repo.hasUserData())
        repo.getOrCreateDaily(day)
        repo.getOrCreateDaily(day.plusDays(1))
        assertFalse(repo.hasUserData())
        repo.createNote("")
        assertFalse(repo.hasUserData())
    }

    @Test
    fun hasUserDataTrueForNonBlankTask() = runBlocking {
        val p = repo.getOrCreateDaily(day)
        repo.saveTasks(p.id, TaskSection.TODO, listOf(TaskItemEntity(pageId = p.id, section = "", position = 0, text = "  ")))
        assertFalse(repo.hasUserData())
        repo.saveTasks(p.id, TaskSection.TODO, listOf(TaskItemEntity(pageId = p.id, section = "", position = 0, text = "x")))
        assertTrue(repo.hasUserData())
    }

    @Test
    fun hasUserDataTrueForScheduleText() = runBlocking {
        val p = repo.getOrCreateDaily(day)
        val rows = repo.getSchedule(p.id)
        repo.saveSchedule(p.id, listOf(rows.first().copy(text = "Dentist")) + rows.drop(1))
        assertTrue(repo.hasUserData())
    }

    @Test
    fun hasUserDataTrueForMood() = runBlocking {
        repo.saveMood(day, 4)
        assertTrue(repo.hasUserData())
    }

    @Test
    fun hasUserDataTrueForSelfCareCheck() = runBlocking {
        val care = SelfCareRepository(db)
        val item = care.addItem("Walk")
        assertFalse(repo.hasUserData())
        care.toggleCheck(item.id, day)
        assertTrue(repo.hasUserData())
    }

    @Test
    fun hasUserDataTrueForDailyNotesText() = runBlocking {
        val p = repo.getOrCreateDaily(day)
        repo.saveBlockDocument(p.id, SectionKind.NOTES, BlockDocument(listOf(DocBlock(text = "thoughts"))))
        assertTrue(repo.hasUserData())
    }

    @Test
    fun hasUserDataTrueForNoteContentAndTitle() = runBlocking {
        val note = repo.createNote("")
        repo.saveBlockDocument(note.id, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(text = "body"))))
        assertTrue(repo.hasUserData())
        repo.saveBlockDocument(note.id, SectionKind.RICH_TEXT, BlockDocument.EMPTY)
        assertFalse(repo.hasUserData())
        repo.setTitle(note.id, "Ideas")
        assertTrue(repo.hasUserData())
    }

    @Test
    fun weeklyAndMonthlyPagesAreIdempotentSeededAndShareDatesWithDaily() = runBlocking {
        val monday = LocalDate.of(2026, 10, 12)
        val week = repo.getOrCreateWeekly(monday)
        assertEquals(week.id, repo.getOrCreateWeekly(monday).id)
        assertEquals("Week of Oct 12 \u2013 Oct 18 (W42)", week.title)
        assertEquals(PageType.WEEKLY, week.type)
        assertEquals(SectionKind.WEEKLY_KINDS, db.pageDao().getBlocks(week.id).map { it.kind })
        assertEquals(BlockDocument.EMPTY.toJson(), db.pageDao().getBlocks(week.id).first { it.kind == SectionKind.NOTES }.payloadJson)
        assertEquals("{}", db.pageDao().getBlocks(week.id).first { it.kind == SectionKind.GOALS }.payloadJson)

        val first = LocalDate.of(2026, 10, 1)
        val month = repo.getOrCreateMonthly(first)
        assertEquals(month.id, repo.getOrCreateMonthly(first).id)
        assertEquals("October 2026", month.title)
        assertEquals(SectionKind.MONTHLY_KINDS, db.pageDao().getBlocks(month.id).map { it.kind })

        // Daily pages on the same dates are separate pages.
        assertTrue(repo.getOrCreateDaily(monday).id != week.id)
        assertTrue(repo.getOrCreateDaily(first).id != month.id)
        assertEquals(week.id, repo.getOrCreateWeekly(monday).id)
    }

    @Test
    fun weeklyNeedsAMondayAndMonthlyTheFirst() = runBlocking {
        try {
            repo.getOrCreateWeekly(LocalDate.of(2026, 10, 13))
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
        try {
            repo.getOrCreateMonthly(LocalDate.of(2026, 10, 2))
            org.junit.Assert.fail("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }

    @Test
    fun observingAPeriodNeverCreatesIt() = runBlocking {
        val monday = LocalDate.of(2026, 10, 12)
        assertEquals(null, repo.observeWeekly(monday).first())
        assertEquals(null, repo.observeMonthly(LocalDate.of(2026, 10, 1)).first())
        assertEquals(null, db.pageDao().getPage(PageType.WEEKLY, monday))
        val created = repo.getOrCreateWeekly(monday)
        assertEquals(created.id, repo.observeWeekly(monday).first()?.id)
    }

    @Test
    fun searchFindsWeeklyGoalMonthlyEventAndReviewText() = runBlocking {
        val week = repo.getOrCreateWeekly(LocalDate.of(2026, 10, 12))
        repo.saveTasks(
            week.id,
            TaskSection.GOALS,
            listOf(TaskItemEntity(pageId = week.id, section = "", position = 0, text = "Marathon training")),
        )
        repo.saveBlockDocument(
            week.id,
            SectionKind.REVIEW_WELL,
            BlockDocument(listOf(DocBlock(text = "Gratitude journalling worked"))),
        )
        val month = repo.getOrCreateMonthly(LocalDate.of(2026, 10, 1))
        repo.saveEvents(month.id, EventList(listOf(EventItem(LocalDate.of(2026, 10, 20).toEpochDay(), "Wedding rehearsal"))))
        repo.saveBlockDocument(month.id, SectionKind.REVIEW, BlockDocument(listOf(DocBlock(text = "Sluggish fortnight"))))

        assertEquals(listOf(week.id), repo.search("marathon").map { it.id })
        assertEquals(listOf(week.id), repo.search("journalling").map { it.id })
        assertEquals(listOf(month.id), repo.search("rehearsal").map { it.id })
        assertEquals(listOf(month.id), repo.search("sluggish").map { it.id })
        assertEquals(listOf(week.id), repo.search("W42").map { it.id })
        assertEquals(listOf(month.id), repo.search("october").map { it.id })
        assertEquals(PageType.MONTHLY, repo.search("rehearsal").single().type)
    }

    @Test
    fun eventsAreSavedSortedByDate() = runBlocking {
        val month = repo.getOrCreateMonthly(LocalDate.of(2026, 10, 1))
        val late = LocalDate.of(2026, 10, 28).toEpochDay()
        val early = LocalDate.of(2026, 10, 3).toEpochDay()
        repo.saveEvents(month.id, EventList(listOf(EventItem(late, "B"), EventItem(early, "A"))))
        assertEquals(listOf("A", "B"), repo.getEvents(month.id).items.map { it.text })
    }

    @Test
    fun contentDatesAndPeriodQueriesCoverTheRange() = runBlocking {
        val d1 = LocalDate.of(2026, 10, 12)
        val d2 = LocalDate.of(2026, 10, 13)
        val d3 = LocalDate.of(2026, 10, 14)
        val d4 = LocalDate.of(2026, 10, 15)
        val p1 = repo.getOrCreateDaily(d1)
        repo.getOrCreateDaily(d2) // blank page: no content
        val p3 = repo.getOrCreateDaily(d3)
        repo.saveTasks(p1.id, TaskSection.PRIORITY, listOf(TaskItemEntity(pageId = p1.id, section = "", position = 0, text = "Plan", done = true)))
        repo.saveBlockDocument(p3.id, SectionKind.NOTES, BlockDocument(listOf(DocBlock(text = "thoughts"))))
        repo.saveMood(d4, 3)
        repo.getOrCreateDaily(d4)

        val dates = repo.observeContentDates(d1, d4).first()
        assertEquals(listOf(d1, d3, d4), dates)
        assertEquals(listOf(DailyTaskRow(d1, TaskSection.PRIORITY, "Plan", true)), repo.observeDailyTasks(d1, d4).first())
        assertEquals(listOf(3), repo.observeMoods(d1, d4).first().map { it.mood })
    }
}
