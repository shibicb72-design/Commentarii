package com.shibi.commentarii.testutil

import android.os.Looper
import androidx.compose.ui.test.junit4.ComposeTestRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
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

/**
 * Use this instead of runBlocking for settings work while a page is on screen. DataStore runs an edit's transform on its
 * caller's context, so a view model's edit needs the main thread; runBlocking on that thread behind it in the store's queue
 * never returns. This runs [block] off the main thread and keeps the main looper going until it is done.
 */
fun <T> runWithLooper(timeoutMillis: Long = 10_000, block: suspend () -> T): T {
    val work = CoroutineScope(Dispatchers.IO).async { block() }
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (!work.isCompleted) {
        check(System.currentTimeMillis() < deadline) { "Timed out waiting for background work" }
        shadowOf(Looper.getMainLooper()).idle()
        Thread.sleep(5)
    }
    return runBlocking { work.await() }
}
