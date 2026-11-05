package com.shibi.commentarii.ui

import android.os.Looper
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.DeviceStateTestBase
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Checklist #289, and the "never under the 3-button bar" clause of #288: a navigation bar injected at the height of each nav mode
 * into the real MainActivity, each list swiped to its end, real bounds measured. The bounds are unclipped, so an item running
 * under the bar would show as a bottom below the bar's top.
 */
class DeviceNavBarInsetsFlowTest : DeviceStateTestBase() {
    private enum class NavMode(val dp: Int) { GESTURE(24), THREE_BUTTON(48) }

    private val density get() = rule.activity.resources.displayMetrics.density
    private val windowBottom get() = rule.activity.window.decorView.height.toFloat()

    private fun seedTrips() = runBlocking { repeat(30) { container.pages.createNote("Trip $it") } }

    private val tripCard = SemanticsMatcher("a card for a note") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.startsWith("Trip ") } && hasClickActionOn(node)
    }

    private fun hasClickActionOn(node: SemanticsNode) = hasClickAction().matches(node)

    private fun lowestTrip() = rule.onAllNodes(tripCard).fetchSemanticsNodes().maxBy { it.positionInRoot.y }

    /**
     * The bar is where the bars say it is: every tab ends exactly the nav bar's height above the window's bottom, which also proves
     * the injected inset really reached the layout. Returns the line nothing may cross.
     */
    private fun assertBottomBarAboveTheNavBar(mode: NavMode): Float {
        assertTrue("a bottom bar", isBottomBar())
        val navPx = mode.dp * density
        val tabs = tabNodes()
        assertEquals("the tabs end at the nav bar's top", windowBottom - navPx, tabs.maxOf(::bottomOf), 2f)
        return tabs.minOf { it.positionInRoot.y }
    }

    private fun notes(mode: NavMode) {
        seedTrips()
        awaitToday()
        openTab(R.string.tab_notes)
        await { exists(tripCard) }
        injectInsets(mode.dp)
        val barTop = assertBottomBarAboveTheNavBar(mode)

        assertTrue("the list really had to scroll", scrollPageToEnd() > 0f)
        val fab = rule.onNode(hasContentDescription(str(R.string.nt_new))).fetchSemanticsNode()
        val last = lowestTrip()

        assertAbove("the last note", bottomOf(last), barTop)
        assertAbove("the + button", bottomOf(fab), barTop)
        assertAbove("the last note, clear of the + button", bottomOf(last), fab.positionInRoot.y)
        assertEquals("the + button is on screen in full, at its 56dp", 56 * density, fab.boundsInRoot.height, 2f)
    }

    private fun search(mode: NavMode) {
        seedTrips()
        awaitToday()
        openTab(R.string.tab_search)
        await { exists(hasSetTextAction()) }
        rule.onNode(hasSetTextAction()).performTextInput("Trip")
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            exists(tripCard)
        }
        rule.waitForIdle()
        injectInsets(mode.dp)
        val barTop = assertBottomBarAboveTheNavBar(mode)

        assertTrue("the results really had to scroll", scrollPageToEnd() > 0f)

        assertAbove("the last result", bottomOf(lowestTrip()), barTop)
        assertTrue("the last result is on screen in full", lowestTrip().boundsInRoot.height >= lowestTrip().size.height - 1)
    }

    private fun settings(mode: NavMode) {
        awaitToday()
        openTab(R.string.tab_settings)
        await { exists(str(R.string.settings_section_backup)) }
        injectInsets(mode.dp)
        val barTop = assertBottomBarAboveTheNavBar(mode)

        assertTrue("the page really had to scroll", scrollPageToEnd() > 0f)
        val last = rule.onNode(hasText(str(R.string.settings_licence_show))).fetchSemanticsNode()

        assertAbove("the last thing on the page", bottomOf(last), barTop)
    }

    private fun dailyFooter(mode: NavMode) {
        awaitToday()
        injectInsets(mode.dp)
        val barTop = assertBottomBarAboveTheNavBar(mode)

        assertTrue("the page really had to scroll", scrollPageToEnd() > 0f)
        val footer = rule.onNode(hasText(str(R.string.daily_footer))).fetchSemanticsNode()

        assertAbove("the footer", bottomOf(footer), barTop)
    }

    @Test fun item289_gestureNav_notesLastItemAndFabAboveTheBar() = notes(NavMode.GESTURE)

    @Test fun item288_item289_threeButtonNav_notesLastItemAndFabAboveTheBar() = notes(NavMode.THREE_BUTTON)

    @Test fun item289_gestureNav_lastSearchResultAboveTheBar() = search(NavMode.GESTURE)

    @Test fun item288_item289_threeButtonNav_lastSearchResultAboveTheBar() = search(NavMode.THREE_BUTTON)

    @Test fun item289_gestureNav_settingsEndAboveTheBar() = settings(NavMode.GESTURE)

    @Test fun item288_item289_threeButtonNav_settingsEndAboveTheBar() = settings(NavMode.THREE_BUTTON)

    @Test fun item289_gestureNav_dailyFooterAboveTheBar() = dailyFooter(NavMode.GESTURE)

    @Test fun item288_item289_threeButtonNav_dailyFooterAboveTheBar() = dailyFooter(NavMode.THREE_BUTTON)

    // ---- The rail: no bottom bar, so the nav bar itself is the limit ----

    private fun railNotes(mode: NavMode) {
        seedTrips()
        awaitToday()
        openTab(R.string.tab_notes)
        await { exists(tripCard) }
        injectInsets(mode.dp)
        assertTrue("a rail", isRail())
        val navTop = windowBottom - mode.dp * density

        assertTrue("every tab above the nav bar", tabNodes().maxOf(::bottomOf) <= navTop + 1f)
        assertTrue("the list really had to scroll", scrollPageToEnd() > 0f)
        assertAbove("the last note", bottomOf(lowestTrip()), navTop)
        assertAbove("the + button", bottomOf(rule.onNode(hasContentDescription(str(R.string.nt_new))).fetchSemanticsNode()), navTop)
    }

    @Config(qualifiers = "en-rUS-w673dp-h747dp-xxhdpi")
    @Test
    fun item289_gestureNav_onTheRailNotesLastItemAndFabAboveTheNavBar() = railNotes(NavMode.GESTURE)

    @Config(qualifiers = "en-rUS-w673dp-h747dp-xxhdpi")
    @Test
    fun item288_item289_threeButtonNav_onTheRailNotesLastItemAndFabAboveTheNavBar() = railNotes(NavMode.THREE_BUTTON)
}
