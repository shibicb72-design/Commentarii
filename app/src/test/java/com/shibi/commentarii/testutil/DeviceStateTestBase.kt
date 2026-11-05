package com.shibi.commentarii.testutil

import android.app.Dialog
import android.content.res.Configuration
import android.os.Looper
import android.view.ViewGroup
import androidx.activity.BackEventCompat
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.shibi.commentarii.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * The real MainActivity for the device-state items: a rotation or resize is a new configuration plus a recreate (the manifest
 * declares no configChanges, so a device recreates too), a nav mode is injected insets as in ImeGapTest, and screens are told
 * apart by what they show, because AppNav's back stack is not visible from MainActivity.
 */
abstract class DeviceStateTestBase : MainActivityTestBase() {
    protected val today: LocalDate get() = LocalDate.now(container.clock)
    protected val inDialog = hasAnyAncestor(isDialog())

    private val dayTitleFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())

    protected fun dayTitle(date: LocalDate): String = date.format(dayTitleFormat)

    /** Waits until a daily page has loaded: its priority rows only appear after that. */
    protected fun awaitDay(date: LocalDate) = await { exists(hasText(dayTitle(date))) && exists(hasText("1.")) }

    protected fun awaitToday() {
        await { tabSelected(R.string.tab_today) }
        awaitDay(today)
    }

    /** The biggest vertical scroller on screen, outside any dialog: the page itself, not a field or a card inside it. */
    protected fun pageScroller(): SemanticsMatcher {
        val vertical = SemanticsMatcher("scrolls vertically") { SemanticsProperties.VerticalScrollAxisRange in it.config }
        val page = rule.onAllNodes(vertical and !inDialog).fetchSemanticsNodes().maxBy { it.size.height }
        return SemanticsMatcher("the page's scroller") { it.id == page.id }
    }

    /** Swipes the page up with real touch input until it cannot scroll further, and returns how far along its axis it ended up. */
    protected fun scrollPageToEnd(): Float {
        val scroller = pageScroller()
        for (i in 0 until 200) {
            val range = rule.onNode(scroller).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            if (range.value() >= range.maxValue()) return range.value()
            rule.onNode(scroller).performTouchInput { swipeUp() }
            rule.mainClock.advanceTimeBy(1_000)
            rule.waitForIdle()
        }
        error("the page never reached its end")
    }

    /** Where the node really is, including any part clipped off by a list or hidden under a bar. */
    protected fun bottomOf(node: SemanticsNode) = node.positionInRoot.y + node.size.height

    protected fun tabNodes(): List<SemanticsNode> = TABS.map { rule.onNode(tab(it)).fetchSemanticsNode() }

    /** Bottom bar: every tab on one line along the bottom of the window. Rail: every tab in one column on the left. */
    protected fun isBottomBar(): Boolean {
        val tabs = tabNodes()
        return tabs.map { it.positionInRoot.y }.distinct().size == 1 && tabs.map { it.positionInRoot.x }.distinct().size == tabs.size
    }

    protected fun isRail(): Boolean {
        val tabs = tabNodes()
        return tabs.map { it.positionInRoot.x }.distinct().size == 1 && tabs.map { it.positionInRoot.y }.distinct().size == tabs.size
    }

    // ---- Configuration ----

    /** Turns the window, as far as Robolectric's display goes, then recreates like a device with no configChanges does. */
    protected fun rotate() {
        val landscape = rule.activity.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        RuntimeEnvironment.setQualifiers(if (landscape) "+land" else "+port")
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
        assertEquals(
            if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT,
            rule.activity.resources.configuration.orientation,
        )
    }

    /** A new window size with [qualifiers]: without configChanges in the manifest, a resize is a recreate. */
    protected fun resizeTo(qualifiers: String) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    // ---- Back ----

    /** The 3-button Back button: the dispatcher alone. */
    protected fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    /** The gesture as the dispatcher sees it: started, some progress, committed. What the OS does to recognise it needs a device. */
    protected fun swipeBack() {
        rule.runOnUiThread {
            val dispatcher = rule.activity.onBackPressedDispatcher
            dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 800f, 0f, BackEventCompat.EDGE_LEFT))
            dispatcher.dispatchOnBackProgressed(BackEventCompat(300f, 800f, 0.5f, BackEventCompat.EDGE_LEFT))
            dispatcher.onBackPressed()
        }
        rule.waitForIdle()
    }

    /** Back, delivered to the showing dialog's own window as the system would. */
    protected fun pressBackInDialog() {
        val dialog: Dialog = ShadowDialog.getLatestDialog()
        @Suppress("DEPRECATION")
        rule.runOnUiThread { dialog.onBackPressed() }
        rule.waitForIdle()
    }

    // ---- Insets ----

    /** Tells the app's Compose view about a navigation bar [navBarDp] tall, and a keyboard [imeDp] tall if there is one. */
    protected fun injectInsets(navBarDp: Int, imeDp: Int = 0, statusBarDp: Int = 32) {
        val density = rule.activity.resources.displayMetrics.density
        val builder = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (statusBarDp * density).roundToInt(), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, (navBarDp * density).roundToInt()))
        if (imeDp > 0) {
            builder.setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ((imeDp + navBarDp) * density).roundToInt()))
            builder.setVisible(WindowInsetsCompat.Type.ime(), true)
        }
        val composeRoot = rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        rule.runOnUiThread { ViewCompat.dispatchApplyWindowInsets(composeRoot, builder.build()) }
        rule.waitForIdle()
    }

    // ---- Moving around ----

    protected fun tapDescription(description: String) {
        rule.onNode(hasContentDescription(description)).performClick()
        rule.waitForIdle()
    }

    /** Runs the looper and compose for [millis] of real time, for checks that something does not arrive. */
    protected fun settleFor(millis: Long) {
        val end = System.currentTimeMillis() + millis
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idle()
            rule.waitForIdle()
            Thread.sleep(10)
        }
    }

    protected fun assertOnlyOne(what: String, matcher: SemanticsMatcher) = assertEquals(what, 1, count(matcher))

    protected fun field(text: String) = hasText(text) and hasSetTextAction() and !inDialog

    protected fun assertAbove(what: String, bottom: Float, limit: Float) =
        assertTrue("$what: bottom $bottom must be at or above $limit", bottom <= limit + 1f)

    protected fun card(text: String) = hasText(text) and hasClickAction()

    private companion object {
        val TABS = listOf(
            R.string.tab_today,
            R.string.tab_week,
            R.string.tab_month,
            R.string.tab_notes,
            R.string.tab_search,
            R.string.tab_settings,
        )
    }
}
