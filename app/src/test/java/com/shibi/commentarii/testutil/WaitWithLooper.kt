package com.shibi.commentarii.testutil

import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeTestRule
import org.robolectric.Shadows.shadowOf

/**
 * Use this instead of [ComposeTestRule.waitUntil] when the condition depends on Room, DataStore or a view model. Under
 * Robolectric, waitUntil only advances the compose clock and never drains the main looper, so a continuation posted back
 * to Dispatchers.Main (viewModelScope) does not run and the wait times out, unless the work happened to finish earlier.
 */
fun ComposeTestRule.waitUntilWithLooper(timeoutMillis: Long = 10_000, condition: () -> Boolean) {
    waitUntil(timeoutMillis) {
        shadowOf(Looper.getMainLooper()).idle()
        condition()
    }
}
