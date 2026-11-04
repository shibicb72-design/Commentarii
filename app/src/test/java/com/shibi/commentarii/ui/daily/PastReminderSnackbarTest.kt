package com.shibi.commentarii.ui.daily

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.resetToDefaults
import com.shibi.commentarii.testutil.waitUntilWithLooper
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checklist #114: typing on a past row of the real DailyPageScreen. Yesterday's page is used because every row on it is past
 * whatever the system clock says. Autosave is the real one, so the save is awaited through the page's updatedAt.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class PastReminderSnackbarTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val container get() = (rule.activity.application as CommentariiApp).container
    private val past get() = rule.activity.getString(R.string.outcome_past)
    private lateinit var date: LocalDate

    @Before
    fun setUp() {
        container.settings.resetToDefaults()
        date = LocalDate.now(container.clock).minusDays(1)
        rule.setContent {
            CommentariiTheme(dark = false) { DailyPageScreen(container, date, {}, WindowWidthSizeClass.Compact) }
        }
        val hint = rule.activity.getString(R.string.daily_schedule_hint)
        rule.waitUntilWithLooper { runCatching { rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(hint)) }.isSuccess }
    }

    @After
    fun tearDown() {
        container.settings.resetToDefaults()
    }

    private fun updatedAt() = runBlocking { container.pages.getOrCreateDaily(date).updatedAt }

    private fun row(text: String): SemanticsNodeInteraction {
        rule.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        return rule.onAllNodes(hasText(text) and hasSetTextAction()).onFirst()
    }

    private fun firstRowText() = runBlocking {
        container.pages.getSchedule(container.pages.getOrCreateDaily(date).id).first { it.minuteOfDay == 6 * 60 }.text
    }

    /** Clears the first row and types [text]. */
    private fun retype(from: String, text: String) {
        row(from).performTextClearance()
        row(rule.activity.getString(R.string.daily_schedule_hint)).performTextInput(text)
    }

    /**
     * Runs one edit and waits for autosave to finish: the row holds [text], the page's updatedAt moved, and it then stays put
     * for longer than the 500 ms debounce. Clear-then-type can save twice, and the first save must not end the wait.
     */
    private fun savedAfter(text: String, edit: () -> Unit) {
        val before = updatedAt()
        edit()
        var last = before
        var stableSince = System.currentTimeMillis()
        rule.waitUntilWithLooper {
            val now = updatedAt()
            if (now != last) {
                last = now
                stableSince = System.currentTimeMillis()
            }
            last != before && firstRowText() == text && System.currentTimeMillis() - stableSince > 700
        }
    }

    private fun snackbarCount() = rule.onAllNodes(hasText(past)).fetchSemanticsNodes().size

    private fun awaitSnackbar() = rule.waitUntilWithLooper { snackbarCount() == 1 }

    private fun letSnackbarExpire() {
        rule.mainClock.advanceTimeBy(6_000)
        rule.waitUntilWithLooper { snackbarCount() == 0 }
    }

    @Test
    fun item114_snackbarOncePerEntryAndText() {
        savedAfter("late") { row(rule.activity.getString(R.string.daily_schedule_hint)).performTextInput("late") }
        awaitSnackbar()
        letSnackbarExpire()

        savedAfter("late") { retype("late", "late") }
        rule.waitForIdle()
        row("late").assertExists()
        assertEquals("retyping the same text shows nothing new", 0, snackbarCount())

        savedAfter("later") { retype("late", "later") }
        awaitSnackbar()
    }
}
