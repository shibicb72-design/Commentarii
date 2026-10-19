package com.shibi.commentarii.ui.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutosaverTest {
    private val written = mutableListOf<String>()
    private var failNext = false

    private fun TestScope.saver(): Pair<Autosaver, Job> {
        val job = SupervisorJob()
        val saver = Autosaver(CoroutineScope(job + StandardTestDispatcher(testScheduler))) { key ->
            if (failNext) {
                failNext = false
                throw IllegalStateException("disk full")
            }
            written += key
        }
        return saver to job
    }

    @Test
    fun nothingIsWrittenBeforeReadyThenReadyFlushesPendingKeys() = runTest {
        val (saver, _) = saver()
        saver.markDirty("a")
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(written.isEmpty())
        saver.markReady()
        runCurrent()
        assertEquals(listOf("a"), written)
    }

    @Test
    fun editsAreDebouncedIntoOneWrite() = runTest {
        val (saver, _) = saver()
        saver.markReady()
        saver.markDirty("a")
        advanceTimeBy(300)
        saver.markDirty("a")
        advanceTimeBy(300)
        runCurrent()
        assertTrue(written.isEmpty())
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("a"), written)
    }

    @Test
    fun flushNowWritesWithoutWaitingForTheDebounce() = runTest {
        val (saver, _) = saver()
        saver.markReady()
        saver.markDirty("a")
        saver.markDirty("b")
        saver.flushNow()
        runCurrent()
        assertEquals(listOf("a", "b"), written.sorted())
    }

    @Test
    fun failedWriteIsRetriedOnTheNextFlush() = runTest {
        val (saver, _) = saver()
        saver.markReady()
        failNext = true
        saver.markDirty("a")
        advanceTimeBy(600)
        runCurrent()
        assertTrue(written.isEmpty())
        saver.flushNow()
        runCurrent()
        assertEquals(listOf("a"), written)
    }

    @Test
    fun closeFlushesThenCancelsTheScope() = runTest {
        val (saver, job) = saver()
        saver.markReady()
        saver.markDirty("a")
        saver.close()
        runCurrent()
        assertEquals(listOf("a"), written)
        assertTrue(job.isCancelled)
    }
}
