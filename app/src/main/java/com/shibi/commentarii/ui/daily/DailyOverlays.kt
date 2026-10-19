package com.shibi.commentarii.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import com.shibi.commentarii.domain.RolloverOffer
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Dismissable "export a backup" banner. Colours come from its own container. */
@Composable
fun ExportReminderBanner(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.reminder_text), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reminder_dismiss)) }
        }
    }
}

/**
 * "Carry over N unfinished items from <date>?" with Move all / Pick... / Skip. Pick switches to a checkbox list
 * (all ticked initially) with a confirm button. Any choice calls exactly one of the callbacks; tapping outside or back calls [onDismiss] (asked again next open).
 */
@Composable
fun RolloverDialog(
    offer: RolloverOffer,
    onMoveAll: () -> Unit,
    onMoveSelected: (Set<Long>) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit = {},
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    var selected by remember(offer) { mutableStateOf(offer.items.map { it.id }.toSet()) }
    val dateText = offer.fromDate.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val n = offer.items.size

    if (!picking) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    if (n == 1) {
                        stringResource(R.string.rollover_title_one, dateText)
                    } else {
                        stringResource(R.string.rollover_title, n, dateText)
                    },
                )
            },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    offer.items.forEach { Text("• " + it.text, style = MaterialTheme.typography.bodyMedium) }
                }
            },
            confirmButton = { TextButton(onClick = onMoveAll) { Text(stringResource(R.string.rollover_move_all)) } },
            dismissButton = {
                Row {
                    TextButton(onClick = { picking = true }) { Text(stringResource(R.string.rollover_pick)) }
                    TextButton(onClick = onSkip) { Text(stringResource(R.string.rollover_skip)) }
                }
            },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.rollover_pick_title)) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    offer.items.forEach { item ->
                        val checked = item.id in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(checked, role = Role.Checkbox) {
                                    selected = if (checked) selected - item.id else selected + item.id
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                            Text(item.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onMoveSelected(selected) }) { Text(stringResource(R.string.rollover_move_selected)) }
            },
            dismissButton = { TextButton(onClick = onSkip) { Text(stringResource(R.string.rollover_skip)) } },
        )
    }
}
