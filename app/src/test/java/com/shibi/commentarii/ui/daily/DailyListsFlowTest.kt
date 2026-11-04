package com.shibi.commentarii.ui.daily

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasImeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import com.shibi.commentarii.R
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.DailyPageTestBase
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/** Checklist #56-#63: Top Priorities, To-Do List and Don't Forget, with every edit read back from the database. */
class DailyListsFlowTest : DailyPageTestBase() {
    private val shown = LocalDate.of(2031, 3, 12)

    private val priorities get() = str(R.string.section_priorities)
    private val todoTitle get() = str(R.string.section_todo)
    private val dontForgetTitle get() = str(R.string.section_dont_forget)
    private val moodTitle get() = str(R.string.section_mood)
    private val scheduleTitle get() = str(R.string.section_schedule)
    private val todoHint get() = str(R.string.daily_todo_hint)
    private val rememberHint get() = str(R.string.daily_dont_forget_hint)
    private val remove get() = str(R.string.daily_remove)
    private val moveUp get() = str(R.string.daily_move_up)
    private val moveDown get() = str(R.string.daily_move_down)

    private fun priorityHint(i: Int) = str(R.string.daily_priority_hint, i)

    private fun strikeOf(text: String) = layoutOf(field(text).fetchSemanticsNode()).layoutInput.style.textDecoration

    private fun topOf(text: String) = bounds(rule.onNode(hasText(text))).top

    /** How many [matcher] nodes sit between two section titles. Needs the whole page composed, so the window must be tall. */
    private fun countBetween(matcher: SemanticsMatcher, fromTitle: String, toTitle: String): Int {
        val from = topOf(fromTitle)
        val to = topOf(toTitle)
        return rule.onAllNodes(matcher).fetchSemanticsNodes().count { it.boundsInRoot.top in from..to }
    }

    private fun todoRows() = count(hasContentDescription(moveUp))

    private fun seedTodos(vararg texts: String) = seedTasks(shown, TaskSection.TODO, *texts)

    private fun todoOrderOnScreen(vararg texts: String) {
        val tops = texts.map { bounds(field(it)).top }
        assertEquals("order on screen", tops.sorted(), tops)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h3000dp-xxhdpi")
    fun item056_exactlyThreeNumberedRowsWithAChecksAndNoListControls() {
        open(shown)
        for (i in 1..3) {
            rule.onNode(hasText("$i.")).assertExists()
            field(priorityHint(i)).assertExists()
        }
        assertFalse(exists(hasText("4.")))
        assertFalse(exists(hasText(priorityHint(4))))

        val check = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox) and isToggleable()
        assertEquals(3, countBetween(check, priorities, todoTitle))
        for (description in listOf(remove, moveUp, moveDown)) {
            assertEquals("no $description", 0, countBetween(hasContentDescription(description), priorities, todoTitle))
        }
        assertEquals("no add button", 0, countBetween(hasText(str(R.string.daily_add_item)), priorities, todoTitle))

        // Control: the same counts do see those controls one card further down.
        assertEquals(3, countBetween(hasContentDescription(remove), todoTitle, scheduleTitle))
        assertEquals(1, countBetween(hasText(str(R.string.daily_add_item)), todoTitle, scheduleTitle))
    }

    @Test
    fun item057_checkStrikesThroughAndTappingAgainUndoes() {
        open(shown)
        field(priorityHint(1)).performTextInput("Call home")
        rule.waitForIdle()
        checkOf(field("Call home")).assertIsOff()
        assertEquals(TextDecoration.None, strikeOf("Call home"))

        checkOf(field("Call home")).performClick()
        rule.waitForIdle()
        checkOf(field("Call home")).assertIsOn()
        assertEquals(TextDecoration.LineThrough, strikeOf("Call home"))
        await { tasks(shown, TaskSection.PRIORITY).any { it.text == "Call home" && it.done } }

        // A ticked check has a filled disc, an empty one has only its ring.
        settleAnimations()
        val ring = checkOf(field("Call home"))
        assertSameColor("filled check", colors.primary, pixel(ring, 12f, 20f))

        checkOf(field("Call home")).performClick()
        rule.waitForIdle()
        checkOf(field("Call home")).assertIsOff()
        assertEquals(TextDecoration.None, strikeOf("Call home"))
        await { tasks(shown, TaskSection.PRIORITY).any { it.text == "Call home" && !it.done } }
    }

    @Test
    fun item058_rowsUseTheDoneActionAndPastedNewlinesBecomeSpaces() {
        open(shown)
        field(priorityHint(1)).assert(hasImeAction(ImeAction.Done))
        scrollTo(hasText(todoHint))
        rule.onAllNodes(hasText(todoHint) and hasSetTextAction())[0].assert(hasImeAction(ImeAction.Done))

        field(priorityHint(1)).performTextInput("first\nsecond\nthird")
        await { exists(hasText("first second third") and hasSetTextAction()) }
        await { texts(shown, TaskSection.PRIORITY) == listOf("first second third") }

        field("first second third").performImeAction()
        rule.waitForIdle()
        field("first second third").assertExists()

        rule.onAllNodes(hasText(todoHint) and hasSetTextAction())[0].performTextInput("a\nb")
        await { texts(shown, TaskSection.TODO) == listOf("a b") }
    }

    @Test
    fun item059_addItemPutsANewEmptyRowAtTheBottom() {
        seedTodos("one", "two", "three")
        open(shown)
        scrollTo(hasText(str(R.string.daily_add_item)))
        assertEquals(3, todoRows())
        assertFalse("no empty row yet", exists(hasText(todoHint) and hasSetTextAction()))

        tapText(str(R.string.daily_add_item))
        await { todoRows() == 4 }

        val blank = bounds(field(todoHint))
        assertTrue("empty row below the others", blank.top > bounds(field("three")).top)
        assertTrue("above the button", blank.top < topOf(str(R.string.daily_add_item)))
    }

    @Test
    fun item060_checkStrikesThroughAndUntickRestores() {
        open(shown)
        scrollTo(hasText(todoHint))
        rule.onAllNodes(hasText(todoHint) and hasSetTextAction())[0].performTextInput("Buy milk")
        rule.waitForIdle()

        checkOf(field("Buy milk")).assertIsOff().performClick()
        rule.waitForIdle()
        checkOf(field("Buy milk")).assertIsOn()
        assertEquals(TextDecoration.LineThrough, strikeOf("Buy milk"))
        await { tasks(shown, TaskSection.TODO).any { it.text == "Buy milk" && it.done } }

        checkOf(field("Buy milk")).performClick()
        rule.waitForIdle()
        checkOf(field("Buy milk")).assertIsOff()
        assertEquals(TextDecoration.None, strikeOf("Buy milk"))
        await { tasks(shown, TaskSection.TODO).any { it.text == "Buy milk" && !it.done } }
    }

    @Test
    fun item061_arrowsReorderFilledRowsOnScreenAndInTheDatabase() {
        seedTodos("one", "two", "three")
        open(shown)
        scrollTo(hasText("three"))
        rule.onAllNodes(hasContentDescription(moveUp))[0].assertIsNotEnabled()
        rule.onAllNodes(hasContentDescription(moveDown))[2].assertIsNotEnabled()
        rule.onAllNodes(hasContentDescription(moveDown))[0].assertIsEnabled()
        rule.onAllNodes(hasContentDescription(moveUp))[2].assertIsEnabled()

        rule.onAllNodes(hasContentDescription(moveDown))[0].performClick()
        rule.waitForIdle()
        todoOrderOnScreen("two", "one", "three")
        await { texts(shown, TaskSection.TODO) == listOf("two", "one", "three") }

        rule.onAllNodes(hasContentDescription(moveUp))[2].performClick()
        rule.waitForIdle()
        todoOrderOnScreen("two", "three", "one")
        await { texts(shown, TaskSection.TODO) == listOf("two", "three", "one") }

        rule.onAllNodes(hasContentDescription(moveUp))[0].assertIsNotEnabled()
        rule.onAllNodes(hasContentDescription(moveDown))[2].assertIsNotEnabled()
        assertEquals("nothing was removed", 3, todoRows())
    }

    @Test
    fun item062_removingNeverLeavesFewerThanThreeRows() {
        seedTodos("a", "b", "c", "d", "e")
        open(shown)
        scrollTo(hasText("a"))
        assertEquals(5, todoRows())

        for (expectedRows in listOf(4, 3, 3, 3, 3, 3)) {
            rule.onAllNodes(hasContentDescription(remove))[0].performClick()
            rule.waitForIdle()
            assertEquals(expectedRows, todoRows())
        }
        assertEquals("only blanks are left", 3, count(hasText(todoHint) and hasSetTextAction()))
        for (gone in listOf("a", "b", "c", "d", "e")) assertFalse(exists(hasText(gone) and hasSetTextAction()))
        await { texts(shown, TaskSection.DONT_FORGET).isEmpty() && texts(shown, TaskSection.TODO).isEmpty() }
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h3000dp-xxhdpi")
    fun item063_addLineTypeAndRemoveKeepsAtLeastThreeBulletRowsWithNoCheck() {
        open(shown)
        assertEquals(3, count(hasText(rememberHint) and hasSetTextAction()))
        assertEquals("no checks here", 0, countBetween(isToggleable(), dontForgetTitle, moodTitle))
        assertEquals("control: to-do has them", 3, countBetween(isToggleable(), todoTitle, scheduleTitle))

        tapText(str(R.string.daily_add_line))
        await { count(hasText(rememberHint) and hasSetTextAction()) == 4 }

        rule.onAllNodes(hasText(rememberHint) and hasSetTextAction())[0].performTextInput("Passport")
        await { texts(shown, TaskSection.DONT_FORGET) == listOf("Passport") }

        fun removesInCard() = countBetween(hasContentDescription(remove), dontForgetTitle, moodTitle)
        assertEquals(4, removesInCard())
        fun tapRemoveOfFirstRow() {
            val top = topOf(dontForgetTitle)
            val nodes = rule.onAllNodes(hasContentDescription(remove)).fetchSemanticsNodes()
            rule.onAllNodes(hasContentDescription(remove))[nodes.indexOfFirst { it.boundsInRoot.top > top }].performClick()
            rule.waitForIdle()
        }
        tapRemoveOfFirstRow()
        await { removesInCard() == 3 }
        assertFalse(exists(hasText("Passport") and hasSetTextAction()))
        await { texts(shown, TaskSection.DONT_FORGET).isEmpty() }

        repeat(2) {
            tapRemoveOfFirstRow()
            assertEquals("never fewer than three", 3, removesInCard())
        }
        assertEquals(3, count(hasText(rememberHint) and hasSetTextAction()))
    }
}
