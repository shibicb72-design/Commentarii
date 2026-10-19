package com.shibi.commentarii.ui.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Debounced writer for the week and month pages: edits mark a section key dirty and [write] persists it. The scope outlives
 * the ViewModel so the last edits are still written after the user navigates away. Nothing is written before [ready].
 */
class Autosaver(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val write: suspend (key: String) -> Unit,
) {
    private val dirty = HashSet<String>()
    private val mutex = Mutex()
    private var debounceJob: Job? = null

    @Volatile
    var ready = false
        private set

    /** Allows writes and flushes whatever was marked dirty before the initial load finished. */
    fun markReady() {
        ready = true
        val pending = synchronized(dirty) { dirty.isNotEmpty() }
        if (pending) flushNow()
    }

    fun markDirty(key: String) {
        synchronized(dirty) { dirty += key }
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            withContext(NonCancellable) { flush() }
        }
    }

    /** Writes pending edits now (e.g. when the app goes to the background or the ViewModel is cleared). */
    fun flushNow() {
        debounceJob?.cancel()
        scope.launch { flush() }
    }

    /** Final flush (ViewModel cleared); the scope is cancelled once it completes. */
    fun close() {
        debounceJob?.cancel()
        scope.launch {
            try {
                flush()
            } finally {
                scope.cancel()
            }
        }
    }

    private suspend fun flush() = mutex.withLock {
        if (!ready) return@withLock
        val keys = synchronized(dirty) { dirty.toList().also { dirty.clear() } }
        for (key in keys) {
            try {
                write(key)
            } catch (e: CancellationException) {
                synchronized(dirty) { dirty += key }
                throw e
            } catch (_: Exception) {
                synchronized(dirty) { dirty += key }
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 500L
    }
}
