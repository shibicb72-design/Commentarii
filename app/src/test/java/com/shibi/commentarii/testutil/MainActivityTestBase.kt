package com.shibi.commentarii.testutil

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.MainActivity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Hosts the real MainActivity on the real container, for items that need what only it has: the window size class, the lock
 * wiring, the re-checks in onResume and the notification permission request. Settings leak across tests, so they are reset on
 * both sides, off the main thread because the activity's collectors may have an edit in flight.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
abstract class MainActivityTestBase {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    // Not through rule.activity, which only works on the main thread: runWithLooper blocks run off it.
    protected val container = ApplicationProvider.getApplicationContext<CommentariiApp>().container

    @Before
    fun resetState() {
        runWithLooper { container.settings.resetToDefaults() }
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun restoreState() {
        runWithLooper { container.settings.resetToDefaults() }
    }

    protected fun str(res: Int, vararg args: Any): String = rule.activity.getString(res, *args)

    protected fun exists(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()

    protected fun exists(text: String) = exists(hasText(text))

    protected fun count(matcher: SemanticsMatcher) = rule.onAllNodes(matcher).fetchSemanticsNodes().size

    protected fun await(condition: () -> Boolean) {
        rule.waitUntilWithLooper(condition = condition)
        rule.waitForIdle()
    }

    protected fun tab(label: Int) =
        hasText(str(label)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)

    protected fun tabSelected(label: Int) = exists(tab(label) and isSelected())

    /** Waits for the settings to load and the app shell to appear, then taps [label]. */
    protected fun openTab(label: Int) {
        await { exists(tab(label)) }
        rule.onNode(tab(label)).performClick()
        await { tabSelected(label) }
    }

    /** The activity leaving the screen and coming back, e.g. a visit to a system settings page. */
    protected fun leaveAndReturn() {
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
    }

    protected fun tapText(text: String) {
        rule.onNode(hasText(text)).performClick()
        rule.waitForIdle()
    }

    protected fun boundsOf(matcher: SemanticsMatcher): Rect = rule.onNode(matcher).fetchSemanticsNode().boundsInRoot

    /** A software draw of the activity's window. */
    protected fun windowBitmap(): Bitmap {
        val view = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { view.draw(Canvas(bitmap)) }
        return bitmap
    }
}
