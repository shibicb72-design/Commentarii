package com.shibi.commentarii.ui

import android.view.ViewGroup
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.MainActivityTestBase
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Checklist #3 and #13 on the real MainActivity, whose calculateWindowSizeClass turns the configured width into the shell's
 * layout. System bars are injected the way ImeGapTest does it: Robolectric's window has none of its own. A real rotation and a
 * real fold are not exercised, only the widths they produce.
 */
class AppShellTest : MainActivityTestBase() {
    private val labels = listOf(
        R.string.tab_today,
        R.string.tab_week,
        R.string.tab_month,
        R.string.tab_notes,
        R.string.tab_search,
        R.string.tab_settings,
    )

    private val pageList = SemanticsMatcher("the page's vertical list") {
        SemanticsActions.ScrollToIndex in it.config && SemanticsProperties.VerticalScrollAxisRange in it.config
    }

    private val anyTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun awaitShell() = await { exists(tab(R.string.tab_today)) && exists(hasText("1.")) }

    private fun tabBounds(): List<Rect> = labels.map { boundsOf(tab(it)) }

    private fun assertBottomBar() {
        val tabs = tabBounds()
        val rootBottom = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        assertEquals("a bar's items share one row", 1, tabs.map { it.top }.distinct().size)
        assertEquals("left to right in tab order", tabs.map { it.left }.sorted(), tabs.map { it.left })
        assertEquals("six separate columns", 6, tabs.map { it.left }.distinct().size)
        assertTrue("at the bottom of the window", tabs[0].bottom > rootBottom * 0.8f)
    }

    private fun assertRail() {
        val tabs = tabBounds()
        val rootWidth = rule.onRoot().fetchSemanticsNode().boundsInRoot.width
        assertEquals("a rail's items share one column", 1, tabs.map { it.left }.distinct().size)
        assertEquals("top to bottom in tab order", tabs.map { it.top }.sorted(), tabs.map { it.top })
        assertEquals("six separate rows", 6, tabs.map { it.top }.distinct().size)
        assertTrue("at the left of the window", tabs[0].right < rootWidth * 0.3f)
    }

    /** Puts system bars on the Compose view; sizes are in dp. */
    private fun injectSystemBars(status: Int, navigationLeft: Int = 0, navigationBottom: Int = 0) {
        val density = rule.activity.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(status), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(px(navigationLeft), 0, 0, px(navigationBottom)))
            .build()
        val composeRoot = rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        ViewCompat.dispatchApplyWindowInsets(composeRoot, insets)
        rule.waitForIdle()
    }

    private fun dp(value: Int) = value * rule.activity.resources.displayMetrics.density

    private fun assertBarClearOfSystemBars() {
        val status = 32
        val navigation = 48
        injectSystemBars(status, navigationBottom = navigation)
        val root = rule.onRoot().fetchSemanticsNode().boundsInRoot
        val page = boundsOf(pageList)
        assertTrue("page below the status bar", page.top >= dp(status))
        tabBounds().forEach {
            assertTrue("tab $it above the navigation bar", it.bottom <= root.bottom - dp(navigation))
            assertTrue("tab $it clear of the page $page", it.top >= page.bottom)
        }
    }

    /** A phone in landscape has its three-button bar on the side, which is where it could collide with a rail. */
    private fun assertRailClearOfSystemBars() {
        val status = 32
        val navigation = 48
        injectSystemBars(status, navigationLeft = navigation)
        val page = boundsOf(pageList)
        assertTrue("page below the status bar", page.top >= dp(status))
        tabBounds().forEach {
            assertTrue("tab $it below the status bar", it.top >= dp(status))
            assertTrue("tab $it right of the navigation bar", it.left >= dp(navigation))
            assertTrue("tab $it clear of the page $page", it.right <= page.left)
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
    fun item003_compactWidthShowsSixLabelledTabsWithIconsAndTodaySelected() {
        awaitShell()
        assertEquals(listOf("Today", "Week", "Month", "Notes", "Search", "Settings"), labels.map { str(it) })
        labels.forEach { rule.onNode(tab(it)).assertExists() }
        assertBottomBar()
        labels.forEach { assertEquals("only Today is selected", it == R.string.tab_today, tabSelected(it)) }

        // An icon has no semantics, so read the pixels above each label: each tab must draw a shape of its own there.
        val density = rule.activity.resources.displayMetrics.density
        val bitmap = windowBitmap()
        val shapes = labels.map { label ->
            val tabBounds = boundsOf(tab(label))
            val text = rule.onNode(hasText(str(label)) and hasAnyAncestor(anyTab), useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val size = (24 * density).toInt()
            val left = (tabBounds.center.x - size / 2).toInt()
            val top = (text.top - size - 8 * density).toInt()
            val background = bitmap.getPixel(left, top)
            buildList {
                for (y in top until top + size) {
                    for (x in left until left + size) {
                        if (bitmap.getPixel(x, y) != background) add((x - left) to (y - top))
                    }
                }
            }
        }
        shapes.forEachIndexed { i, shape -> assertTrue("${str(labels[i])} draws an icon: ${shape.size} pixels", shape.size > 50) }
        assertEquals("six different icons", 6, shapes.toSet().size)
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h800dp-xxhdpi")
    fun item013_coverWidthInPortraitKeepsTheBottomBar() {
        awaitShell()
        assertBottomBar()
        assertBarClearOfSystemBars()
    }

    @Test
    @Config(qualifiers = "en-rUS-w599dp-h360dp-land-xxhdpi")
    fun item013_landscapeStillCompactKeepsTheBottomBar() {
        awaitShell()
        assertBottomBar()
        assertBarClearOfSystemBars()
    }

    @Test
    @Config(qualifiers = "en-rUS-w600dp-h412dp-land-xxhdpi")
    fun item013_landscapeAtMediumWidthSwitchesToTheRail() {
        awaitShell()
        assertRail()
        assertRailClearOfSystemBars()
    }

    @Test
    @Config(qualifiers = "en-rUS-w840dp-h600dp-land-xxhdpi")
    fun item013_expandedWidthHasTheRail() {
        awaitShell()
        assertRail()
        assertRailClearOfSystemBars()
    }

    /**
     * A phone turned sideways is about 412 dp tall, and six rail items need 360 dp of that. The Scaffold's padding already keeps
     * the rail clear of the system bars, so the rail must not add them a second time, which pushes its last tabs off the screen.
     */
    @Test
    @Config(qualifiers = "en-rUS-w800dp-h412dp-land-xxhdpi")
    fun item013_railAddsTheSystemBarsOnceAndEveryTabStaysReachableOnAPhoneInLandscape() {
        awaitShell()
        assertRail()
        injectSystemBars(status = 32, navigationBottom = 24)
        val firstTab = boundsOf(tab(R.string.tab_today))
        assertEquals("the rail's own 4 dp below the status bar", dp(32 + 4), firstTab.top, 1f)
        labels.forEach { label ->
            val height = boundsOf(tab(label)).height
            assertTrue("${str(label)} shows $height px of a tab, less than a 48 dp touch target", height >= dp(48))
        }
        rule.onNode(tab(R.string.tab_settings)).performClick()
        await { tabSelected(R.string.tab_settings) }
    }
}
