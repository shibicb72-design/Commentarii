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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
