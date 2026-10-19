package com.shibi.commentarii.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private fun ratio(a: Color, b: Color): Float {
        val l1 = maxOf(a.luminance(), b.luminance())
        val l2 = minOf(a.luminance(), b.luminance())
        return (l1 + 0.05f) / (l2 + 0.05f)
    }

    private fun check(label: String, fg: Color, bg: Color, min: Float, failures: MutableList<String>) {
        val r = ratio(fg, bg)
        if (r < min) failures += "$label %.2f < $min".format(r)
    }

    @Test
    fun bodyAndTitlePairsMeetContrast() {
        val failures = mutableListOf<String>()
        for (theme in AppThemes.all) {
            for ((modeName, mode) in listOf("light" to theme.light, "dark" to theme.dark)) {
                val s = mode.scheme
                val e = mode.extras
                val tag = "${theme.id}/$modeName"
                check("$tag onBg/bg", s.onBackground, s.background, 4.5f, failures)
                check("$tag onCard/card", e.onCard, e.card, 4.5f, failures)
                check("$tag onPrimary/primary", s.onPrimary, s.primary, 4.5f, failures)
                e.tabs.forEachIndexed { i, tab -> check("$tag onTab/tab$i", e.onTab, tab, 4.5f, failures) }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun titleGradientStopsReadAgainstBackground() {
        val failures = mutableListOf<String>()
        for (theme in AppThemes.all) {
            val specs = listOf(
                "light" to (theme.light to titleStops(theme.id, dark = false)),
                "dark" to (theme.dark to titleStops(theme.id, dark = true)),
            )
            for ((modeName, pair) in specs) {
                val (mode, stops) = pair
                stops.forEachIndexed { i, c -> check("${theme.id}/$modeName title$i", c, mode.scheme.background, 3f, failures) }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    // Brush stops are not introspectable, so the palette hex values are mirrored here from the plan.
    private fun titleStops(id: String, dark: Boolean): List<Color> = when (id to dark) {
        "washi_planner" to false -> listOf(0xFFC2477A, 0xFF7E5BB5, 0xFF2E8A66, 0xFF9A6A0A, 0xFF3B7BB8)
        "washi_planner" to true -> listOf(0xFFF7B9D0, 0xFFCDBDF5, 0xFFAEE3C8, 0xFFF6E3A0, 0xFFB5D8F5)
        "sunny_journal" to false -> listOf(0xFFC4513A, 0xFF9C6F00, 0xFF3E8E4F, 0xFF3A6FB8, 0xFF7B5AB8)
        "sunny_journal" to true -> listOf(0xFFFFC9A0, 0xFFF3E58A, 0xFFB6E8A8, 0xFFA8C8F5, 0xFFCBBEF5)
        "lavender_moon" to false -> listOf(0xFF6A5AA8, 0xFF3F5A9E)
        "lavender_moon" to true -> listOf(0xFFC3B6F2, 0xFFB5C8F5)
        else -> emptyList()
    }.map { Color(it) }

    @Test
    fun idsAreUniqueAndLookupFallsBack() {
        val ids = AppThemes.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals(AppThemes.Default, AppThemes.byId("nope"))
        assertEquals(AppThemes.Default, AppThemes.byId(null))
        assertTrue(AppThemes.all.count { it.animated } <= 3)
    }
}
