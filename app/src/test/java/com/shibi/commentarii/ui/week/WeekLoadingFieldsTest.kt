package com.shibi.commentarii.ui.week

import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performScrollToNode
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.daily.TaskUi
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checklist #151, layout half: the week page's fields are read-only until `loaded`, and editable after. The stateless content
 * is hosted because a real load finishes too fast to catch. LoadGateTest covers the view model refusing early edits.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class WeekLoadingFieldsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val monday = LocalDate.of(2031, 3, 10)

    private fun show(loaded: Boolean) {
        val state = WeekUiState(
            monday = monday,
            today = monday,
            goals = listOf(TaskUi(key = 1, text = "goal")),
            todo = listOf(TaskUi(key = 2, text = "todo")),
            dontForget = listOf(TaskUi(key = 3, text = "keep")),
            loaded = loaded,
        )
        rule.setContent {
            CommentariiTheme(dark = false) { WeekPageContent(state, WeekActions(), WindowWidthSizeClass.Compact) }
        }
        rule.waitForIdle()
    }

    private fun assertAddButtons(matcher: SemanticsMatcher) {
        val buttons = rule.onAllNodesWithText(rule.activity.getString(R.string.daily_add_item))
        assertEquals("goals and to-do", 2, buttons.fetchSemanticsNodes().size)
        buttons.assertAll(matcher)
    }

    private fun assertFieldsEditable(editable: Boolean) {
        val isField = SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText)
        val isEditable = SemanticsMatcher.expectValue(SemanticsProperties.IsEditable, editable)
        val list = rule.onAllNodes(hasScrollToNodeAction()).onFirst()
        for (text in listOf("goal", "todo", "keep")) {
            list.performScrollToNode(hasText(text))
            rule.onNode(hasText(text) and isField).assert(isEditable)
            rule.onNode(hasText(text) and isField).assert(if (editable) hasSetTextAction() else !hasSetTextAction())
        }
        val fields = rule.onAllNodes(isField)
        assertTrue(fields.fetchSemanticsNodes().isNotEmpty())
        fields.assertAll(isEditable)
    }

    @Test
    fun item151_fieldsAndButtonsAreDisabledBeforeTheLoad() {
        show(loaded = false)
        assertAddButtons(isNotEnabled())
        assertFieldsEditable(false)
    }

    @Test
    fun item151_positiveControlEverythingIsEditableAfterTheLoad() {
        show(loaded = true)
        assertAddButtons(isEnabled())
        assertFieldsEditable(true)
    }
}
