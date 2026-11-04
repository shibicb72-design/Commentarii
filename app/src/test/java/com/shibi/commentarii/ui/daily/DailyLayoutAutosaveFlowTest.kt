package com.shibi.commentarii.ui.daily

import android.os.Looper
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import com.shibi.commentarii.R
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.domain.weekStart
import com.shibi.commentarii.testutil.DailyPageTestBase
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Checklist #79-#83: the footer, the two layouts, and autosave when leaving the page or the app. */
class DailyLayoutAutosaveFlowTest : DailyPageTestBase() {
    private val shown = LocalDate.of(2031, 3, 12)
    private val footer get() = str(R.string.daily_footer)
    private val priorities get() = str(R.string.section_priorities)
    private val todo get() = str(R.string.section_todo)
    private val schedule get() = str(R.string.section_schedule)
    private val dontForget get() = str(R.string.section_dont_forget)
    private val mood get() = str(R.string.section_mood)
    private val notes get() = str(R.string.section_notes)
    private val cardTitle get() = str(R.string.period_card_title)

    private fun boundsOf(text: String): Rect = bounds(rule.onNode(hasText(text)))

    private fun firstEmpty(hint: String) = rule.onAllNodes(hasText(hint) and hasSetTextAction())[0]

    private fun seedGoal() = runBlocking {
        val week = container.pages.getOrCreateWeekly(weekStart(shown))
        container.pages.saveTasks(
            week.id,
            TaskSection.GOALS,
            listOf(TaskItemEntity(pageId = week.id, section = TaskSection.GOALS, position = 0, text = "Walk")),
        )
    }

    @Test
    fun item079_footerIsAtTheBottomAndCentred() {
        open(shown)
        scrollTo(hasText(footer))
        val node = rule.onNode(hasText(footer)).fetchSemanticsNode()
        assertTrue("below Notes", node.boundsInRoot.top > boundsOf(notes).top)

        val layout = layoutOf(node)
        val leftGap = layout.getLineLeft(0)
        val rightGap = layout.size.width - layout.getLineRight(0)
        assertTrue("the line has room to be centred in", leftGap > 0f)
        assertEquals("line centred in its box", leftGap, rightGap, 2f)
        assertEquals("box centred on screen", rootWidth / 2f, node.boundsInRoot.center.x, 2f)
    }

    private val strip = SemanticsMatcher("the date strip") {
        SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.HorizontalScrollAxisRange in it.config
    }

    private fun assertTwoColumns(width: WindowWidthSizeClass) {
        seedGoal()
        open(shown, width)
        await { exists(hasText(cardTitle)) && exists(hasText(footer)) }
        val middle = rootWidth / 2f

        val left = listOf(priorities, todo, dontForget, mood).map { boundsOf(it) }
        val right = listOf(schedule, notes).map { boundsOf(it) }
        left.forEach { assertTrue("left column: $it", it.right <= middle) }
        right.forEach { assertTrue("right column: $it", it.left >= middle) }
        assertEquals("left column in order", left.map { it.top }.sorted(), left.map { it.top })
        assertEquals("right column in order", right.map { it.top }.sorted(), right.map { it.top })
        assertEquals("the columns start level", left.first().top, right.first().top, 2f)

        // A spanning part crosses the middle, and the whole column block sits between the top parts and the footer.
        val spanning = listOf(bounds(rule.onNode(strip)), boundsOf(cardTitle), boundsOf(footer))
        spanning.forEach { assertTrue("spans both columns: $it", it.left < middle && it.right > middle) }
        assertTrue("header above the columns", bounds(rule.onNode(strip)).bottom < left.first().top)
        assertTrue("goals card above the columns", boundsOf(cardTitle).bottom < left.first().top)
        assertTrue("footer below both", boundsOf(footer).top > maxOf(left.last().top, right.last().top))
    }

    @Test
    @Config(qualifiers = "en-rUS-w900dp-h3000dp-xxhdpi")
    fun item082_expandedWidthHasTwoColumns() = assertTwoColumns(WindowWidthSizeClass.Expanded)

    @Test
    @Config(qualifiers = "en-rUS-w700dp-h3000dp-xxhdpi")
    fun item082_mediumWidthHasTwoColumns() = assertTwoColumns(WindowWidthSizeClass.Medium)

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h3000dp-xxhdpi")
    fun item083_compactWidthHasOneColumnInItsOwnOrder() {
        open(shown, WindowWidthSizeClass.Compact)
        await { exists(hasText(footer)) }
        val sections = listOf(priorities, todo, schedule, dontForget, mood, notes).map { boundsOf(it) }
        assertEquals("in this order", sections.map { it.top }.sorted(), sections.map { it.top })
        val lefts = sections.map { it.left }
        assertTrue("one column: all start at the same x, $lefts", lefts.all { abs(it - lefts.first()) < 2f })
    }

    private fun notesText(date: LocalDate) = runBlocking {
        container.pages.getBlockDocument(pageId(date), SectionKind.NOTES)
    }.blocks.joinToString("\n") { it.text }

    private fun assertEverythingSaved(date: LocalDate) {
        await {
            texts(date, TaskSection.PRIORITY) == listOf("my priority") &&
                texts(date, TaskSection.TODO) == listOf("my todo") &&
                texts(date, TaskSection.DONT_FORGET) == listOf("my reminder") &&
                notesText(date) == "my notes"
        }
    }

    private fun assertEverythingOnScreen() {
        listOf("my priority", "my todo", "my reminder", "my notes").forEach { field(it).assertExists() }
    }

    private fun typeInEverySection() {
        field(str(R.string.daily_priority_hint, 1)).performTextInput("my priority")
        scrollTo(hasText(str(R.string.daily_todo_hint)))
        firstEmpty(str(R.string.daily_todo_hint)).performTextInput("my todo")
        scrollTo(hasText(str(R.string.daily_dont_forget_hint)))
        firstEmpty(str(R.string.daily_dont_forget_hint)).performTextInput("my reminder")
        field(str(R.string.daily_notes_hint)).performTextInput("my notes")
    }

    @Test
    fun item080_autosaveWritesAfterTheDebounceWithoutLeaving() {
        open(shown)
        typeInEverySection()
        assertEverythingSaved(shown)
        assertEverythingOnScreen()
    }

    @Test
    fun item080_textInSeveralSectionsSurvivesAnImmediateTabChangeAndReturn() {
        openApp()
        typeInEverySection()
        tapTab(R.string.tab_week)
        tapTab(R.string.tab_today)
        awaitLoaded()

        assertEverythingOnScreen()
        assertEverythingSaved(today)
    }

    @Test
    fun item080_leavingForAnotherDayFlushesWhatWasJustTyped() {
        open(shown)
        field(str(R.string.daily_priority_hint, 1)).performTextInput("my priority")
        // The date strip's next day, tapped at once: this view model is cleared before its debounce can fire.
        rule.onNode(hasText("13", substring = false) and hasText("Thu")).performClick()
        await { exists(hasText("Thursday, Mar 13")) }
        await { texts(shown, TaskSection.PRIORITY) == listOf("my priority") }
    }

    /**
     * #81. A write seen within the 500 ms debounce of the start of typing can only have come from the flush on stop, since the
     * debounce fires at least that long after the edit. A slow machine can take too long to prove it, so that is retried, and
     * the test fails only if no attempt was fast enough. Without the flush every attempt takes over 500 ms.
     */
    @Test
    fun item081_stoppingTheActivityFlushesBeforeTheDebounceAndTheTextIsThereOnReopening() {
        open(shown)
        val scenario = rule.activityRule.scenario
        val delays = mutableListOf<Long>()
        val hints = listOf(1, 2, 3).map { str(R.string.daily_priority_hint, it) }
        for (hint in hints) {
            val text = "typed in $hint"
            val startedAt = System.currentTimeMillis()
            field(hint).performTextInput(text)
            scenario.moveToState(Lifecycle.State.CREATED)
            val deadline = System.currentTimeMillis() + 10_000
            while (text !in texts(shown, TaskSection.PRIORITY)) {
                check(System.currentTimeMillis() < deadline) { "$text was never saved" }
                shadowOf(Looper.getMainLooper()).idle()
                Thread.sleep(2)
            }
            delays += runBlocking { container.pages.getPage(pageId(shown))!!.updatedAt }.toEpochMilli() - startedAt
            scenario.moveToState(Lifecycle.State.RESUMED)
            rule.waitForIdle()
            if (delays.last() < 500) break
        }
        assertTrue("saved before the debounce could fire; delays $delays ms", delays.last() < 500)

        val saved = texts(shown, TaskSection.PRIORITY)
        reopen()
        saved.forEach { field(it).assertExists() }
    }
}
