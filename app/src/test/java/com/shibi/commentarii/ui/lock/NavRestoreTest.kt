package com.shibi.commentarii.ui.lock

import com.shibi.commentarii.ui.Routes
import com.shibi.commentarii.ui.theme.AppThemes
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

    @Test
    fun debugRouteSurvivesRestore() {
        val routes = listOf("daily/2026-10-18", "settings", "settings/debug")
        assertEquals(routes, NavRestore.sanitize(routes))
        assertEquals(routes, NavRestore.decode(NavRestore.encode(routes)))
    }

    @Test
    fun weekAndMonthRoutesAreAccepted() {
        val routes = listOf("daily/2026-10-18", "week/2026-10-12", "month/2026-10")
        assertEquals(routes, NavRestore.sanitize(routes))
        assertEquals(routes, NavRestore.decode(NavRestore.encode(routes)))
        assertEquals(listOf(RestoreStep("week/2026-10-12", false)), NavRestore.plan(listOf("week/2026-10-12")))
    }

    @Test
    fun malformedWeekAndMonthRoutesAreDropped() {
        val routes = listOf("week/", "week/nope", "week/{mondayDate}", "month/2026-13", "month/2026-1", "month/{yearMonth}", "month/")
        assertEquals(emptyList<String>(), NavRestore.sanitize(routes))
    }

    @Test
    fun themeCustomizeRouteIsKeptForEveryThemeInOrder() {
        val ids = listOf("rose_cream", "washi_planner", "sunny_journal", "cloud_drift", "petal_fall", "floating_hearts", "matcha_tea", "lavender_moon")
        assertEquals("the list covers every theme, in the app's order", AppThemes.all.map { it.id }, ids)
        val routes = ids.map { Routes.themeCustomize(it) }
        assertEquals(ids.map { "settings/theme/$it" }, routes)
        assertEquals(routes, NavRestore.sanitize(routes))
        assertEquals(routes, NavRestore.decode(NavRestore.encode(routes)))
    }

    @Test
    fun themeCustomizeRouteRestoresOnTopOfSettings() {
        val routes = listOf("daily/2026-10-18", "settings", "settings/theme/petal_fall")
        assertEquals(routes, NavRestore.decode(NavRestore.encode(routes)))
        assertEquals(
            listOf(RestoreStep("daily/2026-10-18", true), RestoreStep("settings", false), RestoreStep("settings/theme/petal_fall", false)),
            NavRestore.plan(routes),
        )
    }

    @Test
    fun themeCustomizeRouteWithAnUnknownOrEmptyIdIsDropped() {
        val bad = listOf(
            "settings/theme/",
            "settings/theme",
            "settings/theme/nope",
            "settings/theme/{id}",
            "settings/theme/ROSE_CREAM",
            "settings/theme/rose_cream/extra",
            "settings/theme/rose_cream ",
        )
        assertEquals(emptyList<String>(), NavRestore.sanitize(bad))
        assertEquals(
            "only the bad ones go",
            listOf("settings", "settings/theme/washi_planner"),
            NavRestore.sanitize(listOf("settings", "settings/theme/nope", "settings/theme/washi_planner", "settings/theme/")),
        )
    }
}
