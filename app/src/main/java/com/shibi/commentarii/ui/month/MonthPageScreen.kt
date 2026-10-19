package com.shibi.commentarii.ui.month

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
import java.time.YearMonth

/** Monthly page wired to [MonthViewModel]. */
@Composable
fun MonthPageScreen(
    container: AppContainer,
    month: YearMonth,
    onSelectMonth: (YearMonth) -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    windowWidthClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
) {
    val vm: MonthViewModel = viewModel(key = "month-$month", factory = MonthViewModel.factory(container, month))
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.flushNow() }
    val actions = remember(vm, onSelectMonth, onOpenDay) { vm.buildActions(onSelectMonth, onOpenDay) }
    MonthPageContent(state, actions, windowWidthClass, modifier)
}
