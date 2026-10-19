package com.shibi.commentarii.ui.lock

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {
    @Test
    fun startsLockedAndUnlocks() {
        val lock = AppLock(nowMs = { 0L })
        assertTrue(lock.locked.value)
        lock.unlock()
        assertFalse(lock.locked.value)
    }

    @Test
    fun relocksOnlyAfterMoreThanSixtySeconds() {
        var t = 0L
        val lock = AppLock(nowMs = { t })
        lock.unlock()
        lock.onBackground()
        t = 60_000
        lock.onForeground()
        assertFalse(lock.locked.value)
        lock.onBackground()
        t += 60_001
        lock.onForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun foregroundWithoutBackgroundKeepsState() {
        val lock = AppLock(nowMs = { 1_000_000L })
        lock.unlock()
        lock.onForeground()
        assertFalse(lock.locked.value)
    }
}
