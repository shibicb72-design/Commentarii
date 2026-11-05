package com.shibi.commentarii.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.PeriodPageTestBase
import com.shibi.commentarii.testutil.inkOf
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /** Checklist #295: a far-future week, untouched: blank fields with their placeholders, "Nothing planned" seven times, no mood, no tint. */
    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item295_aFarFutureWeekIsBlankAndEveryDaySaysNothingPlanned() {
        openAppAt(day)
        openWeekTab()

        assertEquals("every day of The Week", 7, count(hasText(str(R.string.week_day_empty))))
        scrollTo(hasText(goalsTitle))
        assertTrue(exists(hasText(str(R.string.goal_hint, 1)) and hasSetTextAction()))
        scrollTo(hasText(reviewHint))
        assertTrue(exists(hasText(reviewHint) and hasSetTextAction()))
        assertEquals("no field holds any text", emptyList<String>(), filledFields())
        scrollTo(hasText(str(R.string.section_mood_week)))
        assertEquals("the mood row", 7, count(hasContentDescription(str(R.string.mood_none), substring = true)))

        scrollTo(hasText(str(R.string.section_week_days)))
        settleAnimations()
        val rows = rule.onAllNodes(hasText(str(R.string.week_day_empty)))
        val density = rule.activity.resources.displayMetrics.density
        val fills = (0 until 7).map { i -> pixel(rows[i], rows[i].fetchSemanticsNode().size.width / density + 6f, 2f) }
        assertEquals("all seven cards are the same plain colour: $fills", 1, fills.toSet().size)
        assertFalse("no today highlight", fills.first() == colors.primaryContainer)
    }

    /** Checklist #295: a far-future month, untouched: no day has entries, no cell a dot or a mood tint, blank fields with placeholders. */
    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item295_aFarFutureMonthIsBlankWithNoDotsOrTints() {
        openAppAt(day)
        openMonthTab()
        scrollTo(hasText(str(R.string.section_calendar)))
        settleAnimations()

        assertEquals("no day has entries", 0, count(hasContentDescription(str(R.string.month_day_has_entries), substring = true)))
        val density = rule.activity.resources.displayMetrics.density
        for (d in 1..31) {
            val cell = rule.onNode(hasContentDescription("March $d"))
            assertSameColor("March $d has no tint", appColors.card, pixel(cell, 0.7f, bounds(cell).height / density / 2))
        }
        // The dot sits under the number; a number from 13 to 22 appears once in the grid.
        for (d in 13..22) {
            val number = rule.onNode(hasText(d.toString()), useUnmergedTree = true)
            val box = bounds(number)
            assertSameColor("March $d has no dot", appColors.card, pixel(number, box.width / density / 2, box.height / density + 4.5f))
        }

        scrollTo(hasText(str(R.string.event_add)))
        scrollTo(hasText(goalsTitle))
        scrollTo(hasText(reviewHint))
        assertTrue(exists(hasText(reviewHint) and hasSetTextAction()))
        assertEquals("no field holds any text", emptyList<String>(), filledFields())
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
