package com.shibi.commentarii.notify

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.data.ScheduleEntryEntity
import com.shibi.commentarii.debug.DebugLog
import com.shibi.commentarii.domain.AlarmKind
import com.shibi.commentarii.testutil.MutableClock
import com.shibi.commentarii.testutil.TestEnv
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Checklist #109-#112, #114, #121, #130, #274: pending reminders through the repository and a real AlarmScheduler. A fired
 * alarm goes through Robolectric's AlarmManager to the real AlarmReceiver, so "one notification fires" is counted from
 * NotificationManager. The clock is movable; the snackbar on top of #114 is in PastReminderSnackbarTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderBehaviourTest {
    @get:Rule val tmp = TemporaryFolder()

    private val day = LocalDate.of(2031, 3, 12)
    private val clock = MutableClock(day.atTime(9, 0))
    private lateinit var context: Context
    private lateinit var env: TestEnv
    private lateinit var alarmManager: AlarmManager
    private var pageId = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        alarmManager = context.getSystemService(AlarmManager::class.java)
        env = TestEnv(tmp, clock)
        pageId = runBlocking { env.pages.getOrCreateDaily(day).id }
    }

    @After
    fun tearDown() = env.close()

    private fun alarms() = shadowOf(alarmManager).scheduledAlarms

    private fun rows() = runBlocking { env.pages.getSchedule(pageId) }

    /** Saves [texts] on the seeded hour rows, keyed by hour; other rows keep their text. Same call autosave makes. */
    private fun saveTexts(vararg texts: Pair<Int, String>): List<ScheduleEntryEntity> = runBlocking {
        val byMinute = texts.associate { (hour, text) -> hour * 60 to text }
        env.pages.saveSchedule(pageId, rows().map { row -> byMinute[row.minuteOfDay]?.let { row.copy(text = it) } ?: row })
    }

    private fun ms(date: LocalDate, hour: Int, minute: Int = 0) =
        date.atTime(hour, minute).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Suppress("DEPRECATION")
    private fun textOf(a: ShadowAlarmManager.ScheduledAlarm) =
        shadowOf(a.operation!!).savedIntent.getStringExtra(Notifications.EXTRA_TEXT)

    private fun fireAll() {
        alarms().toList().forEach { shadowOf(alarmManager).fireAlarm(it) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun notificationTexts() = shadowOf(context.getSystemService(NotificationManager::class.java)).allNotifications
        .map { it.extras.getCharSequence("android.text").toString() }

    @Test
    fun item109_editingPendingTextFiresOnceWithTheNewText() {
        saveTexts(15 to "Tea")
        assertEquals("Tea", textOf(alarms().single()))

        saveTexts(15 to "Green tea")

        val alarm = alarms().single()
        assertEquals("Green tea", textOf(alarm))
        assertEquals(ms(day, 15), alarm.triggerAtMs)
        fireAll()
        val posted = notificationTexts().single()
        assertTrue(posted, posted.endsWith(": Green tea"))
    }

    @Test
    fun item110_clearingPendingTextCancelsTheAlarm() {
        saveTexts(15 to "Tea", 16 to "Stay")
        assertEquals(2, alarms().size)

        saveTexts(15 to "")
        assertEquals(listOf("Stay"), alarms().map { textOf(it) })

        saveTexts(16 to "   ")
        assertEquals("whitespace counts as blank", 0, alarms().size)
        runBlocking { env.scheduler.rescheduleAll(env.pages.getUpcomingSchedule(day), "test") }
        assertEquals("nothing comes back on reschedule", 0, alarms().size)
        fireAll()
        assertEquals(emptyList<String>(), notificationTexts())
    }

    @Test
    fun item111_removingACustomRowCancelsOnlyItsAlarm() = runBlocking {
        saveTexts(16 to "Stay")
        val custom = ScheduleEntryEntity(pageId = pageId, date = day, minuteOfDay = 15 * 60 + 30, text = "Call", isCustom = true)
        val saved = env.pages.saveSchedule(pageId, rows() + custom)
        val customId = saved.first { it.isCustom }.id
        assertEquals(listOf(ms(day, 15, 30), ms(day, 16)), alarms().map { it.triggerAtMs }.sorted())

        env.pages.saveSchedule(pageId, saved.filterNot { it.isCustom })

        assertEquals(listOf("Stay"), alarms().map { textOf(it) })
        assertTrue(rows().none { it.id == customId })
        assertTrue(env.log.readAll().contains("CANCEL id=$customId existed=true"))
        fireAll()
        assertEquals(1, notificationTexts().size)
    }

    @Test
    fun item112_sameTimeAndTextCollapsesDifferentTextDoesNot() = runBlocking {
        val tomorrow = day.plusDays(1)
        pageId = env.pages.getOrCreateDaily(tomorrow).id
        val nine = rows().map { if (it.minuteOfDay == 9 * 60) it.copy(text = "Gym") else it }
        val custom = ScheduleEntryEntity(pageId = pageId, date = tomorrow, minuteOfDay = 9 * 60, text = "Gym", isCustom = true)
        val saved = env.pages.saveSchedule(pageId, nine + custom)
        assertEquals(1, alarms().size)
        assertEquals(ms(tomorrow, 9), alarms().single().triggerAtMs)
        fireAll()
        assertEquals(1, notificationTexts().size)

        val different = env.pages.saveSchedule(pageId, saved.map { if (it.isCustom) it.copy(text = "Swim") else it })
        assertEquals(setOf("Gym", "Swim"), alarms().map { textOf(it) }.toSet())
        assertEquals(2, alarms().size)
        fireAll()
        // Each entry has its own notification id, so the Gym alarm's second firing replaces its first.
        assertEquals(listOf(": Gym", ": Swim"), notificationTexts().map { it.substring(it.lastIndexOf(": ")) }.sorted())

        // With the seeded row blanked the remaining entry still rings, whichever of the pair had the alarm.
        val same = env.pages.saveSchedule(pageId, different.map { if (it.isCustom) it.copy(text = "Gym") else it })
        assertEquals(1, alarms().size)
        env.pages.saveSchedule(pageId, same.map { if (!it.isCustom && it.minuteOfDay == 9 * 60) it.copy(text = "") else it })
        assertEquals(listOf("Gym"), alarms().map { textOf(it) })
    }

    @Test
    fun item114_pastOutcomeOncePerEntryAndText() {
        val outcomes = mutableListOf<AlarmKind>()
        val collector = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        collector.launch { env.scheduler.outcomes.collect { outcomes += it.kind } }
        try {
            saveTexts(8 to "late")
            assertEquals(listOf(AlarmKind.PAST), outcomes)
            assertEquals(0, alarms().size)

            saveTexts(8 to "late")
            assertEquals("retyping the same text", 1, outcomes.size)

            saveTexts(15 to "future row")
            assertEquals("another save on the page", 1, outcomes.size)

            saveTexts(8 to "later")
            assertEquals("new text reports again", 2, outcomes.size)

            saveTexts(8 to "late")
            assertEquals("that combination was already reported", 2, outcomes.size)
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun item121_deniedExactAlarmsFallBackToInexactAndStillNotify() {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(env.scheduler.canScheduleExact())

        saveTexts(15 to "Tea")

        val alarm = alarms().single()
        assertEquals(ms(day, 15), alarm.triggerAtMs)
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(ShadowAlarmManager.WINDOW_HEURISTIC, alarm.windowLengthMs)
        val line = env.log.lines().last { it.contains(" SCHEDULE ") }
        assertTrue(line, line.endsWith(" inexact textLen=3"))
        fireAll()
        assertTrue(notificationTexts().single().endsWith(": Tea"))
    }

    /** Positive control for #121: the same save with exact alarms allowed takes the exact path. */
    @Test
    fun item121_grantedExactAlarmsUseTheExactCall() {
        saveTexts(15 to "Tea")
        val alarm = alarms().single()
        assertTrue(alarm.isAllowWhileIdle)
        assertEquals(ShadowAlarmManager.WINDOW_EXACT, alarm.windowLengthMs)
        val line = env.log.lines().last { it.contains(" SCHEDULE ") }
        assertTrue(line, line.endsWith(" exact textLen=3"))
    }

    /** Device clock moved back past an entry that had passed: the reschedule TIME_SET triggers makes it pending again. */
    @Test
    fun item130_clockMovedBackRegistersAgainAtTheEntryTime() = runBlocking {
        clock.set(day.atTime(15, 30))
        saveTexts(15 to "Call")
        assertEquals("already past", 0, alarms().size)

        clock.set(day.atTime(14, 58))
        env.scheduler.rescheduleAll(env.pages.getUpcomingSchedule(day.minusDays(1)), "android.intent.action.TIME_SET")

        assertEquals(ms(day, 15), alarms().single().triggerAtMs)
        assertTrue(env.log.readAll().contains("RESCHEDULE_ALL reason=android.intent.action.TIME_SET"))
    }

    /** The other direction: moved past a pending entry, it no longer has an alarm. */
    @Test
    fun item130_clockMovedForwardDropsTheAlarm() = runBlocking {
        saveTexts(15 to "Call")
        assertEquals(1, alarms().size)
        clock.set(day.atTime(15, 30))
        env.scheduler.rescheduleAll(env.pages.getUpcomingSchedule(day.minusDays(1)), "android.intent.action.TIME_SET")
        assertEquals(0, alarms().size)
    }

    @Test
    fun item274_repeatedTestRemindersNeverPushTheLogOverTheCap() {
        val log = DebugLog(tmp.newFolder(), clock)
        val scheduler = AlarmScheduler(context, clock, log)
        repeat(300) {
            scheduler.scheduleTestReminder()
            assertTrue(log.lines().size <= DebugLog.MAX_LINES)
        }
        assertEquals(DebugLog.MAX_LINES, log.lines().size)
        assertEquals("oldest dropped, newest kept", true, log.lines().last().contains("SCHEDULE id=-1"))
        val file = tmp.root.walkTopDown().first { it.name == DebugLog.FILE_NAME && it.readLines().size == DebugLog.MAX_LINES }
        assertEquals(log.lines(), file.readLines())
    }
}
