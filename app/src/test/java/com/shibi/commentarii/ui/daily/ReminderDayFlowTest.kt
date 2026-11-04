package com.shibi.commentarii.ui.daily

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.DailyPageTestBase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist #108 and #113: typing a reminder on tomorrow's and yesterday's page, reached through the real date strip in AppNav,
 * and what that registers. The real clock is used because that is what the scheduler reads.
 */
class ReminderDayFlowTest : DailyPageTestBase() {
    private val past get() = str(R.string.outcome_past)

    private val strip = SemanticsMatcher("the date strip") {
        SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.HorizontalScrollAxisRange in it.config
    }

    private fun pill(day: LocalDate) = hasText(day.format(DateTimeFormatter.ofPattern("EEE", Locale.US))) and
        hasText(day.dayOfMonth.toString()) and hasAnyAncestor(strip)

    private fun openWithStrip(day: LocalDate) {
        rule.onNode(strip).performScrollToNode(pill(day))
        rule.waitForIdle()
        rule.onNode(pill(day)).performClick()
        rule.waitForIdle()
        await { routes.lastOrNull() == "daily/$day" && exists(hasText("1.")) }
    }

    private fun fieldAtNine(): SemanticsNodeInteraction {
        scrollTo(hasTime("9:00 AM"))
        val row = centerY(rule.onNode(hasTime("9:00 AM")).fetchSemanticsNode())
        val nodes = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        return rule.onAllNodes(hasSetTextAction())[nodes.indices.minBy { abs(centerY(nodes[it]) - row) }]
    }

    private fun log() = container.debugLog.readAll()

    @Test
    fun item108_tomorrowRegistersAnExactReminderWithNoSnackbar_item113_yesterdayIsTheControl() {
        openApp()
        val now = today
        val tomorrow = now.plusDays(1)
        openWithStrip(tomorrow)

        fieldAtNine().performTextInput("Dentist")
        await { log().contains("date=$tomorrow minute=540") }
        assertTrue(log(), Regex("""SCHEDULE id=\d+ date=$tomorrow minute=540 triggerAt=\S+ \S+ \S+ exact textLen=7""").containsMatchIn(log()))
        assertEquals(listOf(triggerMs(tomorrow, 540)), alarms().map { it.triggerAtMs })
        settleFor(700)
        assertFalse("no snackbar for a future day", exists(hasText(past)))

        // Control: the same typing on a past page does bring the snackbar, so its absence above means something.
        val yesterday = now.minusDays(1)
        openWithStrip(yesterday)
        fieldAtNine().performTextInput("Dentist")
        await { exists(hasText(past)) }
    }

    @Test
    fun item113_yesterdayShowsTheSnackbarAndSetsNoReminder() {
        openApp()
        val yesterday = today.minusDays(1)
        openWithStrip(yesterday)

        fieldAtNine().performTextInput("Too late")
        await { exists(hasText(past)) }
        assertEquals("That time has passed - no reminder set", past)
        assertTrue(log(), Regex("""SCHEDULE id=\d+ date=$yesterday minute=540 past, no alarm""").containsMatchIn(log()))
        assertEquals("no alarm, so no notification can come", 0, alarms().size)
    }
}
