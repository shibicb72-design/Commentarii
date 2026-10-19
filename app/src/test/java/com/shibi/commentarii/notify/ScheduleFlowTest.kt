package com.shibi.commentarii.notify

import android.app.AlarmManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.data.CommentariiDatabase
import com.shibi.commentarii.data.PageRepository
import com.shibi.commentarii.data.ScheduleEntryEntity
import com.shibi.commentarii.debug.DebugLog
import com.shibi.commentarii.domain.AlarmKind
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScheduleFlowTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-18T09:00:00Z"), ZoneOffset.UTC)
    private val day = LocalDate.of(2026, 10, 18)
    private lateinit var context: Context
    private lateinit var db: CommentariiDatabase
    private lateinit var repo: PageRepository
    private lateinit var scheduler: AlarmScheduler
    private lateinit var alarmManager: AlarmManager
    private var pageId = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        alarmManager = context.getSystemService(AlarmManager::class.java)
        db = Room.inMemoryDatabaseBuilder(context, CommentariiDatabase::class.java).allowMainThreadQueries().build()
        scheduler = AlarmScheduler(context, clock)
        repo = PageRepository(db, clock, ZoneOffset.UTC, scheduler)
        pageId = runBlocking { repo.getOrCreateDaily(day).id }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun alarms() = shadowOf(alarmManager).scheduledAlarms

    private fun entry(id: Long, minute: Int, text: String) =
        ScheduleEntryEntity(id = id, pageId = pageId, date = day, minuteOfDay = minute, text = text, isCustom = true)

    private fun uriOf(a: ShadowAlarmManager.ScheduledAlarm) = shadowOf(op(a)).savedIntent.data.toString()

    @Suppress("DEPRECATION")
    private fun op(a: ShadowAlarmManager.ScheduledAlarm) = a.operation!!

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    @Test
    fun customEntryIsScheduledUnderItsDbIdThenReplacedThenCancelled() = runBlocking {
        val saved = repo.saveSchedule(pageId, listOf(entry(0, 15 * 60, "call")))
        val id = saved.single().id
        assertTrue(id > 0)
        assertEquals("commentarii://alarm/$id", uriOf(alarms().single()))
        assertEquals(ms("2026-10-18T14:50:00Z"), alarms().single().triggerAtMs)

        repo.saveSchedule(pageId, listOf(entry(id, 15 * 60, "call mum")))
        assertEquals(1, alarms().size)
        val text = shadowOf(op(alarms().single())).savedIntent.getStringExtra(Notifications.EXTRA_TEXT)
        assertEquals("call mum", text)

        repo.saveSchedule(pageId, listOf(entry(id, 16 * 60 + 30, "call mum")))
        assertEquals(1, alarms().size)
        assertEquals(ms("2026-10-18T16:20:00Z"), alarms().single().triggerAtMs)

        repo.saveSchedule(pageId, emptyList())
        assertEquals(0, alarms().size)
    }

    @Test
    fun distinctEntriesGetDistinctAlarmsAndCancelRemovesOnlyTheMatch() = runBlocking {
        val saved = repo.saveSchedule(pageId, listOf(entry(0, 15 * 60, "a"), entry(0, 16 * 60, "b")))
        assertEquals(2, alarms().size)
        assertEquals(2, alarms().map { uriOf(it) }.toSet().size)
        scheduler.cancel(listOf(saved[0].id))
        assertEquals(listOf("commentarii://alarm/${saved[1].id}"), alarms().map { uriOf(it) })
    }

    @Test
    fun soonEntryFiresInFiveSecondsOnceAndEmitsOutcome() = runBlocking {
        val first = async(start = CoroutineStart.UNDISPATCHED) { scheduler.outcomes.first() }
        val saved = repo.saveSchedule(pageId, listOf(entry(0, 9 * 60 + 5, "soon")))
        val outcome = first.await()
        assertEquals(AlarmKind.IMMEDIATE, outcome.kind)
        assertEquals(saved.single().id, outcome.entryId)
        assertEquals(ms("2026-10-18T09:00:05Z"), alarms().single().triggerAtMs)

        // Boot or resume must not re-fire an immediate already scheduled for this entry, time and text.
        scheduler.rescheduleAll(saved, "test")
        assertEquals(0, alarms().size)
    }

    @Test
    fun pastEntryEmitsPastOutcomeAndNoAlarm() = runBlocking {
        val first = async(start = CoroutineStart.UNDISPATCHED) { scheduler.outcomes.first() }
        repo.saveSchedule(pageId, listOf(entry(0, 8 * 60, "late")))
        assertEquals(AlarmKind.PAST, first.await().kind)
        assertEquals(0, alarms().size)
    }

    @Test
    fun rescheduleAllDoesNotEmitOutcomes() = runBlocking {
        val saved = repo.saveSchedule(pageId, listOf(entry(0, 8 * 60, "late")))
        val first = async(start = CoroutineStart.UNDISPATCHED) { scheduler.outcomes.first() }
        scheduler.rescheduleAll(saved, "test")
        assertTrue(!first.isCompleted)
        first.cancel()
    }

    @Test
    fun testReminderIsScheduledTenSecondsAheadUnderIdMinusOne() {
        scheduler.scheduleTestReminder()
        scheduler.scheduleTestReminder()
        val alarm = alarms().single()
        assertEquals(ms("2026-10-18T09:00:10Z"), alarm.triggerAtMs)
        assertEquals("commentarii://alarm/-1", uriOf(alarm))
        val intent = shadowOf(op(alarm)).savedIntent
        assertEquals(-1L, intent.getLongExtra(Notifications.EXTRA_ENTRY_ID, 0))
        assertEquals("Test reminder", intent.getStringExtra(Notifications.EXTRA_TEXT))
    }

    @Test
    fun scheduleCancelAndRescheduleAllAreLoggedWithoutNoteText() {
        val log = DebugLog(Files.createTempDirectory("l").toFile(), clock)
        val s = AlarmScheduler(context, clock, log)
        val e = ScheduleEntryEntity(id = 5, pageId = 1, date = day, minuteOfDay = 15 * 60, text = "secret")
        s.rescheduleAll(listOf(e), "boot")
        val text = log.readAll()
        assertTrue(text.contains("CANCEL id=5"))
        assertTrue(text.contains("SCHEDULE id=5 date=2026-10-18"))
        assertTrue(text.contains("exact immediate=false textLen=6"))
        assertTrue(text.contains("RESCHEDULE_ALL reason=boot entries=1 registered=1"))
        assertTrue(!text.contains("secret"))
    }
}
