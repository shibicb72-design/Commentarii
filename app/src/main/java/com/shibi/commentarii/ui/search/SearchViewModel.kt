package com.shibi.commentarii.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.data.PageEntity
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SearchResult(
    val id: Long,
    val type: String,
    val title: String,
    val date: LocalDate? = null,
    val pinned: Boolean = false,
)

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    /** True once a search for the current query has completed (distinguishes "no results" from "not searched"). */
    val searched: Boolean = false,
)

class SearchViewModel(container: AppContainer) : ViewModel() {
    private val pages = container.pages
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onQuery(query: String) {
        _state.update { it.copy(query = query, searched = false) }
        job?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(results = emptyList(), searched = false) }
            return
        }
        job = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            val found = try {
                pages.search(query).map(::toResult)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            _state.update { if (it.query == query) it.copy(results = found, searched = true) else it }
        }
    }

    private fun toResult(p: PageEntity) = SearchResult(p.id, p.type, p.title, p.date, p.pinned)

    companion object {
        private const val DEBOUNCE_MS = 250L

        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SearchViewModel(container) as T
        }
    }
}
