package com.shibi.commentarii.ui.daily

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
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
    val reminderBanner: (@Composable () -> Unit)? = if (showReminder) {
        { ExportReminderBanner(onDismiss = vm::dismissReminder) }
    } else {
        banner
    }
    DailyPageContent(state, actions, windowWidthClass, modifier, reminderBanner)
    offer?.let {
        RolloverDialog(
            offer = it,
            onMoveAll = { vm.resolveRollover(it.items.map { item -> item.id }.toSet()) },
            onMoveSelected = vm::resolveRollover,
            onSkip = { vm.resolveRollover(emptySet()) },
        )
    }
}
