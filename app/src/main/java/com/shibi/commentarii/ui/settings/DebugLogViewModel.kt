package com.shibi.commentarii.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.shibi.commentarii.debug.DebugLog

/** Reads, copies and clears the local debug log. The UI re-reads [lines] when `debugLog.version` changes. */
class DebugLogViewModel(private val debugLog: DebugLog) : ViewModel() {
    val version get() = debugLog.version

    fun lines(): List<String> = debugLog.lines()

    /** Exactly what "Copy all" puts on the clipboard. */
    fun copyText(): String = debugLog.readAll()

    fun clear() = debugLog.clear()

    companion object {
        fun factory(debugLog: DebugLog): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = DebugLogViewModel(debugLog) as T
        }
    }
}
