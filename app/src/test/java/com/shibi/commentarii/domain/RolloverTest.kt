package com.shibi.commentarii.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.data.CommentariiDatabase
import com.shibi.commentarii.data.PageRepository
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RolloverTest {
    private val today = LocalDate.of(2026, 10, 18)
    private val yesterday = today.minusDays(1)

    private fun item(id: Long, section: String, text: String = "t$id", done: Boolean = false) =
        TaskItemEntity(id = id, pageId = 1, section = section, position = 0, text = text, done = done)

    private val items = listOf(item(1, TaskSection.PRIORITY), item(2, TaskSection.TODO))

    @Test
    fun offerOnNewDayWithUnfinishedItems() {
        val offer = computeRollover(today, yesterday, yesterday, items)
        assertNotNull(offer)
        assertEquals(yesterday, offer!!.fromDate)
        assertEquals(items, offer.items)
        assertNotNull(computeRollover(today, null, yesterday, items))
    }

    @Test
    fun nullOnSameDay() {
        assertNull(computeRollover(today, today, yesterday, items))
    }

    @Test
    fun nullWithoutItemsOrPreviousDaily() {
        assertNull(computeRollover(today, yesterday, yesterday, emptyList()))
        assertNull(computeRollover(today, yesterday, yesterday, listOf(item(3, TaskSection.TODO, done = true))))
        assertNull(computeRollover(today, yesterday, null, items))
        assertNull(computeRollover(today, yesterday, today, items))
    }

    // ---- applyRollover (Room) ----

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

    private fun newItem(pageId: Long, text: String) =
        TaskItemEntity(pageId = pageId, section = "", position = 0, text = text)

    @Test
    fun applyRolloverUsesFreePrioritySlotsElseTodo() = runBlocking {
        val prev = repo.getOrCreateDaily(yesterday)
        repo.saveTasks(prev.id, TaskSection.PRIORITY, listOf(newItem(prev.id, "p1"), newItem(prev.id, "p2")))
        repo.saveTasks(prev.id, TaskSection.TODO, listOf(newItem(prev.id, "t1")))
        val unfinished = repo.getTasks(prev.id)
        val offer = computeRollover(today, yesterday, yesterday, unfinished)!!
        assertEquals(3, offer.items.size)

        val todayPage = repo.getOrCreateDaily(today)
        repo.saveTasks(todayPage.id, TaskSection.PRIORITY, listOf(newItem(todayPage.id, "a"), newItem(todayPage.id, "b")))

        applyRollover(repo, offer, offer.items.map { it.id }.toSet(), today)

        val prio = repo.getTasks(todayPage.id, TaskSection.PRIORITY).map { it.text }
        val todo = repo.getTasks(todayPage.id, TaskSection.TODO).map { it.text }
        assertEquals(listOf("a", "b", "p1"), prio)
        assertEquals(listOf("p2", "t1"), todo)

        val originals = repo.getTasks(prev.id)
        assertEquals(3, originals.size)
        originals.forEach {
            assertEquals(today, it.carriedTo)
            assertFalse(it.done)
        }
        assertEquals(setOf("p1", "p2", "t1"), originals.map { it.text }.toSet())
    }

    @Test
    fun applyRolloverOnlyMovesSelected() = runBlocking {
        val prev = repo.getOrCreateDaily(yesterday)
        repo.saveTasks(prev.id, TaskSection.TODO, listOf(newItem(prev.id, "x"), newItem(prev.id, "y")))
        val offer = computeRollover(today, yesterday, yesterday, repo.getTasks(prev.id))!!
        val x = offer.items.first { it.text == "x" }
        applyRollover(repo, offer, setOf(x.id), today)

        val todayPage = repo.getOrCreateDaily(today)
        assertEquals(listOf("x"), repo.getTasks(todayPage.id, TaskSection.TODO).map { it.text })
        val originals = repo.getTasks(prev.id).associateBy { it.text }
        assertEquals(today, originals.getValue("x").carriedTo)
        assertNull(originals.getValue("y").carriedTo)
    }
}
