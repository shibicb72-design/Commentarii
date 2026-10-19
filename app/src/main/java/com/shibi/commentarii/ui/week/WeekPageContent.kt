package com.shibi.commentarii.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import com.shibi.commentarii.domain.isoWeek
import com.shibi.commentarii.domain.weekDays
import com.shibi.commentarii.ui.common.GoalsCard
import com.shibi.commentarii.ui.common.PeriodHeader
import com.shibi.commentarii.ui.common.ReviewCard
import com.shibi.commentarii.ui.daily.DontForgetCard
import com.shibi.commentarii.ui.daily.NotesCard
import com.shibi.commentarii.ui.daily.TodoCard
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Stateless weekly page. Compact width: one scrolling column of all sections.
 * Medium/Expanded: two columns (left: goals..mood, right: the seven days, notes and review).
 */
@Composable
fun WeekPageContent(
    state: WeekUiState,
    actions: WeekActions,
    windowWidthClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val twoColumns = windowWidthClass != WindowWidthSizeClass.Compact
    val days = remember(state.monday) { weekDays(state.monday) }
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
            item { WeekHeader(state, actions) }
            if (twoColumns) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            GoalsCard(state.goals, actions.goals, maxItems = WeekViewModel.GOAL_SLOTS, enabled = state.loaded)
                            TodoCard(state.todo, actions.todo, enabled = state.loaded)
                            DontForgetCard(state.dontForget, actions.dontForget, enabled = state.loaded)
                            WeekMoodCard(days, state.moods, actions.onOpenDay)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            WeekDaysCard(state.days, state.today, actions.onOpenDay)
                            NotesCard(state.notes, actions.onNotes, enabled = state.loaded)
                            ReviewCard(stringResource(R.string.section_review_well), state.reviewWell, actions.onReviewWell, enabled = state.loaded)
                            ReviewCard(stringResource(R.string.section_review_improve), state.reviewImprove, actions.onReviewImprove, enabled = state.loaded)
                        }
                    }
                }
            } else {
                item { GoalsCard(state.goals, actions.goals, maxItems = WeekViewModel.GOAL_SLOTS, enabled = state.loaded) }
                item { WeekDaysCard(state.days, state.today, actions.onOpenDay) }
                item { TodoCard(state.todo, actions.todo, enabled = state.loaded) }
                item { DontForgetCard(state.dontForget, actions.dontForget, enabled = state.loaded) }
                item { WeekMoodCard(days, state.moods, actions.onOpenDay) }
                item { NotesCard(state.notes, actions.onNotes, enabled = state.loaded) }
                item { ReviewCard(stringResource(R.string.section_review_well), state.reviewWell, actions.onReviewWell, enabled = state.loaded) }
                item { ReviewCard(stringResource(R.string.section_review_improve), state.reviewImprove, actions.onReviewImprove, enabled = state.loaded) }
            }
        }
    }
}

@Composable
private fun WeekHeader(state: WeekUiState, actions: WeekActions) {
    val format = remember { DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()) }
    val sunday = state.monday.plusDays(6)
    PeriodHeader(
        title = stringResource(R.string.week_title_fmt, state.monday.format(format), sunday.format(format)),
        subtitle = stringResource(R.string.iso_week_fmt, isoWeek(state.monday).week),
        pickerDate = state.monday,
        previousDescription = stringResource(R.string.week_previous),
        nextDescription = stringResource(R.string.week_next),
        onPrevious = { actions.onSelectWeek(state.monday.minusWeeks(1)) },
        onNext = { actions.onSelectWeek(state.monday.plusWeeks(1)) },
        onPickDate = actions.onSelectWeek,
    )
}
