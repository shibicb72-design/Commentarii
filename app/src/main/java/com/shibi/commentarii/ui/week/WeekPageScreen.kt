package com.shibi.commentarii.ui.week

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

/** Weekly page wired to [WeekViewModel]. [monday] is the Monday of the shown week. */
@Composable
fun WeekPageScreen(
    container: AppContainer,
    monday: LocalDate,
    onSelectWeek: (LocalDate) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    windowWidthClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val vm: WeekViewModel = viewModel(key = "week-$monday", factory = WeekViewModel.factory(container, monday))
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushNow() }
    val actions = remember(vm, onSelectWeek, onOpenDay) { vm.buildActions(onSelectWeek, onOpenDay) }
    WeekPageContent(state, actions, windowWidthClass, modifier)
}
