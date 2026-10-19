package com.shibi.commentarii.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R

/**
 * Stateless daily page. Compact width: one scrolling column of all sections.
 * Medium/Expanded: two columns (left: priorities..mood, right: schedule and notes), footer spanning.
 * [banner] is an optional slot shown above the header (export reminder etc.).
 */
@Composable
fun DailyPageContent(
    state: DailyUiState,
    actions: DailyActions,
    windowWidthClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    banner: (@Composable () -> Unit)? = null,
) {
    val twoColumns = windowWidthClass != WindowWidthSizeClass.Compact
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (banner != null) item { banner() }
            item { DailyHeader(state, actions) }
            item { PeriodGoalsCard(state.weekGoals, state.monthGoals, actions.onOpenWeek, actions.onOpenMonth) }
            if (twoColumns) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PrioritiesCard(state.priorities, actions.priorities)
                            TodoCard(state.todo, actions.todo)
                            DontForgetCard(state.dontForget, actions.dontForget)
                            GratefulCard(state.grateful, actions.grateful)
                            SelfCare(state, actions)
                            MoodCard(state.mood, actions.onMood)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Schedule(state, actions)
                            NotesCard(state.notes, actions.onNotes)
                        }
                    }
                }
            } else {
                item { PrioritiesCard(state.priorities, actions.priorities) }
                item { TodoCard(state.todo, actions.todo) }
                item { Schedule(state, actions) }
                item { DontForgetCard(state.dontForget, actions.dontForget) }
                item { GratefulCard(state.grateful, actions.grateful) }
                item { SelfCare(state, actions) }
                item { MoodCard(state.mood, actions.onMood) }
                item { NotesCard(state.notes, actions.onNotes) }
            }
            item {
                Text(
                    stringResource(R.string.daily_footer),
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun Schedule(state: DailyUiState, actions: DailyActions) = ScheduleCard(
    rows = state.schedule,
    onText = actions.onScheduleText,
    onAddCustomTime = actions.onAddCustomTime,
    onRemoveCustomTime = actions.onRemoveCustomTime,
)

@Composable
private fun SelfCare(state: DailyUiState, actions: DailyActions) = SelfCareCard(
    items = state.selfCare,
    onToggle = actions.onToggleSelfCare,
    onAdd = actions.onAddSelfCare,
    onRename = actions.onRenameSelfCare,
    onRemove = actions.onRemoveSelfCare,
)
