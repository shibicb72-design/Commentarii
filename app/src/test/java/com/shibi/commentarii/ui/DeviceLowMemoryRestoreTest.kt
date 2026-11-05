package com.shibi.commentarii.ui

import android.os.Bundle
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.MainActivity
import com.shibi.commentarii.R
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.domain.weekStart
import com.shibi.commentarii.testutil.resetToDefaults
import com.shibi.commentarii.testutil.runWithLooper
import com.shibi.commentarii.testutil.waitUntilWithLooper
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checklist #292, "Don't keep activities": the activity's state goes into a bundle and the activity is destroyed for good with its
 * view models; a new instance starts from that bundle alone. Only what was saved can bring the screen back, unlike a recreate.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class DeviceLowMemoryRestoreTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<CommentariiApp>()
    private val container = app.container
    private var live: ActivityController<MainActivity>? = null

    @Before
    fun setUp() {
        runWithLooper { container.settings.resetToDefaults() }
    }

    @After
    fun tearDown() {
        live?.pause()?.stop()?.destroy()
        runWithLooper { container.settings.resetToDefaults() }
    }

    private fun str(res: Int) = app.getString(res)

    private fun exists(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    private fun await(condition: () -> Boolean) {
        rule.waitUntilWithLooper(condition = condition)
        rule.waitForIdle()
    }

    private fun tab(label: Int) = hasText(str(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    private fun field(text: String) = hasText(text) and hasSetTextAction()

    private fun savedBlocks(id: Long) = runBlocking { container.pages.getBlockDocument(id, SectionKind.RICH_TEXT) }.blocks

    private fun openNote(title: String) {
        await { exists(tab(R.string.tab_notes)) }
        rule.onNode(tab(R.string.tab_notes)).performClick()
        rule.waitForIdle()
        await { exists(hasText(title) and hasClickAction()) }
        rule.onNode(hasText(title) and hasClickAction()).performClick()
        rule.waitForIdle()
    }

    /** Home with the option on: state saved, then stopped and destroyed; then back: a new instance from that state. */
    private fun homeAndReturn() {
        val bundle = Bundle()
        checkNotNull(live).saveInstanceState(bundle).pause().stop().destroy()
        live = Robolectric.buildActivity(MainActivity::class.java).create(bundle).start().restoreInstanceState(bundle).resume().visible()
        rule.waitForIdle()
    }

    @Test
    fun item292_aDestroyedActivityComesBackOnTheNoteWithItsContentAndBackStack() {
        val id = runBlocking {
            val page = container.pages.createNote("Restore me")
            container.pages.saveBlockDocument(page.id, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(text = "Saved body"))))
            page.id
        }
        live = Robolectric.buildActivity(MainActivity::class.java).setup()
        val first = checkNotNull(live).get()
        openNote("Restore me")
        await { exists(field("Saved body")) }
        rule.onNode(field("Saved body")).performTextInputSelection(TextRange(0))
        rule.onNode(field("Saved body")).performTextInput("Typed ")
        rule.waitForIdle()

        homeAndReturn()

        val second = checkNotNull(live).get()
        assertTrue("really a new instance", first !== second)
        assertTrue("and the old one is gone", first.isDestroyed)
        await { exists(field("Typed Saved body")) && exists(field("Restore me")) }
        assertTrue("the same screen", exists(hasContentDescription(str(R.string.ed_organize))))
        assertTrue("in the Notes tab", exists(tab(R.string.tab_notes) and isSelected()))
        await { savedBlocks(id).map { it.text } == listOf("Typed Saved body") }

        // The stack came back too, once.
        second.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        await { exists(hasContentDescription(str(R.string.nt_new))) && exists(hasText("Restore me")) }
        second.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        await { exists(tab(R.string.tab_today) and isSelected()) }
        assertFalse("not left yet", second.isFinishing)
        second.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        assertTrue("Today leaves the app", second.isFinishing)
        assertEquals("the note kept its title", "Restore me", runBlocking { container.pages.getPage(id) }?.title)
    }

    /** A stack that is deeper than the note's: the day opened from a week goes back to the week, then Today, then out. */
    @Test
    fun item292_aDayOpenedFromTheWeekComesBackWithOneOfEachScreenUnderIt() {
        live = Robolectric.buildActivity(MainActivity::class.java).setup()
        await { exists(tab(R.string.tab_week)) }
        rule.onNode(tab(R.string.tab_week)).performClick()
        rule.waitForIdle()
        val fri = hasText("Fri") and hasClickAction()
        await { exists(fri) }
        rule.onNode(fri).performClick()
        rule.waitForIdle()
        val friday = weekStart(LocalDate.now(container.clock)).plusDays(4)
        val fridayTitle = hasText(friday.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())))
        await { exists(fridayTitle) && exists(hasText("1.")) }

        homeAndReturn()

        val activity = checkNotNull(live).get()
        await { exists(fridayTitle) && exists(hasText("1.")) }
        assertTrue("a day belongs to Today", exists(tab(R.string.tab_today) and isSelected()))

        activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        await { exists(tab(R.string.tab_week) and isSelected()) && exists(fri) }
        activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        await { exists(tab(R.string.tab_today) and isSelected()) }
        assertFalse("not left yet", activity.isFinishing)
        activity.onBackPressedDispatcher.onBackPressed()
        rule.waitForIdle()
        assertTrue("nothing stacked under Today", activity.isFinishing)
    }

    /** Positive control: without the saved state the new instance starts on Today, so the test above did prove something. */
    @Test
    fun item292_aNewInstanceWithoutTheBundleStartsOnToday() {
        runBlocking { container.pages.createNote("Restore me") }
        live = Robolectric.buildActivity(MainActivity::class.java).setup()
        openNote("Restore me")
        await { exists(field("Restore me")) }

        checkNotNull(live).pause().stop().destroy()
        live = Robolectric.buildActivity(MainActivity::class.java).setup()
        rule.waitForIdle()

        await { exists(tab(R.string.tab_today) and isSelected()) }
        assertFalse("not on the note", exists(field("Restore me")))
    }
}
