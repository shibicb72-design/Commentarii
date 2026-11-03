package com.shibi.commentarii.ui

import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.commentarii.ui.notes.FORMAT_TOOLBAR_TAG
import com.shibi.commentarii.ui.notes.NoteEditorActions
import com.shibi.commentarii.ui.notes.NoteEditorContent
import com.shibi.commentarii.ui.notes.sampleNoteEditorUiState
import com.shibi.commentarii.ui.theme.CommentariiTheme
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Galaxy S25 Ultra keyboard gap: the note editor's format toolbar must sit flush on the IME.
 * Hosts the AppNav shell (Scaffold + AppBottomBar + AppScaffoldBody) around the real editor,
 * injects IME and navigation-bar insets into the Compose view and measures the toolbar.
 * Screenshots go to app/screenshots/ime-gap/ when run with recordRoborazziDebug.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class ImeGapTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private enum class NavMode(val navBarDp: Int, val label: String) {
        GESTURE(24, "gesture"),
        THREE_BUTTON(48, "3button"),
    }

    private val statusBarDp = 32
    // The IME inset is measured from the screen bottom, so it covers the nav bar area too.
    private val keyboardDp = 280

    private var seenIme = -1
    private var seenNav = -1
    private var scaffoldBottomPx = -1f

    private class Measured(val toolbarBottom: Float, val imeTop: Float, val scaffoldBottom: Float)

    private fun show(nav: NavMode, consumeInsets: Boolean): Measured {
        val activity = rule.activity
        activity.enableEdgeToEdge()
        rule.setContent {
            CommentariiTheme(dark = false) {
                val density = LocalDensity.current
                seenIme = WindowInsets.ime.getBottom(density)
                seenNav = WindowInsets.navigationBars.getBottom(density)
                Box(Modifier.fillMaxSize()) {
                    Scaffold(bottomBar = { AppBottomBar(Tab.NOTES) {} }) { padding ->
                        scaffoldBottomPx = with(density) { padding.calculateBottomPadding().toPx() }
                        AppScaffoldBody(padding, consumeInsets) {
                            NoteEditorContent(sampleNoteEditorUiState(), NoteEditorActions(), Modifier.weight(1f))
                        }
                    }
                    // Test-only stand-in for the keyboard so the screenshots show where the IME is.
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.ime)
                            .background(Color(0xFF3A3A3A)),
                        contentAlignment = Alignment.Center,
                    ) { Text("keyboard (IME inset)", color = Color.White) }
                }
            }
        }
        rule.waitForIdle()

        val density = activity.resources.displayMetrics.density
        val navPx = (nav.navBarDp * density).roundToInt()
        val imePx = ((keyboardDp + nav.navBarDp) * density).roundToInt()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, (statusBarDp * density).roundToInt(), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, navPx))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imePx))
            .setVisible(WindowInsetsCompat.Type.ime(), true)
            .build()
        val composeRoot = activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        ViewCompat.dispatchApplyWindowInsets(composeRoot, insets)
        rule.waitForIdle()

        assertEquals("WindowInsets.ime must report the injected IME bottom", imePx, seenIme)
        assertEquals("WindowInsets.navigationBars must report the injected nav bar", navPx, seenNav)

        val rootHeight = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val toolbarBottom = rule.onNodeWithTag(FORMAT_TOOLBAR_TAG).fetchSemanticsNode().boundsInRoot.bottom
        return Measured(toolbarBottom, rootHeight - imePx, scaffoldBottomPx)
    }

    private fun toolbarSitsOnKeyboard(nav: NavMode) {
        val m = show(nav, consumeInsets = true)
        assertEquals("toolbar bottom must equal IME top (${nav.label} nav)", m.imeTop, m.toolbarBottom, 1f)
        rule.onRoot().captureRoboImage("screenshots/ime-gap/after_${nav.label}.png")
    }

    private fun oldLayoutLeavesGap(nav: NavMode) {
        val m = show(nav, consumeInsets = false)
        assertTrue("Scaffold bottom padding must be non-zero", m.scaffoldBottom > 0f)
        // Root cause: the gap is exactly the unconsumed Scaffold bottom padding (bottom bar + nav bar).
        assertEquals("old gap equals Scaffold bottom padding", m.scaffoldBottom, m.imeTop - m.toolbarBottom, 1f)
        rule.onRoot().captureRoboImage("screenshots/ime-gap/before_${nav.label}.png")
    }

    @Test fun toolbarSitsOnKeyboardGestureNav() = toolbarSitsOnKeyboard(NavMode.GESTURE)
    @Test fun toolbarSitsOnKeyboardThreeButtonNav() = toolbarSitsOnKeyboard(NavMode.THREE_BUTTON)
    @Test fun oldLayoutLeavesGapGestureNav() = oldLayoutLeavesGap(NavMode.GESTURE)
    @Test fun oldLayoutLeavesGapThreeButtonNav() = oldLayoutLeavesGap(NavMode.THREE_BUTTON)
}
