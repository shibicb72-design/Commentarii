package com.shibi.commentarii.ui.notes

import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.testutil.NotesTestBase
import com.shibi.commentarii.ui.AppNav
import com.shibi.commentarii.ui.Routes
import com.shibi.commentarii.ui.theme.CommentariiTheme
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Checklist #201, #202: where the format toolbar sits with the keyboard open and closed, on the real AppNav and NoteEditorScreen.
 * The keyboard is an injected IME inset, as in ImeGapTest, measured from the bottom of the screen so it covers the nav bar too.
 * What a real keyboard reports and what its Back press does are left for a device.
 */
class NoteEditorInsetsFlowTest : NotesTestBase() {
    private enum class NavMode(val navBarDp: Int) { GESTURE(24), THREE_BUTTON(48) }

    private val keyboardDp = 280
    private val toolbar get() = rule.onNodeWithTag(FORMAT_TOOLBAR_TAG)
    private val rootBottom get() = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom

    private fun openEditor(width: WindowWidthSizeClass): Long {
        val id = seedNote("Note", DocBlock(text = "text"))
        rule.activity.enableEdgeToEdge()
        rule.setContent {
            CommentariiTheme(dark = false) {
                AppNav(container, width, restoreRoutes = listOf(Routes.note(id)), onRoutesChanged = { routes = it })
            }
        }
        rule.waitForIdle()
        await { routes.lastOrNull() == Routes.note(id) }
        awaitEditor()
        return id
    }

    private fun px(value: Int) = (value * rule.activity.resources.displayMetrics.density).roundToInt()

    /** Sends the insets to the Compose view; returns the IME inset in pixels. */
    private fun inject(nav: NavMode, keyboardOpen: Boolean): Int {
        val imePx = if (keyboardOpen) px(keyboardDp + nav.navBarDp) else 0
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(32), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px(nav.navBarDp)))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imePx))
            .setVisible(WindowInsetsCompat.Type.ime(), keyboardOpen)
            .build()
        val composeRoot = rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        ViewCompat.dispatchApplyWindowInsets(composeRoot, insets)
        rule.waitForIdle()
        return imePx
    }

    private fun tabBounds(label: Int): Rect = bounds(rule.onNode(tab(label)))

    /** The tabs really are a rail here: one column on the left, not a bar along the bottom. */
    private fun assertRail() {
        val tabs = listOf(R.string.tab_today, R.string.tab_notes, R.string.tab_settings).map(::tabBounds)
        assertEquals("one column", 1, tabs.map { it.left }.distinct().size)
        assertEquals("separate rows", 3, tabs.map { it.top }.distinct().size)
        assertTrue("on the left", tabs[0].right < bounds(rule.onRoot()).width * 0.3f)
        assertTrue("the editor is beside it", bounds(toolbar).left >= tabs[0].right)
    }

    private fun assertFlushOnKeyboard(imePx: Int) {
        assertEquals("toolbar bottom must equal the keyboard's top", rootBottom - imePx, bounds(toolbar).bottom, 1f)
    }

    private fun assertRailToolbarPlacement(nav: NavMode) {
        openEditor(WindowWidthSizeClass.Expanded)
        assertRail()
        assertFlushOnKeyboard(inject(nav, keyboardOpen = true))
        assertRail()

        inject(nav, keyboardOpen = false)
        assertEquals("closed: just above the nav bar", rootBottom - px(nav.navBarDp), bounds(toolbar).bottom, 1f)
    }

    @Test
    @Config(qualifiers = "en-rUS-w840dp-h900dp-xxhdpi")
    fun item201_item202_railToolbarIsFlushOnTheKeyboardAndDropsToTheNavBarGesture() = assertRailToolbarPlacement(NavMode.GESTURE)

    @Test
    @Config(qualifiers = "en-rUS-w840dp-h900dp-xxhdpi")
    fun item201_item202_railToolbarIsFlushOnTheKeyboardAndDropsToTheNavBarThreeButton() = assertRailToolbarPlacement(NavMode.THREE_BUTTON)

    private fun assertBottomBarToolbarPlacement(nav: NavMode) {
        openEditor(WindowWidthSizeClass.Compact)
        assertFlushOnKeyboard(inject(nav, keyboardOpen = true))

        inject(nav, keyboardOpen = false)
        val barTop = tabBounds(R.string.tab_notes).top
        assertEquals("closed: just above the bottom bar", barTop, bounds(toolbar).bottom, 1f)
        assertTrue("the bar itself clears the nav bar", tabBounds(R.string.tab_notes).bottom <= rootBottom - px(nav.navBarDp) + 1f)
    }

    @Test
    fun item202_toolbarDropsToTheBottomBarWhenTheKeyboardClosesGesture() = assertBottomBarToolbarPlacement(NavMode.GESTURE)

    @Test
    fun item202_toolbarDropsToTheBottomBarWhenTheKeyboardClosesThreeButton() = assertBottomBarToolbarPlacement(NavMode.THREE_BUTTON)

    /** With no keyboard to close, one Back leaves the editor; the first Back closing a real keyboard needs a device. */
    @Test
    fun item202_backWithTheKeyboardClosedLeavesTheEditor() {
        val id = openEditor(WindowWidthSizeClass.Compact)
        inject(NavMode.GESTURE, keyboardOpen = false)
        assertTrue("on the editor", exists(hasContentDescription(pinNote)))
        assertEquals(Routes.note(id), routes.last())

        pressBackInApp()

        await { routes.lastOrNull() != Routes.note(id) }
        assertFalse("left it", exists(hasText(str(R.string.ed_title_hint))) || exists(hasContentDescription(pinNote)))
    }
}
