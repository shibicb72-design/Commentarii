@file:OptIn(ExperimentalMaterial3Api::class)

package com.shibi.commentarii.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Material date picker dialog; [onPick] gets the chosen day. Shared by the daily, week and month headers. */
@Composable
fun DatePickerDialogFor(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                pickerState.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                }
            }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/** Script title with an optional subtitle, previous/next arrows and a date-picker button. */
@Composable
fun PeriodHeader(
    title: String,
    subtitle: String?,
    pickerDate: LocalDate,
    previousDescription: String,
    nextDescription: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = previousDescription)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.displaySmall)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelLarge)
        }
        IconButton(onClick = { showDatePicker = true }) {
            Icon(Icons.Filled.CalendarMonth, contentDescription = stringResource(R.string.daily_pick_date))
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = nextDescription)
        }
    }
    if (showDatePicker) {
        DatePickerDialogFor(pickerDate, onPick = onPickDate, onDismiss = { showDatePicker = false })
    }
}
