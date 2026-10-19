package com.shibi.commentarii.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.settings.NotificationsBlockedMessage
import com.shibi.commentarii.ui.settings.openNotificationSettings
import com.shibi.commentarii.ui.settings.outcomeMessageRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.AppContainer
import java.time.LocalDate

/** Daily page wired to [DailyViewModel]. [onSelectDate] navigates to another day. */
@Composable
fun DailyPageScreen(
    container: AppContainer,
    date: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    windowWidthClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    banner: (@Composable () -> Unit)? = null,
) {
    val vm: DailyViewModel = viewModel(key = "daily-$date", factory = DailyViewModel.factory(container, date))
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushNow() }
    val actions = remember(vm, onSelectDate) { vm.buildActions(onSelectDate) }
    val showReminder by vm.reminderVisible.collectAsStateWithLifecycle()
    val offer by vm.rollover.collectAsStateWithLifecycle()
    val blocked by container.notificationsBlocked.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val immediateText = stringResource(R.string.outcome_immediate)
    val pastText = stringResource(R.string.outcome_past)
    LaunchedEffect(container) {
        container.alarms.outcomes.collect { outcome ->
            val text = when (outcomeMessageRes(outcome.kind)) {
                R.string.outcome_immediate -> immediateText
                R.string.outcome_past -> pastText
                else -> null
            }
            if (text != null) {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(text)
            }
        }
    }
    val topBanner: (@Composable () -> Unit)? = when {
        showReminder -> {
            { ExportReminderBanner(onDismiss = vm::dismissReminder) }
        }
        blocked -> {
            { NotificationsBlockedBanner { openNotificationSettings(context) } }
        }
        else -> banner
    }
    Box(modifier) {
        DailyPageContent(state, actions, windowWidthClass, Modifier, topBanner)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    offer?.let {
        RolloverDialog(
            offer = it,
            onMoveAll = { vm.resolveRollover(it.items.map { item -> item.id }.toSet()) },
            onMoveSelected = vm::resolveRollover,
            onSkip = { vm.resolveRollover(emptySet()) },
            onDismiss = vm::dismissRollover,
        )
    }
}

/** Shown while the system blocks notifications; colours come from its own container. */
@Composable
private fun NotificationsBlockedBanner(onOpenSettings: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NotificationsBlockedMessage(onOpenSettings)
        }
    }
}
