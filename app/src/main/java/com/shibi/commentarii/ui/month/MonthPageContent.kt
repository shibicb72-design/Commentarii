package com.shibi.commentarii.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.common.GoalsCard
import com.shibi.commentarii.ui.common.PeriodHeader
import com.shibi.commentarii.ui.common.ReviewCard
import com.shibi.commentarii.ui.daily.NotesCard
import com.shibi.commentarii.ui.daily.TodoCard
import com.shibi.commentarii.ui.theme.ThemedPage
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/**
 * Stateless monthly page. Compact width: one scrolling column of all sections.
 * Medium/Expanded: two columns (left: calendar and review, right: goals, events, to-dos, notes and review).
 */
@Composable
fun MonthPageContent(
    state: MonthUiState,
    actions: MonthActions,
    windowWidthClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val twoColumns = windowWidthClass != WindowWidthSizeClass.Compact
    ThemedPage(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { MonthHeader(state, actions) }
            if (twoColumns) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            MonthCalendarCard(state.grid, state.today, actions.onOpenDay)
                            ReviewCard(stringResource(R.string.section_review), state.review, actions.onReview, enabled = state.loaded)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            GoalsCard(state.goals, actions.goals, maxItems = null, enabled = state.loaded)
                            EventsSection(state, actions)
                            TodoCard(state.todo, actions.todo, enabled = state.loaded)
                            NotesCard(state.notes, actions.onNotes, enabled = state.loaded)
                        }
                    }
                }
            } else {
                item { MonthCalendarCard(state.grid, state.today, actions.onOpenDay) }
                item { GoalsCard(state.goals, actions.goals, maxItems = null, enabled = state.loaded) }
                item { EventsSection(state, actions) }
                item { TodoCard(state.todo, actions.todo, enabled = state.loaded) }
                item { NotesCard(state.notes, actions.onNotes, enabled = state.loaded) }
                item { ReviewCard(stringResource(R.string.section_review), state.review, actions.onReview, enabled = state.loaded) }
            }
        }
    }
}

@Composable
private fun EventsSection(state: MonthUiState, actions: MonthActions) = EventsCard(
    events = state.events,
    onAdd = actions.onAddEvent,
    onText = actions.onEventText,
    onDate = actions.onEventDate,
    onRemove = actions.onRemoveEvent,
    enabled = state.loaded,
)

@Composable
private fun MonthHeader(state: MonthUiState, actions: MonthActions) {
    val monthName = remember(state.month) {
        state.month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault())
    }
    PeriodHeader(
        title = stringResource(R.string.month_title_fmt, monthName, state.month.year),
        subtitle = null,
        pickerDate = state.month.atDay(1),
        previousDescription = stringResource(R.string.month_previous),
        nextDescription = stringResource(R.string.month_next),
        onPrevious = { actions.onSelectMonth(state.month.minusMonths(1)) },
        onNext = { actions.onSelectMonth(state.month.plusMonths(1)) },
        onPickDate = { actions.onSelectMonth(YearMonth.from(it)) },
    )
}
