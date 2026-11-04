package com.shibi.commentarii.ui.notes

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.ui.theme.CommentariiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Items 183/184/191: backspace at the start of a block merges it into the previous one, but only key events reach
 * that code (NoteEditorScreen BlockRow onPreviewKeyEvent). Soft keyboards may not send a key event there.
 * The hardware path is checked here. The soft keyboard path needs a real keyboard on a device: a JVM test has no IME,
 * so it cannot show what Gboard or Samsung Keyboard send. It stays ignored until an instrumented test exists.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class BackspaceAtBlockStartTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(
        NoteEditorUiState(
            loaded = true,
            blocks = listOf(
                EditorBlock(1, DocBlock(BlockType.PARAGRAPH, "Hello")),
                EditorBlock(2, DocBlock(BlockType.PARAGRAPH, "World")),
            ),
            focusRequest = FocusRequest(2, 0),
        ),
    )

    private fun show() {
        val actions = NoteEditorActions(
            onFocusHandled = { state = state.copy(focusRequest = null) },
            onBackspaceAtStart = { key ->
                val i = state.blocks.indexOfFirst { it.key == key }
                val merged = mergeBlocks(state.blocks[i - 1].block, state.blocks[i].block)
                state = state.copy(blocks = listOf(EditorBlock(state.blocks[i - 1].key, merged)))
            },
        )
        rule.setContent { CommentariiTheme(dark = false) { NoteEditorContent(state, actions) } }
        rule.waitForIdle()
    }

    private fun assertMerged() {
        assertEquals(listOf("HelloWorld"), state.blocks.map { it.block.text })
    }

    /** The focused Compose view's real InputConnection: the object a keyboard talks to. */
    private fun withInputConnection(block: (InputConnection) -> Unit) {
        val composeView = rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as ViewGroup
        val owner: View = composeView.getChildAt(0)
        rule.runOnIdle {
            val connection = owner.onCreateInputConnection(EditorInfo())
            assertNotNull("no input connection: the block field has no text input session", connection)
            block(connection!!)
        }
        rule.waitForIdle()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun hardwareBackspaceAtStartMerges() {
        show()
        rule.onNode(hasText("World") and hasSetTextAction()).performKeyInput { pressKey(Key.Backspace) }
        rule.waitForIdle()
        assertMerged()
    }

    @Ignore(
        "items 183/184/191: needs real keyboard (instrumented); on the JVM deleteSurroundingText(1, 0) at cursor 0 " +
            "does not merge, but no real IME runs here, so whether a keyboard sends this call is unknown",
    )
    @Test
    fun softKeyboardDeleteSurroundingTextAtStartMerges() {
        show()
        withInputConnection { it.deleteSurroundingText(1, 0) }
        assertMerged()
    }

    @Ignore(
        "items 183/184/191: needs real keyboard (instrumented); a DEL key event through the InputConnection merges " +
            "on the JVM, but no real IME runs here, so a pass would be a false positive for soft keyboards",
    )
    @Test
    fun softKeyboardDelKeyEventAtStartMerges() {
        show()
        withInputConnection {
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            it.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
        assertMerged()
    }
}
