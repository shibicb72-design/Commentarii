package com.shibi.commentarii.ui.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.theme.AppColors

/**
 * Read-only "This week & month" card at the top of the daily page: the goals of both periods, collapsible.
 * Tapping a half opens that period's page. Hidden when there are no goals; the goals are not tasks of the day.
 */
@Composable
fun PeriodGoalsCard(
    weekGoals: List<PeriodGoal>,
    monthGoals: List<PeriodGoal>,
    onOpenWeek: () -> Unit,
    onOpenMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (weekGoals.isEmpty() && monthGoals.isEmpty()) return
    val colors = AppColors.current
    var expanded by rememberSaveable { mutableStateOf(true) }
    Surface(
        color = colors.card,
        contentColor = colors.onCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Surface(
                onClick = { expanded = !expanded },
                color = colors.card,
                contentColor = colors.onCard,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.period_card_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                    )
                    Icon(
                        if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(
                            if (expanded) R.string.period_card_collapse else R.string.period_card_expand,
                        ),
                    )
                }
            }
            if (expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    GoalsHalf(R.string.period_card_week, weekGoals, onOpenWeek)
                    GoalsHalf(R.string.period_card_month, monthGoals, onOpenMonth)
                }
            }
        }
    }
}

@Composable
private fun GoalsHalf(titleRes: Int, goals: List<PeriodGoal>, onOpen: () -> Unit) {
    if (goals.isEmpty()) return
    val colors = AppColors.current
    Surface(
        onClick = onOpen,
        color = colors.card,
        contentColor = colors.onCard,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            TextButton(onClick = onOpen, modifier = Modifier.padding(0.dp)) {
                Text(stringResource(titleRes), style = MaterialTheme.typography.labelLarge)
            }
            goals.forEach { goal ->
                Text(
                    "• ${goal.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    textDecoration = if (goal.done) TextDecoration.LineThrough else TextDecoration.None,
                    modifier = Modifier.padding(start = 12.dp, bottom = 2.dp),
                )
            }
        }
    }
}
