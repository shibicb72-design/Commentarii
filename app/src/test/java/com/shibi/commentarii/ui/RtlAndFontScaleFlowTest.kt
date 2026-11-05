package com.shibi.commentarii.ui

import android.util.LayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.MainActivityTestBase
import com.shibi.commentarii.testutil.runWithLooper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Checklist #303, #305: Arabic right-to-left, and the largest font and display size, on the real MainActivity. */
class RtlAndFontScaleFlowTest : MainActivityTestBase() {
    private val tabs = listOf(R.string.tab_today, R.string.tab_week, R.string.tab_month, R.string.tab_notes, R.string.tab_search, R.string.tab_settings)

    /** Checklist #303: the bar and the period arrows mirror, with Previous now on the right; the month name is in Arabic. */
    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-w412dp-h915dp-xxhdpi")
    fun item303_rightToLeftMirrorsTheBarAndArrowsAndLocalizesTheDates() {
        assertEquals(LayoutDirection.RTL, rule.activity.resources.configuration.layoutDirection)
        await { exists(tab(R.string.tab_today)) }

        val xs = tabs.map { boundsOf(tab(it)).left }
        assertEquals("Today is rightmost, Settings leftmost", xs.sortedDescending(), xs)

        openTab(R.string.tab_month)
        val previous = hasContentDescription(str(R.string.month_previous))
        val next = hasContentDescription(str(R.string.month_next))
        await { exists(previous) && exists(next) }
        assertTrue("Previous sits right of Next", boundsOf(previous).left > boundsOf(next).left)

        val arabicTitle = androidx.compose.ui.test.SemanticsMatcher("Arabic text") { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { t -> Regex("\\p{IsArabic}").containsMatchIn(t.text) }
        }
        await { exists(arabicTitle) }
    }

    /** Checklist #303: an editor's back arrow sits at the right of its top bar in Arabic. */
    @Test
    @Config(qualifiers = "ar-rEG-ldrtl-w412dp-h915dp-xxhdpi")
    fun item303_theEditorsBackArrowIsOnTheRightInRightToLeft() {
        runWithLooper { container.pages.createNote("Mirrored") }
        openTab(R.string.tab_notes)
        await { exists("Mirrored") }
        rule.onNode(hasText("Mirrored")).performClick()
        val backArrow = hasContentDescription(str(R.string.ed_back))
        await { exists(backArrow) }
        val width = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertTrue("back arrow ${boundsOf(backArrow)} in the right half of $width", boundsOf(backArrow).left > width / 2)
    }

    /**
     * Checklist #303, dates after the language changes while the process lives on, which is what a language change in system settings
     * does: the activity is recreated, nothing else restarts. The Week and Month pages are first drawn in English, then the same
     * process goes to Arabic, and the week card's day name, like the rest of the page, must follow.
     */
    @Test
    fun item303_dayNamesFollowALanguageChangeWhileTheProcessLives() {
        val english = Locale.getDefault()
        val arabic = Locale.forLanguageTag("ar-EG")
        val today = LocalDate.now(container.clock)
        val monday = today.with(DayOfWeek.MONDAY)
        val monLabel = monday.format(DateTimeFormatter.ofPattern("EEE", english))
        val arabicDay = monday.format(DateTimeFormatter.ofPattern("EEE", arabic))
        assertTrue("fixture: the two names differ, $monLabel and $arabicDay", monLabel != arabicDay)

        openTab(R.string.tab_week)
        await { exists(hasScrollToNodeAction()) && exists(hasText(str(R.string.goal_hint, 1))) }
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(monLabel))
        await { exists(monLabel) }
        openTab(R.string.tab_month)
        await { exists(hasContentDescription(str(R.string.month_previous))) }

        try {
            Locale.setDefault(arabic)
            RuntimeEnvironment.setQualifiers("ar-rEG-ldrtl")
            rule.activityRule.scenario.recreate()
            rule.waitForIdle()
            openTab(R.string.tab_week)
            await { exists(hasScrollToNodeAction()) && exists(hasText(str(R.string.goal_hint, 1))) }
            val shownDay = runCatching { rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(arabicDay)) }
            if (shownDay.isFailure) rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(monLabel))
            rule.waitForIdle()
            assertTrue("the week card still says \"$monLabel\" in Arabic", shownDay.isSuccess && exists(arabicDay) && !exists(monLabel))

            openTab(R.string.tab_month)
            val cell = today.format(DateTimeFormatter.ofPattern("MMMM d", arabic))
            await { exists(hasContentDescription(str(R.string.month_previous))) }
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(R.string.section_calendar)))
            runCatching { await { exists(hasContentDescription(cell, substring = true)) } }
            assertTrue("no calendar cell says \"$cell\"", exists(hasContentDescription(cell, substring = true)))
            rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(str(R.string.event_add)))
            rule.waitForIdle()
            rule.onNode(hasText(str(R.string.event_add))).performClick()
            rule.waitForIdle()
            val arabicMonth = today.format(DateTimeFormatter.ofPattern("MMM", arabic))
            val englishMonth = today.format(DateTimeFormatter.ofPattern("MMM", english))
            runCatching { await { exists(hasClickAction() and hasText(arabicMonth, substring = true)) } }
            assertTrue("the event's date button is not in Arabic", exists(hasClickAction() and hasText(arabicMonth, substring = true)))
            assertFalse("and nothing clickable says \"$englishMonth\"", exists(hasClickAction() and hasText(englishMonth, substring = true)))
        } finally {
            Locale.setDefault(english)
        }
    }

    /** Control for #303: the same checks in English show the bar left to right and Previous on the left. */
    @Test
    fun item303_control_inEnglishTheBarRunsLeftToRight() {
        await { exists(tab(R.string.tab_today)) }
        val xs = tabs.map { boundsOf(tab(it)).left }
        assertEquals(xs.sorted(), xs)
        openTab(R.string.tab_month)
        val previous = hasContentDescription(str(R.string.month_previous))
        val next = hasContentDescription(str(R.string.month_next))
        await { exists(previous) && exists(next) }
        assertTrue(boundsOf(previous).left < boundsOf(next).left)
    }

    private fun layoutOf(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    /**
     * Checklist #305: font scale 2 on a narrow window (the biggest Display size leaves about 320 dp): every bottom bar label fits its
     * tab on one line, not cut off, and the tabs still tile the window. Then every tab opens without a crash.
     */
    @Test
    @Config(qualifiers = "en-rUS-w320dp-h640dp-xxhdpi")
    fun item305_atTheLargestSizesTheBottomBarLabelsAreWholeAndEveryTabOpens() {
        RuntimeEnvironment.setFontScale(2f)
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        await { exists(tab(R.string.tab_today)) }
        assertEquals(2f, rule.activity.resources.configuration.fontScale)

        for (label in tabs) {
            val text = str(label)
            val layout = layoutOf(text)
            val box = rule.onNode(hasText(text), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val width = boundsOf(tab(label))
            assertFalse("\"$text\" is cut off: ${layout.lineCount} lines in ${layout.size}", layout.hasVisualOverflow)
            assertEquals("\"$text\" on one line", 1, layout.lineCount)
            assertTrue("\"$text\" $box inside its tab $width", box.left >= width.left - 1 && box.right <= width.right + 1)
            // The label shrinks to fit, so fitting says nothing about being readable. The layout's style keeps the unshrunk size, so the
            // drawn size comes from the drawn width against the width at that style. On screen, sp counts double at this scale.
            val input = layout.layoutInput
            val full = TextMeasurer(input.fontFamilyResolver, input.density, input.layoutDirection).measure(text, input.style, softWrap = false, maxLines = 1)
            val drawnSp = input.style.fontSize.value * layout.size.width / full.size.width
            println("item305: \"$text\" drawn at $drawnSp sp (style ${input.style.fontSize}), ${drawnSp * 2f} on screen")
            assertTrue("\"$text\" drawn at ${drawnSp * 2f} on screen", drawnSp * 2f >= 10f)
        }
        for (label in tabs) openTab(label)
    }

    /** Checklist #305: at the same sizes the editor's five toolbar buttons stay inside the window, side by side. */
    @Test
    @Config(qualifiers = "en-rUS-w320dp-h640dp-xxhdpi")
    fun item305_atTheLargestSizesTheEditorToolbarButtonsAreAllInsideTheWindow() {
        runWithLooper { container.pages.createNote("Scaled") }
        RuntimeEnvironment.setFontScale(2f)
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        openTab(R.string.tab_notes)
        await { exists("Scaled") }
        rule.onNode(hasText("Scaled")).performClick()
        await { exists(hasContentDescription(str(R.string.ed_bold))) }

        val width = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        val buttons = listOf(R.string.ed_bold, R.string.ed_h1, R.string.ed_h2, R.string.ed_bullet, R.string.ed_checkbox)
            .map { boundsOf(hasContentDescription(str(it))) }
        for (box in buttons) assertTrue("$box inside $width", box.left >= 0 && box.right <= width + 1)
        assertEquals("left to right", buttons.map { it.left }.sorted(), buttons.map { it.left })
        buttons.zipWithNext { a, b -> assertTrue("$a and $b overlap", a.right <= b.left + 1) }
    }
}
