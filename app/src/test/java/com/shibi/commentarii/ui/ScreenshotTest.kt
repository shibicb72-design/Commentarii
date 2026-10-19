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
import com.shibi.commentarii.ui.daily.PeriodGoal
import com.shibi.commentarii.ui.daily.TaskListActions
import com.shibi.commentarii.ui.daily.TaskUi
import com.shibi.commentarii.ui.daily.TodoCard
import com.shibi.commentarii.ui.daily.DailyPageContent
import com.shibi.commentarii.ui.daily.sampleDailyUiState
import com.shibi.commentarii.ui.lock.LockScreen
import com.shibi.commentarii.ui.month.MonthActions
import com.shibi.commentarii.ui.month.MonthPageContent
import com.shibi.commentarii.ui.month.sampleMonthUiState
import com.shibi.commentarii.ui.notes.EditorBlock
import com.shibi.commentarii.ui.notes.NoteEditorActions
import com.shibi.commentarii.ui.notes.NoteEditorContent
import com.shibi.commentarii.ui.notes.NotesActions
import com.shibi.commentarii.ui.notes.NotesListContent
import com.shibi.commentarii.ui.notes.sampleNoteEditorUiState
import com.shibi.commentarii.ui.notes.sampleNotesUiState
import com.shibi.commentarii.ui.theme.AppThemes
import com.shibi.commentarii.ui.theme.CommentariiTheme
import com.shibi.commentarii.ui.week.WeekActions
import com.shibi.commentarii.ui.week.WeekPageContent
import com.shibi.commentarii.ui.week.sampleWeekUiState
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

    // One Daily shot per theme; animate = false keeps the frames deterministic.
    private fun themedDaily(id: String) {
        RuntimeEnvironment.setQualifiers(compact)
        rule.setContent {
            CommentariiTheme(dark = false, theme = AppThemes.byId(id), animate = false) {
                DailyPageContent(sampleDailyUiState(), DailyActions(), WindowWidthSizeClass.Compact)
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/daily_theme_$id.png")
    }

    @Test fun dailyThemeRoseCream() = themedDaily("rose_cream")
    @Test fun dailyThemeWashiPlanner() = themedDaily("washi_planner")
    @Test fun dailyThemeSunnyJournal() = themedDaily("sunny_journal")
    @Test fun dailyThemeCloudDrift() = themedDaily("cloud_drift")
    @Test fun dailyThemePetalFall() = themedDaily("petal_fall")
    @Test fun dailyThemeFloatingHearts() = themedDaily("floating_hearts")
    @Test fun dailyThemeMatchaTea() = themedDaily("matcha_tea")
    @Test fun dailyThemeLavenderMoon() = themedDaily("lavender_moon")

    private val s24u = "w384dp-h832dp-xxxhdpi"
    private val foldCover = "w373dp-h873dp-xxhdpi"
    private val foldInner = "w673dp-h747dp-xxhdpi"

    // Device shots of a themed page: Compact everywhere except the Fold inner screen (Medium).
    private fun themedPage(id: String, page: String, q: String, dev: String) {
        val width = if (dev == "fold_inner") WindowWidthSizeClass.Medium else WindowWidthSizeClass.Compact
        RuntimeEnvironment.setQualifiers(q)
        rule.setContent {
            CommentariiTheme(dark = false, theme = AppThemes.byId(id), animate = false) {
                when (page) {
                    "daily" -> DailyPageContent(sampleDailyUiState(), DailyActions(), width)
                    "week" -> WeekPageContent(sampleWeekUiState(), WeekActions(), width)
                    else -> MonthPageContent(sampleMonthUiState(), MonthActions(), width)
                }
            }
        }
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/${page}_theme_${id}_$dev.png")
    }

    @Test fun dailyWashiS24u() = themedPage("washi_planner", "daily", s24u, "s24u")
    @Test fun dailyWashiFoldCover() = themedPage("washi_planner", "daily", foldCover, "fold_cover")
    @Test fun dailyWashiFoldInner() = themedPage("washi_planner", "daily", foldInner, "fold_inner")
    @Test fun weekWashiS24u() = themedPage("washi_planner", "week", s24u, "s24u")
    @Test fun weekWashiFoldCover() = themedPage("washi_planner", "week", foldCover, "fold_cover")
    @Test fun weekWashiFoldInner() = themedPage("washi_planner", "week", foldInner, "fold_inner")
    @Test fun monthWashiS24u() = themedPage("washi_planner", "month", s24u, "s24u")
    @Test fun monthWashiFoldCover() = themedPage("washi_planner", "month", foldCover, "fold_cover")
    @Test fun monthWashiFoldInner() = themedPage("washi_planner", "month", foldInner, "fold_inner")
    @Test fun dailyCloudS24u() = themedPage("cloud_drift", "daily", s24u, "s24u")
    @Test fun dailyCloudFoldCover() = themedPage("cloud_drift", "daily", foldCover, "fold_cover")
    @Test fun dailyCloudFoldInner() = themedPage("cloud_drift", "daily", foldInner, "fold_inner")
    @Test fun weekCloudS24u() = themedPage("cloud_drift", "week", s24u, "s24u")
    @Test fun weekCloudFoldCover() = themedPage("cloud_drift", "week", foldCover, "fold_cover")
    @Test fun weekCloudFoldInner() = themedPage("cloud_drift", "week", foldInner, "fold_inner")
    @Test fun monthCloudS24u() = themedPage("cloud_drift", "month", s24u, "s24u")
    @Test fun monthCloudFoldCover() = themedPage("cloud_drift", "month", foldCover, "fold_cover")
    @Test fun monthCloudFoldInner() = themedPage("cloud_drift", "month", foldInner, "fold_inner")

    private fun week(name: String, q: String, dark: Boolean, width: WindowWidthSizeClass) =
        shoot(name, q, dark) { WeekPageContent(sampleWeekUiState(), WeekActions(), width) }

    private fun month(name: String, q: String, dark: Boolean, width: WindowWidthSizeClass) =
        shoot(name, q, dark) { MonthPageContent(sampleMonthUiState(), MonthActions(), width) }

    @Test fun weekLightCompact() = week("week_light_compact", compact, false, WindowWidthSizeClass.Compact)
    @Test fun weekDarkCompact() = week("week_dark_compact", compact, true, WindowWidthSizeClass.Compact)
    @Test fun weekLightExpanded() = week("week_light_expanded", expanded, false, WindowWidthSizeClass.Expanded)
    @Test fun weekDarkExpanded() = week("week_dark_expanded", expanded, true, WindowWidthSizeClass.Expanded)

    @Test fun monthLightCompact() = month("month_light_compact", compact, false, WindowWidthSizeClass.Compact)
    @Test fun monthDarkCompact() = month("month_dark_compact", compact, true, WindowWidthSizeClass.Compact)
    @Test fun monthLightExpanded() = month("month_light_expanded", expanded, false, WindowWidthSizeClass.Expanded)
    @Test fun monthDarkExpanded() = month("month_dark_expanded", expanded, true, WindowWidthSizeClass.Expanded)

    @Test fun dailyPeriodCardLightCompact() = shoot("daily_period_card_light_compact", phone, false) {
        val state = sampleDailyUiState().copy(
            weekGoals = listOf(PeriodGoal("Ship the v2 release", done = true), PeriodGoal("Run three times")),
            monthGoals = listOf(PeriodGoal("Plan the winter trip"), PeriodGoal("Read four books")),
        )
        DailyPageContent(state, DailyActions(), WindowWidthSizeClass.Compact)
    }

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
        }
    }
}
