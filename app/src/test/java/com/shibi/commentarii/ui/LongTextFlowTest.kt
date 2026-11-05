package com.shibi.commentarii.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.data.TaskSection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config
import com.shibi.commentarii.testutil.PeriodPageTestBase

/**
 * Checklist #297: 500 characters pasted into each kind of one-line row in the real AppNav. The row grows and wraps, nothing it
 * holds is cut off, and it stays clear of the row's controls and of the row below. The week's day card keeps a priority to 2 lines.
 */
@Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
class LongTextFlowTest : PeriodPageTestBase() {
    private val long = (1..100).joinToString(" ") { "word" }.take(500)

    private fun overlap(a: Rect, b: Rect) = a.left < b.right - 1 && b.left < a.right - 1 && a.top < b.bottom - 1 && b.top < a.bottom - 1

    private fun longField(): SemanticsNodeInteraction = rule.onNode(hasSetTextAction() and hasText(long))

    /** The pasted row is several lines tall, shows all its text, and its controls in [controls] do not touch it. */
    private fun assertGrewClearOf(emptyHeight: Float, controls: List<String>) {
        val node = longField().fetchSemanticsNode()
        assertEquals("kept whole", long, node.config[SemanticsProperties.EditableText].text)
        val box = node.boundsInRoot
        assertTrue("grew from $emptyHeight to ${box.height}", box.height > emptyHeight * 3)
        assertTrue("inside the window: $box of $rootWidth", box.right <= rootWidth + 1)
        val layout = layoutOf(node)
        assertTrue("wraps: ${layout.lineCount} lines", layout.lineCount >= 4)
        for (control in controls) {
            val bounds = bounds(onRowOf(longField(), hasContentDescription(control)))
            assertFalse("$control at $bounds overlaps the text at $box", overlap(bounds, box))
            assertTrue("$control is on screen: $bounds of $rootWidth", bounds.left >= 0 && bounds.right <= rootWidth + 1)
        }
    }

    private fun paste(hint: String) {
        scrollTo(hasText(hint))
        val empty = rule.onAllNodes(hasText(hint) and hasSetTextAction())[0]
        val height = empty.fetchSemanticsNode().boundsInRoot.height
        empty.performTextInput(long)
        rule.waitForIdle()
        assertGrewClearOf(height, emptyList())
    }

    /** The row's check box stays clear of the text, on the same line of the screen as its first line. */
    private fun assertCheckClear() {
        val box = bounds(longField())
        val check = bounds(checkOf(longField()))
        assertFalse("the check at $check overlaps the text at $box", overlap(check, box))
        assertTrue("on screen: $check", check.left >= 0)
    }

    private val timeText = SemanticsMatcher("a time") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { Regex("^[0-9]{1,2}:[0-9]{2}").containsMatchIn(it.text) }
    }

    /** Checklist #297, priority: wraps inside its row, its controls untouched; saved whole. */
    @Test
    fun item297_aPriorityGrowsAndKeepsClearOfItsCheck() {
        open(day)
        paste(str(R.string.daily_priority_hint, 1))
        assertCheckClear()
        await { texts(day, TaskSection.PRIORITY) == listOf(long) }
    }

    /** Checklist #297, to-do: wraps, with its remove button and arrows clear of the text; the next row sits below it. */
    @Test
    fun item297_aTodoGrowsAndKeepsClearOfItsControls() {
        seedTasks(day, TaskSection.TODO, "First", "Last")
        open(day)
        await { exists(hasText("Last")) }
        scrollTo(hasText("First"))
        val height = bounds(field("First")).height
        val top = bounds(field("Last")).top

        // Into a third, empty row.
        typeInFirst(todoHint, long)
        assertGrewClearOf(height, listOf(remove, moveUp))
        await { texts(day, TaskSection.TODO) == listOf("First", "Last", long) }

        val box = bounds(longField())
        assertTrue("the row sits below the others", box.top >= top)
        val below = rule.onAllNodes(hasText(addItem)).fetchSemanticsNodes().map { it.boundsInRoot.top }.filter { it > box.top }.min()
        assertTrue("the section's Add item is below it: $below, row ends ${box.bottom}", below >= box.bottom - 1)
    }

    /** Checklist #297, schedule: the Plan field wraps, its time label stays beside it, the next row moves down. */
    @Test
    fun item297_aScheduleRowGrowsAndKeepsItsTimeBesideIt() {
        open(day)
        val plan = str(R.string.daily_schedule_hint)
        scrollTo(hasText(plan))
        val rows = rule.onAllNodes(hasText(plan) and hasSetTextAction())
        val height = rows[0].fetchSemanticsNode().boundsInRoot.height
        val nextTop = rows[1].fetchSemanticsNode().boundsInRoot.top
        rows[0].performTextInput(long)
        rule.waitForIdle()

        assertGrewClearOf(height, emptyList())
        val box = bounds(longField())
        val time = bounds(onRowOf(longField(), timeText))
        assertFalse("the time at $time overlaps the text at $box", overlap(time, box))
        assertTrue("the next row moved down", bounds(rule.onAllNodes(hasText(plan) and hasSetTextAction())[0]).top > nextTop)
        assertTrue(bounds(rule.onAllNodes(hasText(plan) and hasSetTextAction())[0]).top >= box.bottom - 1)
    }

    /** Checklist #297, a week's goal: grows and keeps clear of its controls. */
    @Test
    fun item297_aWeekGoalGrowsAndKeepsClearOfItsControls() {
        openAppAt(day)
        openWeekTab()
        paste(str(R.string.goal_hint, 1))
        assertCheckClear()
        await { textsOf(weekPageId(), TaskSection.GOALS) == listOf(long) }
    }

    /** Checklist #297, a month's event: grows and keeps clear of its remove button. */
    @Test
    fun item297_aMonthEventGrowsAndKeepsClearOfItsControls() {
        openAppAt(day)
        openMonthTab()
        val add = str(R.string.event_add)
        scrollTo(hasText(add))
        rule.onNode(hasText(add)).performClick()
        rule.waitForIdle()
        paste(str(R.string.event_hint))
        val box = bounds(longField())
        val x = bounds(onRowOf(longField(), hasContentDescription(remove)))
        assertFalse("the X at $x overlaps the text at $box", overlap(x, box))
        await { events().map { it.text } == listOf(long) }
    }

    private fun events() = runBlocking { container.pages.getEvents(monthPageId()) }.items

    /** Checklist #297: on the week's day card a 500-character priority takes at most 2 lines, and the card's other days stay there. */
    @Test
    fun item297_theWeekCardShowsAtMostTwoLinesOfAPriority() {
        seedTasks(day, TaskSection.PRIORITY, long)
        openAppAt(day)
        openWeekTab()
        val line = hasText("1. ", substring = true)
        await { exists(line) }
        val node = rule.onNode(line, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals("1. $long", node.config[SemanticsProperties.Text].single().text)
        assertEquals("two lines", 2, layoutOf(node).lineCount)
        assertEquals("the other six days", 6, count(hasText(str(R.string.week_day_empty))))
    }
}
