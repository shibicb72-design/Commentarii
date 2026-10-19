package com.shibi.commentarii.ui

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.shibi.commentarii.ui.daily.DailyActions
import com.shibi.commentarii.ui.daily.DailyPageContent
import com.shibi.commentarii.ui.daily.sampleDailyUiState
import com.shibi.commentarii.ui.lock.LockScreen
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
}
