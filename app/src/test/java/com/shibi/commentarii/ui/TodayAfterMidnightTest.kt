package com.shibi.commentarii.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 294: the app stays alive overnight, so a tab tapped after midnight must go to the real current date. Drives the
 * real AppNav and reads where it landed from onRoutesChanged. Dates are far from the real date so the pages' own
 * "is this today" logic, which reads the container's system clock, stays out of the way.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class TodayAfterMidnightTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private class MovableClock(var date: LocalDate) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = date.atTime(0, 1).toInstant(ZoneOffset.UTC)
    }

    private var routes = emptyList<String>()

    private fun show(clock: Clock) {
        val container = (rule.activity.application as CommentariiApp).container
        rule.setContent {
            CommentariiTheme(dark = false) {
                AppNav(container, WindowWidthSizeClass.Compact, onRoutesChanged = { routes = it }, clock = clock)
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tab: Tab) {
        rule.onNode(
            hasText(rule.activity.getString(tab.label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab),
        ).performClick()
        rule.waitForIdle()
    }

    @Test
    fun todayTabGoesToTheNewDate() {
        val clock = MovableClock(LocalDate.parse("2031-03-12"))
        show(clock)
        assertEquals(listOf("daily/2031-03-12"), routes)
        clock.date = LocalDate.parse("2031-03-13")
        tap(Tab.TODAY)
        assertEquals(listOf("daily/2031-03-13"), routes)
    }

    @Test
    fun weekTabGoesToTheNewWeek() {
        val clock = MovableClock(LocalDate.parse("2031-03-16")) // a Sunday
        show(clock)
        clock.date = LocalDate.parse("2031-03-17")
        tap(Tab.WEEK)
        assertEquals("week/2031-03-17", routes.last())
    }

    @Test
    fun monthTabGoesToTheNewMonth() {
        val clock = MovableClock(LocalDate.parse("2031-03-31"))
        show(clock)
        clock.date = LocalDate.parse("2031-04-01")
        tap(Tab.MONTH)
        assertEquals("month/2031-04", routes.last())
    }

    /** The tab restores saved state, which must not bring back the week visited before midnight. */
    @Test
    fun weekTabVisitedBeforeMidnightGoesToTheNewWeek() {
        val clock = MovableClock(LocalDate.parse("2031-03-16"))
        show(clock)
        tap(Tab.WEEK)
        assertEquals("week/2031-03-10", routes.last())
        tap(Tab.NOTES)
        clock.date = LocalDate.parse("2031-03-17")
        tap(Tab.WEEK)
        assertEquals("week/2031-03-17", routes.last())
    }

    @Test
    fun monthTabVisitedBeforeMidnightGoesToTheNewMonth() {
        val clock = MovableClock(LocalDate.parse("2031-03-31"))
        show(clock)
        tap(Tab.MONTH)
        assertEquals("month/2031-03", routes.last())
        tap(Tab.NOTES)
        clock.date = LocalDate.parse("2031-04-01")
        tap(Tab.MONTH)
        assertEquals("month/2031-04", routes.last())
    }

    @Test
    fun weekTabTappedWhileOnTheOldWeekGoesToTheNewWeek() {
        val clock = MovableClock(LocalDate.parse("2031-03-16"))
        show(clock)
        tap(Tab.WEEK)
        clock.date = LocalDate.parse("2031-03-17")
        tap(Tab.WEEK)
        assertEquals("week/2031-03-17", routes.last())
    }

    @Test
    fun monthTabTappedWhileOnTheOldMonthGoesToTheNewMonth() {
        val clock = MovableClock(LocalDate.parse("2031-03-31"))
        show(clock)
        tap(Tab.MONTH)
        clock.date = LocalDate.parse("2031-04-01")
        tap(Tab.MONTH)
        assertEquals("month/2031-04", routes.last())
    }

    /** Leaving the old week for another tab first must not save it in a way the Week tab brings back. */
    @Test
    fun otherTabTappedFirstAfterMidnightStillLeadsToTheNewWeek() {
        val clock = MovableClock(LocalDate.parse("2031-03-16"))
        show(clock)
        tap(Tab.WEEK)
        clock.date = LocalDate.parse("2031-03-17")
        tap(Tab.NOTES)
        tap(Tab.WEEK)
        assertEquals("week/2031-03-17", routes.last())
    }

    /** Rotation restores the saved pages too, so what the tabs last showed has to survive it. */
    @Test
    fun recreationAfterMidnightStillLeadsToTheNewWeek() {
        val clock = MovableClock(LocalDate.parse("2031-03-16"))
        val container = (rule.activity.application as CommentariiApp).container
        val restoration = StateRestorationTester(rule)
        restoration.setContent {
            CommentariiTheme(dark = false) {
                AppNav(container, WindowWidthSizeClass.Compact, onRoutesChanged = { routes = it }, clock = clock)
            }
        }
        rule.waitForIdle()
        tap(Tab.WEEK)
        tap(Tab.NOTES)
        clock.date = LocalDate.parse("2031-03-17")
        restoration.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        tap(Tab.WEEK)
        assertEquals("week/2031-03-17", routes.last())
    }

    /** Existing behaviour: within one week the tab returns to the week the user was looking at, even on a later day. */
    @Test
    fun weekTabStillRestoresThePickedWeekWithinTheSameWeek() {
        val clock = MovableClock(LocalDate.parse("2031-03-12"))
        show(clock)
        tap(Tab.WEEK)
        rule.onNode(hasContentDescription(rule.activity.getString(R.string.week_next))).performClick()
        rule.waitForIdle()
        assertEquals("week/2031-03-17", routes.last())
        tap(Tab.NOTES)
        clock.date = LocalDate.parse("2031-03-13")
        tap(Tab.WEEK)
        assertEquals("week/2031-03-17", routes.last())
    }

    /** The graph's defaultValue must stay fixed: rebuilding the graph after midnight could clear the back stack. */
    @Test
    fun midnightDoesNotDisturbTheBackStack() {
        val clock = MovableClock(LocalDate.parse("2031-03-12"))
        show(clock)
        tap(Tab.NOTES)
        assertEquals(listOf("daily/2031-03-12", Routes.NOTES), routes)
        clock.date = LocalDate.parse("2031-03-13")
        tap(Tab.SEARCH)
        assertEquals(listOf("daily/2031-03-12", Routes.SEARCH), routes)
    }
}
