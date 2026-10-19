package com.shibi.commentarii.debug

import java.io.File
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebugLogTest {
    private lateinit var dir: File
    private val clock = Clock.fixed(Instant.parse("2026-10-18T09:00:00.123Z"), ZoneOffset.UTC)

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("dbglog").toFile()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun lineFormatAndPersistence() {
        val log = DebugLog(dir, clock)
        log.log("TAG", "hello")
        assertEquals(listOf("2026-10-18 09:00:00.123 TAG hello"), log.lines())
        assertEquals(listOf("2026-10-18 09:00:00.123 TAG hello"), DebugLog(dir, clock).lines())
    }

    @Test
    fun keepsOnlyLast500LinesOldestDropped() {
        val log = DebugLog(dir, clock)
        repeat(520) { log.log("T", "m$it") }
        val lines = log.lines()
        assertEquals(500, lines.size)
        assertTrue(lines.first().endsWith("m20"))
        assertTrue(lines.last().endsWith("m519"))
        assertEquals(500, File(dir, DebugLog.FILE_NAME).readLines().size)
        assertEquals(500, DebugLog(dir, clock).lines().size)
    }

    @Test
    fun clearEmptiesAndBumpsVersion() {
        val log = DebugLog(dir, clock)
        log.log("T", "a")
        val v = log.version.value
        log.clear()
        assertEquals("", log.readAll())
        assertTrue(log.version.value > v)
        assertTrue(DebugLog(dir, clock).lines().isEmpty())
    }
}
