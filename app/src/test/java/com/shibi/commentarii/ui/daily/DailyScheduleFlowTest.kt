package com.shibi.commentarii.ui.daily

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.font.FontWeight
import com.shibi.commentarii.R
import com.shibi.commentarii.data.ScheduleEntryEntity
import com.shibi.commentarii.testutil.DailyPageTestBase
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist #64-#70: Schedule rows, custom times and the reminders they attach. The container's clock is the real one, so on
 * today's page the only rows that are past or future at any hour are custom entries at minute 0 and minute 1439.
 * These two can only be wrong during the last minute of a day.
 */
class DailyScheduleFlowTest : DailyPageTestBase() {
    private val shown = LocalDate.of(2031, 3, 12)
    private val plan get() = str(R.string.daily_schedule_hint)
    private val customTime get() = str(R.string.daily_custom_time)
    private val remove get() = str(R.string.daily_remove)
    private val past get() = str(R.string.outcome_past)
    private val ok get() = str(R.string.action_ok)
    private val cancel get() = str(R.string.action_cancel)

    private fun rows(date: LocalDate) = runBlocking { container.pages.getSchedule(pageId(date)) }

    private fun seedCustom(date: LocalDate, minute: Int, text: String = "") {
        val id = pageId(date)
        runBlocking {
            container.pages.saveSchedule(
                id,
                container.pages.getSchedule(id) +
                    ScheduleEntryEntity(pageId = id, date = date, minuteOfDay = minute, text = text, isCustom = true),
            )
        }
    }

    private fun scrollToSchedule() = scrollTo(hasText(customTime))

    private fun label(time: String) = rule.onNode(hasTime(time))

    private fun topOfTime(time: String) = bounds(label(time)).top

    /** The field on the same row as a time label: rows hold no semantic link between the two, only a position. */
    private fun fieldOf(time: String): SemanticsNodeInteraction {
        val row = centerY(rule.onAllNodes(hasTime(time))[0].fetchSemanticsNode())
        val nodes = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        return rule.onAllNodes(hasSetTextAction())[nodes.indices.minBy { abs(centerY(nodes[it]) - row) }]
    }

    private fun removeButtonsInSchedule(): Int {
        val top = centerY(rule.onNode(hasText(str(R.string.section_schedule))).fetchSemanticsNode())
        val bottom = centerY(rule.onNode(hasText(customTime)).fetchSemanticsNode())
        return rule.onAllNodes(hasContentDescription(remove)).fetchSemanticsNodes().count { centerY(it) in top..bottom }
    }

    private fun tapRemoveOf(time: String) {
        scrollTo(hasTime(time))
        val row = centerY(rule.onAllNodes(hasTime(time))[0].fetchSemanticsNode())
        val nodes = rule.onAllNodes(hasContentDescription(remove)).fetchSemanticsNodes()
        rule.onAllNodes(hasContentDescription(remove))[nodes.indices.minBy { abs(centerY(nodes[it]) - row) }].performClick()
        rule.waitForIdle()
    }

    private fun weightOf(interaction: SemanticsNodeInteraction): FontWeight? =
        layoutOf(interaction.fetchSemanticsNode()).layoutInput.style.fontWeight

    private fun openTimePicker() {
        tapText(customTime)
        rule.onNode(hasContentDescription("Select hour", substring = true)).assertExists()
    }

    /** Taps the clock dial, the only way to pick a time when no keyboard is involved. */
    private fun pickOnDial(hour: Int, minute: Int? = null) {
        rule.onNode(hasContentDescription("$hour o'clock")).performClick()
        rule.waitForIdle()
        settleAnimations()
        if (minute != null) {
            rule.onNode(hasContentDescription("$minute minutes")).performClick()
            rule.waitForIdle()
            settleAnimations()
        }
    }

    private fun selector(part: String, value: String) =
        hasContentDescription(part, substring = true) and hasContentDescription(value, substring = true)

    private fun snackbars() = count(hasText(past))

    /** Waits for autosave of a schedule edit to end, including a second save from clear-then-type. */
    private fun awaitSaved(date: LocalDate, minute: Int, text: String) {
        var last = -1L
        var stableSince = 0L
        await {
            val now = runBlocking { container.pages.getPage(pageId(date))!!.updatedAt }.toEpochMilli()
            if (now != last) {
                last = now
                stableSince = System.currentTimeMillis()
            }
            rows(date).any { it.minuteOfDay == minute && it.text == text } && System.currentTimeMillis() - stableSince > 700
        }
    }

    @Test
    fun item064_seventeenHourlyRowsSixToTenWithThePlanPlaceholder() {
        open(shown)
        scrollToSchedule()

        val times = (6..22).map { h ->
            val hour12 = if (h % 12 == 0) 12 else h % 12
            "$hour12:00 ${if (h < 12) "AM" else "PM"}"
        }
        assertEquals("6:00 AM", times.first())
        assertEquals("10:00 PM", times.last())
        val tops = times.map { time -> topOfTime(time) }
        assertEquals("in time order", tops.sorted(), tops)
        assertEquals("all different rows", 17, tops.toSet().size)
        assertEquals(17, count(hasText(plan) and hasSetTextAction()))
        for (absent in listOf("5:00 AM", "11:00 PM", "6:30 AM", "9:30 AM")) assertFalse("no $absent row", exists(hasTime(absent)))
        assertEquals("seeded rows have no X", 0, removeButtonsInSchedule())
    }

    @Test
    fun item065_textStaysOnASeededRowAndSchedulesItsReminder() {
        val tomorrow = today.plusDays(1)
        open(tomorrow)
        scrollToSchedule()

        fieldOf("9:00 AM").performTextInput("Dentist")
        rule.waitForIdle()
        await { rows(tomorrow).any { it.minuteOfDay == 9 * 60 && it.text == "Dentist" } && alarms().size == 1 }

        rule.onNode(hasText("Dentist") and hasSetTextAction()).assertExists()
        assertEquals(triggerMs(tomorrow, 9 * 60), alarms().single().triggerAtMs)
        assertEquals(0, snackbars())
    }

    @Test
    fun item065_onTodayAFutureEntrySchedulesAndAPastOneDoesNot() {
        seedCustom(today, 1439)
        seedCustom(today, 0)
        open(today)
        scrollToSchedule()

        fieldOf("11:59 PM").performTextInput("Tonight")
        rule.waitForIdle()
        await { alarms().size == 1 && rows(today).any { it.minuteOfDay == 1439 && it.text == "Tonight" } }
        assertEquals(triggerMs(today, 1439), alarms().single().triggerAtMs)
        assertEquals("no complaint about a future entry", 0, snackbars())

        fieldOf("12:00 AM").performTextInput("Long gone")
        rule.waitForIdle()
        await { snackbars() == 1 }
        assertEquals("the past row adds no alarm", listOf(triggerMs(today, 1439)), alarms().map { it.triggerAtMs })
    }

    @Test
    fun item066_customTimePickerStartsAtEightAndAddsABoldRemovableSortedRow() {
        open(shown)
        scrollToSchedule()
        openTimePicker()

        rule.onNode(selector("Select hour", "8 o'clock")).assertIsSelected()
        rule.onNode(selector("Select minutes", "0 minutes")).assertExists()
        rule.onNode(hasText("AM")).assertIsSelected()
        rule.onNode(hasContentDescription("12 o'clock")).assertExists()
        rule.onNode(hasContentDescription("1 o'clock")).assertExists()
        rule.onNode(hasText(cancel)).assertExists()
        rule.onNode(hasText(ok)).assertExists()

        pickOnDial(9, 15)
        rule.onNode(selector("Select hour", "9 o'clock")).assertExists()
        rule.onNode(selector("Select minutes", "15 minutes")).assertExists()
        tapInDialog(ok)
        await { exists(hasTime("9:15 AM")) }

        assertEquals(FontWeight.Bold, weightOf(label("9:15 AM")))
        assertEquals("seeded rows are not bold", FontWeight.Normal, weightOf(label("9:00 AM")))
        assertTrue(topOfTime("9:00 AM") < topOfTime("9:15 AM"))
        assertTrue(topOfTime("9:15 AM") < topOfTime("10:00 AM"))
        assertEquals("the custom row has an X", 1, removeButtonsInSchedule())
        await { rows(shown).any { it.minuteOfDay == 9 * 60 + 15 && it.isCustom } }
    }

    @Test
    fun item067_customRowAtASeededTimeSortsRightAfterIt() {
        open(shown)
        scrollToSchedule()
        openTimePicker()
        pickOnDial(9)
        tapInDialog(ok)
        await { count(hasTime("9:00 AM")) == 2 }

        val nodes = rule.onAllNodes(hasTime("9:00 AM")).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
        assertEquals("seeded first", FontWeight.Normal, layoutOf(nodes[0]).layoutInput.style.fontWeight)
        assertEquals("custom second", FontWeight.Bold, layoutOf(nodes[1]).layoutInput.style.fontWeight)
        assertTrue("nothing in between", nodes[1].boundsInRoot.top < topOfTime("10:00 AM"))

        await { rows(shown).count { it.minuteOfDay == 9 * 60 } == 2 }
        reopen()
        scrollToSchedule()
        val reloaded = rule.onAllNodes(hasTime("9:00 AM")).fetchSemanticsNodes().sortedBy { it.boundsInRoot.top }
        assertEquals("still in that order after a reload", FontWeight.Bold, layoutOf(reloaded[1]).layoutInput.style.fontWeight)
    }

    private fun assertPickerClosedWithNoNewRow() {
        assertFalse("dialog closed", exists(hasText(ok)))
        assertFalse(exists(hasTime("9:15 AM")))
        assertEquals(0, removeButtonsInSchedule())
        assertEquals(17, count(hasText(plan) and hasSetTextAction()))
    }

    @Test
    fun item068_cancelBackAndTapOutsideAddNoRow() {
        open(shown)
        scrollToSchedule()

        openTimePicker()
        pickOnDial(9, 15)
        tapInDialog(cancel)
        assertPickerClosedWithNoNewRow()

        openTimePicker()
        pressBackInDialog()
        assertPickerClosedWithNoNewRow()

        openTimePicker()
        tapOutsideDialog()
        assertPickerClosedWithNoNewRow()

        settleFor(700)
        assertEquals("nothing saved either", 17, rows(shown).size)

        // Control: the same steps with OK do add the row.
        openTimePicker()
        pickOnDial(9, 15)
        tapInDialog(ok)
        await { exists(hasTime("9:15 AM")) }
    }

    @Test
    fun item069_xRemovesTheCustomRowAndCancelsItsReminderOnly() {
        seedCustom(today, 1439, "Bedtime")
        seedCustom(today, 1438, "Teeth")
        assertEquals(setOf(triggerMs(today, 1439), triggerMs(today, 1438)), alarms().map { it.triggerAtMs }.toSet())
        open(today)
        scrollToSchedule()
        assertEquals("only the custom rows have an X", 2, removeButtonsInSchedule())

        tapRemoveOf("11:59 PM")
        await { !exists(hasTime("11:59 PM")) }
        await { rows(today).none { it.minuteOfDay == 1439 } && alarms().size == 1 }

        assertEquals(listOf(triggerMs(today, 1438)), alarms().map { it.triggerAtMs })
        assertEquals(1, removeButtonsInSchedule())
        rule.onNode(hasText("Teeth") and hasSetTextAction()).assertExists()
        assertNull(rows(today).firstOrNull { it.text == "Bedtime" })
    }

    private fun retype(time: String, text: String) {
        fieldOf(time).performTextClearance()
        rule.waitForIdle()
        fieldOf(time).performTextInput(text)
        rule.waitForIdle()
    }

    private fun letSnackbarExpire() {
        rule.mainClock.advanceTimeBy(6_000)
        await { snackbars() == 0 }
    }

    @Test
    fun item070_pastRowOnTodayShowsTheSnackbarOncePerEntryAndText() {
        seedCustom(today, 0)
        open(today)
        scrollToSchedule()
        assertEquals(0, snackbars())

        fieldOf("12:00 AM").performTextInput("Breakfast")
        rule.waitForIdle()
        awaitSaved(today, 0, "Breakfast")
        await { snackbars() == 1 }
        assertEquals("no alarm for a past time", 0, alarms().size)
        letSnackbarExpire()

        retype("12:00 AM", "Breakfast")
        awaitSaved(today, 0, "Breakfast")
        rule.waitForIdle()
        assertEquals("same entry, same text: nothing new", 0, snackbars())

        retype("12:00 AM", "Second breakfast")
        awaitSaved(today, 0, "Second breakfast")
        await { snackbars() == 1 }
    }
}
