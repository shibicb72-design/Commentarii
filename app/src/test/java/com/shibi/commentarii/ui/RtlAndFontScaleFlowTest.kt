package com.shibi.commentarii.ui

import android.util.LayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.MainActivityTestBase
import com.shibi.commentarii.testutil.runWithLooper
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
