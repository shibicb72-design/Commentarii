package com.shibi.commentarii.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.daily.AddRowButton
import com.shibi.commentarii.ui.daily.CircleCheck
import com.shibi.commentarii.ui.daily.LineField
import com.shibi.commentarii.ui.daily.SectionCard
import com.shibi.commentarii.ui.daily.SmallIconButton
import com.shibi.commentarii.ui.daily.TaskListActions
import com.shibi.commentarii.ui.daily.TaskUi

/** Goals of a week (fixed rows, [maxItems] = 3) or a month (unlimited, [maxItems] = null). */
@Composable
fun GoalsCard(
    items: List<TaskUi>,
    actions: TaskListActions,
    maxItems: Int?,
    modifier: Modifier = Modifier,
) {
    SectionCard(stringResource(R.string.section_goals), modifier) {
        items.forEachIndexed { i, item ->
            LineField(
                value = item.text,
                onValueChange = { actions.onText(i, it) },
                placeholder = stringResource(R.string.goal_hint, i + 1),
                strike = item.done,
                leading = { CircleCheck(item.done, { actions.onToggleDone(i) }) },
                trailing = {
                    SmallIconButton(Icons.Filled.Close, stringResource(R.string.daily_remove), { actions.onRemove(i) })
                },
            )
        }
        if (maxItems == null || items.size < maxItems) {
            AddRowButton(stringResource(R.string.daily_add_item), actions.onAdd)
        }
    }
}

/** Free-text review box (what went well, what to improve, month review). */
@Composable
fun ReviewCard(title: String, text: String, onText: (String) -> Unit, modifier: Modifier = Modifier) {
    SectionCard(title, modifier) {
        LineField(
            value = text,
            onValueChange = onText,
            placeholder = stringResource(R.string.review_hint),
            singleLine = false,
            minLines = 3,
        )
    }
}
