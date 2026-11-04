package com.shibi.commentarii.ui.daily

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.style.TextDecoration
import com.shibi.commentarii.R
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.domain.weekStart
import com.shibi.commentarii.testutil.DailyPageTestBase
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checklist #71-#78: Mood, Notes and the read-only "This week & month" card. */
class DailyMoodNotesGoalsFlowTest : DailyPageTestBase() {
    private val shown = LocalDate.of(2031, 3, 12)
    private val moodLabels get() = listOf(R.string.mood_5, R.string.mood_4, R.string.mood_3, R.string.mood_2, R.string.mood_1).map { str(it) }
    private val veryHappy get() = str(R.string.mood_5)
    private val sad get() = str(R.string.mood_2)

    private val cardTitle get() = str(R.string.period_card_title)
    private val thisWeek get() = str(R.string.period_card_week)
    private val thisMonth get() = str(R.string.period_card_month)
    private val notesHint get() = str(R.string.daily_notes_hint)

    private fun face(label: String) = rule.onNode(hasContentDescription(label))

    /** A point inside the face's round button but outside its 32 dp icon, where only the highlight can paint. */
    private fun assertHighlighted(selected: String?) {
        settleAnimations()
        for (label in moodLabels) {
            val expected = if (label == selected) colors.primaryContainer else appColors.card
            assertSameColor("$label's circle", expected, pixel(face(label), 20f, 2f))
        }
    }

    private fun mood(date: LocalDate = shown) = runBlocking { container.pages.getMood(date) }?.mood

    private fun openOnMood(date: LocalDate = shown) {
        open(date)
        scrollTo(hasContentDescription(veryHappy))
    }

    private fun seedGoals(week: List<Pair<String, Boolean>> = emptyList(), month: List<Pair<String, Boolean>> = emptyList(), date: LocalDate = shown) {
        runBlocking {
            val weekPage = container.pages.getOrCreateWeekly(weekStart(date))
            val monthPage = container.pages.getOrCreateMonthly(YearMonth.from(date).atDay(1))
            container.pages.saveTasks(weekPage.id, TaskSection.GOALS, goalRows(weekPage.id, week))
            container.pages.saveTasks(monthPage.id, TaskSection.GOALS, goalRows(monthPage.id, month))
        }
    }

    private fun goalRows(pageId: Long, goals: List<Pair<String, Boolean>>) = goals.mapIndexed { i, (text, done) ->
        TaskItemEntity(pageId = pageId, section = TaskSection.GOALS, position = i, text = text, done = done)
    }

    private fun decorationOf(text: String): TextDecoration? = layoutOf(
        rule.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode(),
    ).layoutInput.style.textDecoration

    @Test
    fun item071_tappingAFaceHighlightsItAndAnotherMovesTheHighlight() {
        openOnMood()
        assertEquals(listOf("Very happy", "Happy", "Neutral", "Sad", "Very sad"), moodLabels)
        for (label in moodLabels) face(label).assertExists()
        assertHighlighted(null)

        face(veryHappy).performClick()
        await { mood() == 5 }
        assertHighlighted(veryHappy)

        face(sad).performClick()
        await { mood() == 2 }
        assertHighlighted(sad)
    }

    @Test
    fun item072_tappingTheSelectedFaceAgainClearsTheMood() {
        openOnMood()
        face(veryHappy).performClick()
        await { mood() == 5 }
        assertHighlighted(veryHappy)
        rule.waitForIdle()

        face(veryHappy).performClick()
        await { mood() == null }
        assertNull(runBlocking { container.pages.getMood(shown) })
        assertHighlighted(null)

        // Control: it can be set again.
        face(sad).performClick()
        await { mood() == 2 }
    }

    @Test
    fun item073_moodShowsOnThatDaysWeekPageAndMonthCalendar() {
        openApp()
        scrollTo(hasContentDescription(veryHappy))
        face(veryHappy).performClick()
        await { mood(today) == 5 }

        tapTab(R.string.tab_week)
        // Loading makes the page taller and pushes the mood card out of the lazy list, so scroll to the face on every try.
        await { runCatching { scrollTo(hasContentDescription(veryHappy)) }.isSuccess }
        val faces = rule.onAllNodes(hasContentDescription(str(R.string.mood_none))).fetchSemanticsNodes()
            .plus(rule.onAllNodes(hasContentDescription(veryHappy)).fetchSemanticsNodes())
            .sortedBy { it.boundsInRoot.left }
        assertEquals("a face per day, Monday first", 7, faces.size)
        val todayFace = faces[today.dayOfWeek.value - 1]
        assertEquals(listOf(veryHappy), todayFace.config[SemanticsProperties.ContentDescription])

        tapTab(R.string.tab_month)
        val cell = today.format(DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault())) + ", " + veryHappy
        await { runCatching { scrollTo(hasContentDescription(cell, substring = true)) }.isSuccess }
        assertEquals("only that day", 1, count(hasContentDescription(veryHappy, substring = true)))
        settleAnimations()
        assertSameColor(
            "month cell tint",
            appColors.mood[4],
            pixel(rule.onNode(hasContentDescription(cell, substring = true)), 5f, 5f),
        )
    }

    private fun savedNotes() = runBlocking { container.pages.getBlockDocument(pageId(shown), SectionKind.NOTES) }.blocks.map { it.text }

    @Test
    fun item074_notesKeepNewlinesAndShowAtLeastFiveLinesAndAPlaceholder() {
        open(shown)
        val empty = field(notesHint)
        val layout = layoutOf(empty.fetchSemanticsNode())
        val fiveLines = 5 * layout.multiParagraph.getLineHeight(0)
        assertTrue(
            "field height ${bounds(empty).height} holds at least five lines ($fiveLines)",
            bounds(empty).height >= fiveLines - 1f,
        )

        empty.performTextInput("first\nsecond\nthird")
        rule.waitForIdle()
        assertFalse("placeholder gone", exists(hasText(notesHint) and hasSetTextAction()))
        val typed = field("first\nsecond\nthird")
        assertEquals(3, layoutOf(typed.fetchSemanticsNode()).lineCount)
        await { savedNotes() == listOf("first", "second", "third") }

        reopen()
        field("first\nsecond\nthird").assertExists()
    }

    @Test
    fun item075_noCardWhenTheDaysWeekAndMonthHaveNoGoals() {
        val elsewhere = shown.plusMonths(2)
        seedGoals(week = listOf("Elsewhere week" to false), month = listOf("Elsewhere month" to false), date = elsewhere)
        seedLoadAnchor(shown)
        open(shown)
        awaitLoadAnchor()
        settleFor(300)
        assertFalse(exists(hasText(cardTitle)))
        assertFalse(exists(hasText("• Elsewhere week")))

        // Control: the same goals show on a day in their own week.
        goToDateWithPicker(elsewhere)
        await { exists(hasText(cardTitle)) && exists(hasText("• Elsewhere week")) }
    }

    @Test
    fun item076_cardShowsBothPeriodsBulletsAndStrikesDoneGoals() {
        seedGoals(week = listOf("Walk" to false, "Read" to true), month = listOf("Save" to false, "Paint" to true))
        open(shown)
        await { exists(hasText(cardTitle)) && exists(hasText("• Paint", substring = false)) }

        rule.onNode(hasText(thisWeek)).assertExists()
        rule.onNode(hasText(thisMonth)).assertExists()
        assertEquals(TextDecoration.None, decorationOf("• Walk"))
        assertEquals(TextDecoration.LineThrough, decorationOf("• Read"))
        assertEquals(TextDecoration.None, decorationOf("• Save"))
        assertEquals(TextDecoration.LineThrough, decorationOf("• Paint"))
        assertTrue(
            "week before month",
            bounds(rule.onNode(hasText(thisWeek))).top < bounds(rule.onNode(hasText(thisMonth))).top,
        )
    }

    @Test
    fun item076_goalTypedOnTheWeekPageShowsWhenReturningToTheDay() {
        openApp()
        assertFalse(exists(hasText(cardTitle)))

        tapTab(R.string.tab_week)
        val hint = hasText(str(R.string.goal_hint, 1)) and hasSetTextAction()
        await { runCatching { rule.onNode(pageList).performScrollToNode(hint) }.isSuccess && exists(hint) }
        await { runCatching { rule.onNode(hint).performTextInput("Run 5k") }.isSuccess }
        rule.waitForIdle()
        tapTab(R.string.tab_today)

        await { exists(hasText(cardTitle)) && exists(hasText("• Run 5k")) }
        rule.onNode(hasText(thisWeek)).assertExists()
        assertFalse("no month goals, so no month half", exists(hasText(thisMonth)))
    }

    @Test
    fun item077_titleRowCollapsesAndExpandsTheGoals() {
        seedGoals(week = listOf("Walk" to false))
        open(shown)
        await { exists(hasText("• Walk")) }
        val hide = str(R.string.period_card_collapse)
        val show = str(R.string.period_card_expand)
        rule.onNode(hasContentDescription(hide)).assertExists()

        tapInDialog(cardTitle)
        rule.onNode(hasContentDescription(show)).assertExists()
        assertFalse(exists(hasContentDescription(hide)))
        assertFalse("goals hidden", exists(hasText("• Walk")))
        assertFalse(exists(hasText(thisWeek)))

        tapInDialog(cardTitle)
        rule.onNode(hasContentDescription(hide)).assertExists()
        assertFalse(exists(hasContentDescription(show)))
        rule.onNode(hasText("• Walk")).assertExists()
    }

    @Test
    fun item078_tappingAPeriodOpensItsPageAndBackReturnsToTheDay() {
        seedGoals(week = listOf("Walk" to false), month = listOf("Save" to false), date = today)
        openApp()
        await { exists(hasText(thisWeek)) && exists(hasText(thisMonth)) }
        val daily = routes.single()
        val weekRoute = "week/${weekStart(today)}"
        val monthRoute = "month/${YearMonth.from(today)}"

        tapInDialog(thisWeek)
        await { routes.last() == weekRoute }
        rule.onNode(tab(R.string.tab_week)).assertIsSelected()
        pressBackInApp()
        await { routes == listOf(daily) && exists(hasText(thisMonth)) }

        tapInDialog(thisMonth)
        await { routes.last() == monthRoute }
        rule.onNode(tab(R.string.tab_month)).assertIsSelected()
        pressBackInApp()
        await { routes == listOf(daily) && exists(hasText("• Walk")) }

        // The goals are part of the tap target.
        rule.onNode(hasText("• Walk")).performClick()
        await { routes.last() == weekRoute }
        pressBackInApp()
        await { routes == listOf(daily) && exists(hasText("• Save")) }
        rule.onNode(hasText("• Save")).performClick()
        await { routes.last() == monthRoute }
    }
}
