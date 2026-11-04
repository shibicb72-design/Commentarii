package com.shibi.commentarii.ui.search

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Checklist #228, #231: what the real SearchScreen shows for a query typed into it. SearchQueryTest covers the matching. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class SearchScreenFlowTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val container get() = (rule.activity.application as CommentariiApp).container
    private val noResults get() = rule.activity.getString(R.string.sr_no_results)
    private val pinned get() = rule.activity.getString(R.string.nt_pinned)

    @Before
    fun setUp() {
        rule.setContent { CommentariiTheme(dark = false) { SearchScreen(container, { _, _, _ -> }) } }
        rule.waitForIdle()
    }

    /** Types into the field, then runs the main looper past the view model's debounce delay, which waitUntil alone never does. */
    private fun search(query: String) {
        val field = rule.onNode(hasSetTextAction())
        field.performTextClearance()
        field.performTextInput(query)
    }

    private fun awaitNodes(text: String) {
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            rule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun top(text: String) = rule.onNode(hasText(text)).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun item228_pinnedResultComesFirstAndAloneShowsThePinIcon() {
        val older = runBlocking { container.pages.createNote("alpha older") }
        Thread.sleep(5)
        runBlocking { container.pages.createNote("alpha newer") }

        search("alpha")
        awaitNodes("alpha newer")
        assertTrue("most recently updated first", top("alpha newer") < top("alpha older"))
        assertEquals(0, rule.onAllNodes(hasContentDescription(pinned)).fetchSemanticsNodes().size)

        runBlocking { container.pages.setPinned(older.id, true) }
        search("alph")
        awaitNodes("alpha newer")
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            rule.onAllNodes(hasContentDescription(pinned)).fetchSemanticsNodes().isNotEmpty()
        }

        assertTrue("pinned first", top("alpha older") < top("alpha newer"))
        rule.onNode(hasText("alpha older") and hasContentDescription(pinned)).assertExists()
        assertEquals(1, rule.onAllNodes(hasContentDescription(pinned)).fetchSemanticsNodes().size)
    }

    @Test
    fun item231_punctuationOrEmojiOnlyShowsNothingFound() {
        runBlocking { container.pages.createNote("alpha party") }
        search("alpha")
        awaitNodes("alpha party")
        assertEquals("control: a word finds the note", 0, rule.onAllNodes(hasText(noResults)).fetchSemanticsNodes().size)

        search("!!!")
        awaitNodes(noResults)
        search("alpha")
        awaitNodes("alpha party")

        search("🎉")
        awaitNodes(noResults)
        assertEquals(0, rule.onAllNodes(hasText("alpha party")).fetchSemanticsNodes().size)
    }
}
