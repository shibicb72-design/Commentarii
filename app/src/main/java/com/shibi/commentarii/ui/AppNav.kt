package com.shibi.commentarii.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.R
import com.shibi.commentarii.data.PageType
import com.shibi.commentarii.domain.parseMonthRoute
import com.shibi.commentarii.domain.parseWeekRoute
import com.shibi.commentarii.domain.weekStart
import com.shibi.commentarii.ui.daily.DailyPageScreen
import com.shibi.commentarii.ui.lock.NavRestore
import com.shibi.commentarii.ui.month.MonthPageScreen
import com.shibi.commentarii.ui.notes.NoteEditorScreen
import com.shibi.commentarii.ui.notes.NotesListScreen
import com.shibi.commentarii.ui.search.SearchScreen
import com.shibi.commentarii.ui.settings.DebugLogScreen
import com.shibi.commentarii.ui.settings.SettingsScreen
import com.shibi.commentarii.ui.week.WeekPageScreen
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth

object Routes {
    const val DAILY = "daily/{date}"
    const val WEEK = "week/{mondayDate}"
    const val MONTH = "month/{yearMonth}"
    const val NOTE = "note/{id}"
    const val NOTES = "notes"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val DEBUG = "settings/debug"

    fun daily(date: LocalDate) = "daily/$date"
    fun week(date: LocalDate) = "week/${weekStart(date)}"
    fun month(month: YearMonth) = "month/$month"
    fun note(id: Long) = "note/$id"
}

internal enum class Tab(val label: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Filled.Today),
    WEEK(R.string.tab_week, Icons.Outlined.ViewWeek),
    MONTH(R.string.tab_month, Icons.Outlined.CalendarMonth),
    NOTES(R.string.tab_notes, Icons.Filled.EditNote),
    SEARCH(R.string.tab_search, Icons.Filled.Search),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

private fun tabOf(route: String?): Tab? = when {
    route == null -> null
    route.startsWith("daily/") -> Tab.TODAY
    route.startsWith("week/") -> Tab.WEEK
    route.startsWith("month/") -> Tab.MONTH
    route.startsWith("note") -> Tab.NOTES
    route == Routes.SEARCH -> Tab.SEARCH
    route == Routes.SETTINGS || route == Routes.DEBUG -> Tab.SETTINGS
    else -> null
}

/** App shell: bottom bar on Compact width, navigation rail on Medium/Expanded. */
@Composable
fun AppNav(
    container: AppContainer,
    widthClass: WindowWidthSizeClass,
    openDate: LocalDate? = null,
    onOpenDateHandled: () -> Unit = {},
    restoreRoutes: List<String> = emptyList(),
    onRoutesChanged: (List<String>) -> Unit = {},
    modifier: Modifier = Modifier,
    clock: Clock = container.clock,
    weekHome: MutableState<String> = rememberSaveable { mutableStateOf(Routes.week(LocalDate.now(clock))) },
    monthHome: MutableState<String> = rememberSaveable { mutableStateOf(Routes.month(YearMonth.now(clock))) },
) {
    val nav = rememberNavController()
    val today = remember { LocalDate.now(clock) }
    val entry by nav.currentBackStackEntryAsState()
    val current = tabOf(entry?.destination?.route)
    val compact = widthClass == WindowWidthSizeClass.Compact
    // Read at tap time: the app can stay open past midnight. [today] stays fixed because it feeds the graph's defaultValue.
    // Week and Month restore the page they last showed, unless the week or month has changed since their last tap.
    // MainActivity holds their homes above the lock screen, which removes AppNav from composition.
    val onTab: (Tab) -> Unit = { tab ->
        val now = LocalDate.now(clock)
        val restore = when (tab) {
            Tab.WEEK -> (Routes.week(now) == weekHome.value).also { weekHome.value = Routes.week(now) }
            Tab.MONTH -> Routes.month(YearMonth.from(now)).let { home -> (home == monthHome.value).also { monthHome.value = home } }
            else -> true
        }
        navigateToTab(nav, tab, now, restore)
        // Skipping a saved page keeps it, and this navigate can have just saved the old page too, so discard it last.
        if (!restore) nav.clearBackStack(if (tab == Tab.WEEK) Routes.WEEK else Routes.MONTH)
    }

    // After an unlock the previous back stack is replayed; recording starts only once that is done. Saveable because a recreate
    // brings the back stack back by itself, and a replay on top of that would stack the screens again.
    var restored by rememberSaveable { mutableStateOf(restoreRoutes.isEmpty()) }
    LaunchedEffect(Unit) {
        if (!restored) {
            NavRestore.plan(restoreRoutes).forEach { step ->
                nav.navigate(step.route) {
                    if (step.replaceStart) popUpTo(Routes.DAILY) { inclusive = true }
                }
            }
            restored = true
        }
    }
    LaunchedEffect(entry, restored) {
        if (restored) onRoutesChanged(nav.currentBackStack.value.mapNotNull { concreteRoute(it, today) })
    }

    LaunchedEffect(openDate) {
        openDate?.let { d ->
            nav.navigate(Routes.daily(d)) { popUpTo(Routes.DAILY) { inclusive = true } }
            onOpenDateHandled()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            bottomBar = { if (compact) AppBottomBar(current, onTab) },
        ) { padding ->
            AppScaffoldBody(padding, rail = { if (!compact) AppRail(current, onTab) }) {
                NavHost(nav, startDestination = Routes.DAILY) {
                    composable(
                        Routes.DAILY,
                        arguments = listOf(
                            navArgument("date") {
                                type = NavType.StringType
                                defaultValue = today.toString()
                            },
                        ),
                    ) { e ->
                        val date = e.arguments?.getString("date")
                            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
                        DailyPageScreen(
                            container = container,
                            date = date,
                            onSelectDate = { d ->
                                nav.navigate(Routes.daily(d)) {
                                    popUpTo(Routes.DAILY) { inclusive = true }
                                }
                            },
                            windowWidthClass = widthClass,
                            onOpenWeek = { d -> nav.navigate(Routes.week(d)) },
                            onOpenMonth = { ym -> nav.navigate(Routes.month(ym)) },
                        )
                    }
                    composable(
                        Routes.WEEK,
                        arguments = listOf(navArgument("mondayDate") { type = NavType.StringType }),
                    ) { e ->
                        WeekPageScreen(
                            container = container,
                            monday = parseWeekRoute(e.arguments?.getString("mondayDate"), today),
                            onSelectWeek = { d ->
                                nav.navigate(Routes.week(d)) { popUpTo(Routes.WEEK) { inclusive = true } }
                            },
                            onOpenDay = { d -> nav.navigate(Routes.daily(d)) },
                            windowWidthClass = widthClass,
                        )
                    }
                    composable(
                        Routes.MONTH,
                        arguments = listOf(navArgument("yearMonth") { type = NavType.StringType }),
                    ) { e ->
                        MonthPageScreen(
                            container = container,
                            month = parseMonthRoute(e.arguments?.getString("yearMonth"), today),
                            onSelectMonth = { ym ->
                                nav.navigate(Routes.month(ym)) { popUpTo(Routes.MONTH) { inclusive = true } }
                            },
                            onOpenDay = { d -> nav.navigate(Routes.daily(d)) },
                            windowWidthClass = widthClass,
                        )
                    }
                    composable(
                        Routes.NOTE,
                        arguments = listOf(navArgument("id") { type = NavType.LongType }),
                    ) { e ->
                        NoteEditorScreen(
                            container = container,
                            noteId = e.arguments?.getLong("id") ?: 0L,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable(Routes.NOTES) {
                        NotesListScreen(
                            container = container,
                            onOpenNote = { nav.navigate(Routes.note(it)) },
                            onNewNote = { nav.navigate(Routes.note(it)) },
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(
                            container = container,
                            onOpenPage = { type, id, date ->
                                when {
                                    type == PageType.DAILY && date != null -> nav.navigate(Routes.daily(date))
                                    type == PageType.WEEKLY && date != null -> nav.navigate(Routes.week(date))
                                    type == PageType.MONTHLY && date != null -> nav.navigate(Routes.month(YearMonth.from(date)))
                                    else -> nav.navigate(Routes.note(id))
                                }
                            },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(container, onOpenDebug = { nav.navigate(Routes.DEBUG) })
                    }
                    composable(Routes.DEBUG) { DebugLogScreen(container, onBack = { nav.popBackStack() }) }
                }
            }
        }
    }
}

/** Route with its arguments filled in (e.g. `daily/2026-10-18`), or null for entries without a destination route. */
/** One line that shrinks to fit its tab: at the largest font sizes the tabs are narrower than their words. */
@Composable
private fun TabLabel(tab: Tab) {
    Text(
        stringResource(tab.label),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 5.sp, maxFontSize = LocalTextStyle.current.fontSize),
    )
}

/** Bottom navigation bar shown on Compact width. */
@Composable
internal fun AppBottomBar(current: Tab?, onTab: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == current,
                onClick = { onTab(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { TabLabel(tab) },
            )
        }
    }
}

/** Navigation rail shown on Medium/Expanded width. The Scaffold's padding already keeps it clear of the system bars. */
@Composable
private fun AppRail(current: Tab?, onTab: (Tab) -> Unit) {
    NavigationRail(windowInsets = WindowInsets(0, 0, 0, 0)) {
        Tab.entries.forEach { tab ->
            NavigationRailItem(
                selected = tab == current,
                onClick = { onTab(tab) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { TabLabel(tab) },
            )
        }
    }
}

/**
 * Scaffold body: applies the Scaffold [padding] (status bar on top, bottom bar + nav bar below) and
 * marks it consumed for [content], so a screen's imePadding() only adds the part of the keyboard that
 * the bottom bar does not already cover. Without it the editor's format toolbar floats a bar-height
 * above the IME. The [rail] takes the same padding and sits outside the consumed area.
 * [consumeInsets] = false is the old layout, kept only for ImeGapTest; production uses the default.
 */
@Composable
internal fun AppScaffoldBody(
    padding: PaddingValues,
    consumeInsets: Boolean = true,
    rail: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Row(Modifier.padding(padding)) {
        rail()
        val body = Modifier.weight(1f)
        Box(if (consumeInsets) body.consumeWindowInsets(padding) else body) { content() }
    }
}

private fun concreteRoute(entry: NavBackStackEntry, today: LocalDate): String? = when (val route = entry.destination.route) {
    Routes.DAILY -> Routes.daily(
        entry.arguments?.getString("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today,
    )
    Routes.WEEK -> Routes.week(parseWeekRoute(entry.arguments?.getString("mondayDate"), today))
    Routes.MONTH -> Routes.month(parseMonthRoute(entry.arguments?.getString("yearMonth"), today))
    Routes.NOTE -> entry.arguments?.getLong("id")?.let { Routes.note(it) }
    else -> route
}

private fun navigateToTab(nav: NavHostController, tab: Tab, today: LocalDate, restore: Boolean) {
    when (tab) {
        Tab.TODAY -> nav.navigate(Routes.daily(today)) {
            popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
        else -> nav.navigate(
            when (tab) {
                Tab.WEEK -> Routes.week(today)
                Tab.MONTH -> Routes.month(YearMonth.from(today))
                Tab.NOTES -> Routes.NOTES
                Tab.SEARCH -> Routes.SEARCH
                else -> Routes.SETTINGS
            },
        ) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = restore
        }
    }
}
