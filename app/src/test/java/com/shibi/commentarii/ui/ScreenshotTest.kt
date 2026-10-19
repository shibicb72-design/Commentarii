package com.shibi.commentarii.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.ui.daily.DailyActions
import com.shibi.commentarii.ui.daily.DontForgetCard
import com.shibi.commentarii.ui.daily.SelfCareCard
import com.shibi.commentarii.ui.daily.SelfCareUi
import com.shibi.commentarii.ui.daily.TaskListActions
import com.shibi.commentarii.ui.daily.TaskUi
import com.shibi.commentarii.ui.daily.TodoCard
import com.shibi.commentarii.ui.daily.DailyPageContent
import com.shibi.commentarii.ui.daily.sampleDailyUiState
import com.shibi.commentarii.ui.lock.LockScreen
import com.shibi.commentarii.ui.notes.EditorBlock
import com.shibi.commentarii.ui.notes.NoteEditorActions
import com.shibi.commentarii.ui.notes.NoteEditorContent
import com.shibi.commentarii.ui.notes.NotesActions
import com.shibi.commentarii.ui.notes.NotesListContent
import com.shibi.commentarii.ui.notes.sampleNoteEditorUiState
import com.shibi.commentarii.ui.notes.sampleNotesUiState
import com.shibi.commentarii.ui.theme.CommentariiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Records the screenshots listed in docs/plan-v1.md into app/build/outputs/roborazzi/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ScreenshotTest {
    @get:Rule val rule = createComposeRule()

    // Compact is very tall so the lazy column composes every section in one capture.
    private val compact = "w412dp-h3200dp-xxhdpi"
    private val phone = "w412dp-h915dp-xxhdpi"
    private val tallPhone = "w412dp-h2000dp-xxhdpi"
    private val expanded = "w884dp-h2000dp-xhdpi"

    private fun shoot(name: String, qualifiers: String, dark: Boolean, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        rule.setContent { CommentariiTheme(dark = dark, content = content) }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private fun daily(name: String, q: String, dark: Boolean, width: WindowWidthSizeClass) =
        shoot(name, q, dark) { DailyPageContent(sampleDailyUiState(), DailyActions(), width) }

    @Test fun dailyLightCompact() = daily("daily_light_compact", compact, false, WindowWidthSizeClass.Compact)
    @Test fun dailyDarkCompact() = daily("daily_dark_compact", compact, true, WindowWidthSizeClass.Compact)
    @Test fun dailyLightExpanded() = daily("daily_light_expanded", expanded, false, WindowWidthSizeClass.Expanded)
    @Test fun dailyDarkExpanded() = daily("daily_dark_expanded", expanded, true, WindowWidthSizeClass.Expanded)

    @Test fun notesListLight() = shoot("notes_list_light", phone, false) {
        NotesListContent(sampleNotesUiState(), NotesActions())
    }

    @Test fun noteEditorLight() = shoot("note_editor_light", phone, false) {
        NoteEditorContent(sampleNoteEditorUiState(), NoteEditorActions())
    }

    @Test fun lockScreenLight() = shoot("lock_screen_light", phone, false) { LockScreen(onUnlock = {}) }
    @Test fun lockScreenDark() = shoot("lock_screen_dark", phone, true) { LockScreen(onUnlock = {}) }

    /** Checkbox / bullet centres must line up with the first text line, for short and 3-line items. */
    @Test fun checklistAlignmentLight() = shoot("checklist_alignment_light", tallPhone, false) {
        val long = "A much longer item that wraps over several lines so the checkbox has to stay beside the first " +
            "line only and never drift to the middle of the whole text block."
        val editor = sampleNoteEditorUiState().copy(
            blocks = listOf(
                EditorBlock(1, DocBlock(BlockType.CHECKBOX, "Short item", checked = false)),
                EditorBlock(2, DocBlock(BlockType.CHECKBOX, long, checked = false)),
                EditorBlock(3, DocBlock(BlockType.CHECKBOX, "Short done item", checked = true)),
                EditorBlock(4, DocBlock(BlockType.CHECKBOX, long, checked = true)),
                EditorBlock(5, DocBlock(BlockType.BULLET, "Short bullet")),
                EditorBlock(6, DocBlock(BlockType.BULLET, long)),
            ),
        )
        Column {
            Box(Modifier.height(900.dp)) { NoteEditorContent(editor, NoteEditorActions()) }
            TodoCard(listOf(TaskUi(1, "Short task"), TaskUi(2, long)), TaskListActions())
            DontForgetCard(listOf(TaskUi(1, "Short item"), TaskUi(2, long)), TaskListActions())
            SelfCareCard(
                listOf(SelfCareUi(1, "Short habit", false, 0), SelfCareUi(2, long, true, 3)),
                {}, {}, { _, _ -> }, {},
            )
        }
    }
}
