package com.shibi.commentarii.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Test

class NavRestoreTest {
    @Test
    fun encodeDecodeRoundTrip() {
        val routes = listOf("daily/2026-10-17", "notes", "note/5")
        assertEquals(routes, NavRestore.decode(NavRestore.encode(routes)))
    }

    @Test
    fun emptyRestoresNothing() {
        assertEquals(emptyList<String>(), NavRestore.decode(""))
        assertEquals(emptyList<RestoreStep>(), NavRestore.plan(emptyList()))
    }

    @Test
    fun malformedRoutesAreDropped() {
        val routes = listOf("daily/not-a-date", "note/abc", "bogus", "settings", "daily/2026-10-18", "note/{id}")
        assertEquals(listOf("settings", "daily/2026-10-18"), NavRestore.sanitize(routes))
    }

    @Test
    fun planReplacesStartWhenFirstIsDaily() {
        val plan = NavRestore.plan(listOf("daily/2026-10-01", "note/9"))
        assertEquals(
            listOf(RestoreStep("daily/2026-10-01", true), RestoreStep("note/9", false)),
            plan,
        )
    }

    @Test
    fun planPushesWhenFirstIsNotDaily() {
        assertEquals(listOf(RestoreStep("settings", false)), NavRestore.plan(listOf("settings")))
    }
}
