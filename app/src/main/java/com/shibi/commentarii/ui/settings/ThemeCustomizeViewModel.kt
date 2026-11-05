package com.shibi.commentarii.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.data.SettingsRepository
import com.shibi.commentarii.ui.theme.AppTheme
import com.shibi.commentarii.ui.theme.ThemeOverrideCodec
import com.shibi.commentarii.ui.theme.customized
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ThemeCustomizeUiState(
    /** The theme with this user's edits applied. */
    val theme: AppTheme,
    val darkMode: DarkMode,
    val animatedOn: Boolean,
    /** A stored override, even one that resolves to stock (corrupt or empty), so Reset can always clear it. */
    val hasOverride: Boolean,
) {
    val canReset: Boolean get() = hasOverride || !animatedOn
}

class ThemeCustomizeViewModel(private val settings: SettingsRepository, private val stock: AppTheme) : ViewModel() {
    private val override = settings.themeOverrides.map { it[stock.id] }.distinctUntilChanged()
        .map { json -> stock.customized(ThemeOverrideCodec.decode(json)) to (json != null) }

    /** Null until the stores answer, so the preview never flashes the stock look over the user's edits. */
    val state: StateFlow<ThemeCustomizeUiState?> =
        combine(override, settings.staticThemeIds, settings.darkMode) { (theme, hasOverride), staticIds, darkMode ->
            ThemeCustomizeUiState(theme, darkMode, animatedOn = stock.id !in staticIds, hasOverride = hasOverride)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setAnimated(on: Boolean) {
        viewModelScope.launch { settings.setThemeAnimated(stock.id, on) }
    }

    fun reset() {
        viewModelScope.launch { settings.resetThemeCustomization(stock.id) }
    }

    companion object {
        fun factory(settings: SettingsRepository, stock: AppTheme): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ThemeCustomizeViewModel(settings, stock) as T
            }
    }
}
