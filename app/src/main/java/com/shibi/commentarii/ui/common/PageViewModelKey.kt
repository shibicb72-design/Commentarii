package com.shibi.commentarii.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shibi.commentarii.AppContainer

/**
 * The view model key of a screen that holds pages. An import replaces every row, but navigation keeps view models, and one from
 * before the import would go on showing the old data and save it over the restored data. A new key gets a new view model.
 */
@Composable
fun AppContainer.pageViewModelKey(base: String): String {
    val imports by imports.collectAsStateWithLifecycle()
    return if (imports == 0) base else "$base-import$imports"
}
