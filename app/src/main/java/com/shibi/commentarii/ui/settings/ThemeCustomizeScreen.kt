package com.shibi.commentarii.ui.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.ui.daily.DailyActions
import com.shibi.commentarii.ui.daily.DailyPageContent
import com.shibi.commentarii.ui.daily.sampleDailyUiState
import com.shibi.commentarii.ui.theme.AppTheme
import com.shibi.commentarii.ui.theme.AppThemes
import com.shibi.commentarii.ui.theme.CommentariiTheme

/** One theme's customize page: live preview, Light/Dark preview choice, Animated/Off and Reset. An unknown id pops back. */
@Composable
fun ThemeCustomizeScreen(
    container: AppContainer,
    themeId: String?,
    widthClass: WindowWidthSizeClass,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stock = AppThemes.all.firstOrNull { it.id == themeId }
    if (stock == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val vm: ThemeCustomizeViewModel = viewModel(
        key = "themeCustomize/${stock.id}",
        factory = ThemeCustomizeViewModel.factory(container.settings, stock),
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val name = stringResource(stock.nameRes)
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.ed_back))
                }
                Text(name, style = MaterialTheme.typography.headlineMedium)
            }
            state?.let { loaded ->
                ThemeCustomizeBody(
                    loaded,
                    name,
                    twoColumns = widthClass != WindowWidthSizeClass.Compact,
                    onAnimated = vm::setAnimated,
                    onReset = vm::reset,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ThemeCustomizeBody(
    state: ThemeCustomizeUiState,
    name: String,
    twoColumns: Boolean,
    onAnimated: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Follows the app's mode until the user picks one here; picking never changes the app's mode.
    var pickedDark by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val appDark = when (state.darkMode) {
        DarkMode.DARK -> true
        DarkMode.LIGHT -> false
        DarkMode.SYSTEM -> isSystemInDarkTheme()
    }
    val dark = pickedDark ?: appDark
    val spacing = Arrangement.spacedBy(12.dp)
    if (twoColumns) {
        Row(modifier.fillMaxWidth(), horizontalArrangement = spacing) {
            ThemePreview(state.theme, dark, name, Modifier.weight(1f).fillMaxHeight())
            ThemeControls(state, name, dark, { pickedDark = it }, onAnimated, onReset, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier.fillMaxWidth(), verticalArrangement = spacing) {
            ThemePreview(state.theme, dark, name, Modifier.weight(1f).fillMaxWidth())
            ThemeControls(state, name, dark, { pickedDark = it }, onAnimated, onReset, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

/** A real Daily page from the app's sample in the edited theme. Input is blocked and TalkBack hears one description. */
@Composable
private fun ThemePreview(theme: AppTheme, dark: Boolean, name: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val description = stringResource(R.string.theme_customize_preview_desc, name)
    val sample = remember { sampleDailyUiState() }
    val actions = remember { DailyActions() }
    Box(
        modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        CommentariiTheme(dark = dark, theme = theme, animate = false) {
            DailyPageContent(sample, actions, WindowWidthSizeClass.Compact)
        }
        Box(
            Modifier.matchParentSize().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeControls(
    state: ThemeCustomizeUiState,
    name: String,
    dark: Boolean,
    onDark: (Boolean) -> Unit,
    onAnimated: (Boolean) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.theme_customize_preview_mode), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = !dark,
                onClick = { onDark(false) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.settings_theme_light)) }
            SegmentedButton(
                selected = dark,
                onClick = { onDark(true) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.settings_theme_dark)) }
        }
        if (state.theme.animated) {
            Text(stringResource(R.string.theme_customize_animation), style = MaterialTheme.typography.titleSmall)
            ThemeAnimatedChoice(state.animatedOn, onAnimated)
        }
        OutlinedButton(onClick = { confirmReset = true }, enabled = state.canReset) {
            Text(stringResource(R.string.theme_customize_reset))
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.theme_customize_reset_title, name)) },
            text = {
                val body = if (state.animatedOn) R.string.theme_customize_reset_body else R.string.theme_customize_reset_body_animated
                Text(stringResource(body))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    onReset()
                }) { Text(stringResource(R.string.theme_customize_reset_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}
