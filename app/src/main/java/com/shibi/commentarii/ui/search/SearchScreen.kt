package com.shibi.commentarii.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.R
import com.shibi.commentarii.data.PageType
import com.shibi.commentarii.ui.theme.AppColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/** Search wired to [SearchViewModel]. [onOpenPage] gets (page type, page id, daily date or null). */
@Composable
fun SearchScreen(
    container: AppContainer,
    onOpenPage: (type: String, id: Long, date: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm: SearchViewModel = viewModel(factory = SearchViewModel.factory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    SearchContent(state, vm::onQuery, onOpenPage, modifier)
}

@Composable
fun SearchContent(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onOpenPage: (type: String, id: Long, date: LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.sr_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.sr_clear))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            when {
                state.query.isBlank() -> Message(stringResource(R.string.sr_prompt))
                state.searched && state.results.isEmpty() -> Message(stringResource(R.string.sr_no_results))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.results, key = { it.id }) { r ->
                        ResultCard(r, onClick = { onOpenPage(r.type, r.id, r.date) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopCenter) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ResultCard(result: SearchResult, onClick: () -> Unit) {
    val colors = AppColors.current
    val monthName = result.date?.month?.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()).orEmpty()
    val title = when {
        result.type == PageType.DAILY && result.date != null ->
            result.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        result.type == PageType.WEEKLY && result.date != null -> {
            val format = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
            stringResource(
                R.string.week_title_fmt,
                result.date.format(format),
                result.date.plusDays(6).format(format),
            )
        }
        result.type == PageType.MONTHLY && result.date != null ->
            stringResource(R.string.month_title_fmt, monthName, result.date.year)
        else -> result.title.ifBlank { stringResource(R.string.nt_untitled) }
    }
    val icon = when (result.type) {
        PageType.DAILY -> Icons.Filled.Today
        PageType.WEEKLY -> Icons.Outlined.ViewWeek
        PageType.MONTHLY -> Icons.Outlined.CalendarMonth
        else -> Icons.Filled.EditNote
    }
    val typeLabel = stringResource(
        when (result.type) {
            PageType.DAILY -> R.string.sr_type_daily
            PageType.WEEKLY -> R.string.sr_type_week
            PageType.MONTHLY -> R.string.sr_type_month
            else -> R.string.sr_type_note
        },
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = colors.card,
        contentColor = colors.onCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = typeLabel)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            if (result.pinned) {
                Icon(Icons.Filled.PushPin, contentDescription = stringResource(R.string.nt_pinned))
            }
        }
    }
}
