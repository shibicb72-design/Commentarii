package com.shibi.commentarii.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.PeriodPageTestBase
import com.shibi.commentarii.testutil.inkOf
import com.shibi.commentarii.ui.week.WeekViewModel
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/** Checklist #295, #296: every empty page and empty state on a fresh database, in the real AppNav. */
class EmptyStatesFlowTest : PeriodPageTestBase() {
    private val chipRow = SemanticsMatcher("a sideways list") {
        SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.HorizontalScrollAxisRange in it.config
    }

    private val settingsScroll = SemanticsMatcher("the settings page") {
        SemanticsProperties.VerticalScrollAxisRange in it.config && SemanticsActions.ScrollBy in it.config
    }

    private fun filledFields() = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
        .map { it.config[SemanticsProperties.EditableText].text }.filter { it.isNotEmpty() }

    private fun assertReadable(text: String) {
        settleAnimations()
        val ink = inkOf(windowBitmap(), rule.onNode(hasText(text)).fetchSemanticsNode())
        assertTrue("\"$text\": $ink", ink.ratio >= 4.5)
    }

    private fun assertCentred(text: String) {
        val box = bounds(rule.onNode(hasText(text)))
        assertTrue("\"$text\" centred: $box in width $rootWidth", abs((box.left + box.right) / 2 - rootWidth / 2) < dp(2))
    }

    /** The field showing [hint] is there once scrolled to, and no field composed at that scroll position holds any text. */
    private fun assertPlaceholder(hint: String) {
        val row = hasText(hint) and hasSetTextAction()
        scrollTo(row)
        assertTrue("\"$hint\" is showing", exists(row))
        assertNoFieldHoldsText("at \"$hint\"")
    }

    private fun assertNoFieldHoldsText(where: String) = assertEquals("no field holds any text $where", emptyList<String>(), filledFields())

    /**
     * Each day card's own fill: the card merges its words, so the node is the whole card, and this point is inside it, above the
     * words. The same read as WeekPageFlowTest's highlight test. Past the node's width would be the section behind the card.
     */
    private fun weekDayFills(): List<Color> {
        scrollTo(hasText(str(R.string.section_week_days)))
        settleAnimations()
        val rows = rule.onAllNodes(hasText(str(R.string.week_day_empty)))
        return (0 until 7).map { i -> pixel(rows[i], 200f, 3f) }
    }

    /** Further apart than [assertSameColor] allows, so that assertion can tell these two colours from each other. */
    private fun assertTellsApart(what: String, a: Color, b: Color) {
        val apart = abs(a.red - b.red) >= 0.02f || abs(a.green - b.green) >= 0.02f || abs(a.blue - b.blue) >= 0.02f
        assertTrue("$what: $a and $b", apart)
    }

    /** Where a day's square shows its tint: just inside its left edge, half way down. */
    private fun tintPixel(cell: SemanticsNodeInteraction): Color {
        val density = rule.activity.resources.displayMetrics.density
        return pixel(cell, 0.7f, bounds(cell).height / density / 2)
    }

    /** The number of a day from 13 to 22, each of which appears once in the grid. */
    private fun dayNumber(d: Int) = rule.onNode(hasText(d.toString()), useUnmergedTree = true)

    /** Where a day's dot is drawn: under its number. */
    private fun dotPixel(number: SemanticsNodeInteraction): Color {
        val density = rule.activity.resources.displayMetrics.density
        val box = bounds(number)
        return pixel(number, box.width / density / 2, box.height / density + 4.5f)
    }

    /** Checklist #295: a far-future week, untouched: every section blank with its placeholder, "Nothing planned" seven times, no mood, no tint. */
    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item295_aFarFutureWeekIsBlankAndEveryDaySaysNothingPlanned() {
        openAppAt(day)
        openWeekTab()

        assertEquals("every day of The Week", 7, count(hasText(str(R.string.week_day_empty))))
        scrollTo(hasText(goalsTitle))
        for (i in 1..WeekViewModel.GOAL_SLOTS) assertPlaceholder(str(R.string.goal_hint, i))
        assertPlaceholder(todoHint)
        assertPlaceholder(str(R.string.daily_dont_forget_hint))
        scrollTo(hasText(str(R.string.section_mood_week)))
        assertEquals("the mood row", 7, count(hasContentDescription(str(R.string.mood_none), substring = true)))
        assertNoFieldHoldsText("at the mood row")
        assertPlaceholder(notesHint)
        scrollTo(hasText(str(R.string.section_review_well)))
        assertPlaceholder(reviewHint)
        scrollTo(hasText(str(R.string.section_review_improve)))
        assertEquals("both review boxes", 2, count(hasText(reviewHint) and hasSetTextAction()))
        assertNoFieldHoldsText("at the reviews")

        val fills = weekDayFills()
        assertEquals("all seven cards are the same plain colour: $fills", 1, fills.toSet().size)
        assertTellsApart("the read can tell a highlight from a plain card", colors.primaryContainer, colors.surfaceContainer)
        assertTellsApart("the read can tell a card from the section behind it", appColors.card, colors.surfaceContainer)
        fills.forEachIndexed { i, fill -> assertSameColor("day $i has no today highlight", colors.surfaceContainer, fill) }
    }

    /** Control for #295's week: the same reads on the same page, with one of the days being today, do see the highlight. */
    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item295_control_theSameReadsSeeTheTodayHighlight() {
        showWeek(monday, fixedClock(day))
        await { count(hasText(str(R.string.week_day_empty))) == 7 }

        val fills = weekDayFills()
        val wednesday = java.time.temporal.ChronoUnit.DAYS.between(monday, day).toInt()
        assertTellsApart("the read can tell a highlight from a plain card", colors.primaryContainer, colors.surfaceContainer)
        fills.forEachIndexed { i, fill ->
            assertSameColor("day $i", if (i == wednesday) colors.primaryContainer else colors.surfaceContainer, fill)
        }
    }

    /** Checklist #295: a far-future month, untouched: no day has entries, no cell a dot or a mood tint, every section blank with its placeholder. */
    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item295_aFarFutureMonthIsBlankWithNoDotsOrTints() {
        openAppAt(day)
        openMonthTab()
        scrollTo(hasText(str(R.string.section_calendar)))
        settleAnimations()

        assertEquals("no day has entries", 0, count(hasContentDescription(str(R.string.month_day_has_entries), substring = true)))
        for (d in 1..31) {
            assertSameColor("March $d has no tint", appColors.card, tintPixel(rule.onNode(hasContentDescription("March $d"))))
        }
        for (d in 13..22) assertSameColor("March $d has no dot", appColors.card, dotPixel(dayNumber(d)))

        // A month starts with no goals and no events: their sections are only the button that adds one.
        scrollTo(hasText(goalsTitle))
        assertEquals("no goal rows", 0, count(hasText(str(R.string.goal_hint, 1)) and hasSetTextAction()))
        assertTrue("Goals offers Add item", exists(hasText(addItem)))
        assertNoFieldHoldsText("at the goals")
        scrollTo(hasText(str(R.string.event_add)))
        assertEquals("no event rows", 0, count(hasText(str(R.string.event_hint)) and hasSetTextAction()))
        assertNoFieldHoldsText("at the events")
        assertPlaceholder(todoHint)
        assertPlaceholder(notesHint)
        assertPlaceholder(reviewHint)
    }

    /** Control for #295's month: the same two pixel reads see a seeded mood's tint and a seeded to-do's dot, and not on the day beside them. */
    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item295_control_theSameReadsSeeATintAndADot() {
        seedMood(LocalDate.of(2031, 3, 20), 3)
        seedTasks(LocalDate.of(2031, 3, 21), TaskSection.TODO, "Something")
        openAppAt(day)
        openMonthTab()
        scrollTo(hasText(str(R.string.section_calendar)))
        val entries = str(R.string.month_day_has_entries)
        await { exists(hasContentDescription(str(R.string.mood_3), substring = true)) && exists(hasContentDescription("March 21, $entries")) }
        settleAnimations()

        assertSameColor("the tint", appColors.mood[2], tintPixel(rule.onNode(hasContentDescription("March 20", substring = true))))
        val dotted = dayNumber(21)
        assertSameColor("the dot", layoutOf(dotted.fetchSemanticsNode()).layoutInput.style.color, dotPixel(dotted))

        assertSameColor("no tint beside them", appColors.card, tintPixel(rule.onNode(hasContentDescription("March 22"))))
        assertSameColor("no dot beside them", appColors.card, dotPixel(dayNumber(22)))
    }

    /**
     * Checklist #296: each empty state's exact words on the real screens. Notes and Search are centred; "Never exported." and the
     * log's line sit left-aligned in their column (noted for the report, the item says "centred/readable"). All meet 4.5:1.
     */
    @Test
    fun item296_everyEmptyStateSaysItsWordsAndIsReadable() {
        assertEquals("No notes yet. Tap + to write one.", str(R.string.nt_empty))
        assertEquals("No notes match this filter.", str(R.string.nt_empty_filtered))
        assertEquals("Nothing found.", str(R.string.sr_no_results))
        assertEquals("The log is empty.", str(R.string.debug_empty))
        assertEquals("Never exported.", str(R.string.settings_never_exported))
        runBlocking { container.pages.createFolder("Unused") }
        openApp()

        tapTab(R.string.tab_notes)
        await { exists(hasText(str(R.string.nt_empty))) }
        assertCentred(str(R.string.nt_empty))
        assertReadable(str(R.string.nt_empty))

        val chip = hasText("Unused") and hasAnyAncestor(chipRow)
        await { exists(chip) }
        rule.onNode(chip).performClick()
        await { exists(hasText(str(R.string.nt_empty_filtered))) }
        assertCentred(str(R.string.nt_empty_filtered))
        assertReadable(str(R.string.nt_empty_filtered))

        tapTab(R.string.tab_search)
        await { exists(hasText(str(R.string.sr_prompt))) }
        assertCentred(str(R.string.sr_prompt))
        assertReadable(str(R.string.sr_prompt))
        rule.onNode(hasSetTextAction()).performTextInput("zzzzqqq")
        await { exists(hasText(str(R.string.sr_no_results))) }
        assertCentred(str(R.string.sr_no_results))
        assertReadable(str(R.string.sr_no_results))

        tapTab(R.string.tab_settings)
        val never = hasText(str(R.string.settings_never_exported)) and hasAnyAncestor(settingsScroll)
        await { exists(never) }
        rule.onNode(never).performScrollTo()
        assertReadable(str(R.string.settings_never_exported))

        val debug = hasText(str(R.string.settings_debug_open)) and hasAnyAncestor(settingsScroll)
        rule.onNode(debug).performScrollTo().performClick()
        await { exists(hasText(str(R.string.debug_title))) }
        container.debugLog.clear()
        await { exists(hasText(str(R.string.debug_empty))) }
        assertReadable(str(R.string.debug_empty))
    }
}
