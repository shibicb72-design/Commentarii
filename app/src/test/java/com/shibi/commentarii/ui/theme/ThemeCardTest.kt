package com.shibi.commentarii.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCardTest {
    private val dailyTitles =
        listOf("Today's Top Priorities", "To-Do List", "Schedule", "Don't Forget", "Today's Mood", "Notes")
    private val ids = listOf(
        "washi_planner", "sunny_journal", "cloud_drift", "petal_fall", "floating_hearts", "matcha_tea", "lavender_moon",
    )

    @Test
    fun dailyTitlesGetDistinctStickersPerTheme() {
        for (id in ids) {
            val art = themeArt(id)!!
            val doodles = dailyTitles.map { cardDecor(id, art, art.light, it).doodle }
            assertEquals("$id: $doodles", 6, doodles.toSet().size)
            assertTrue(doodles.all { it in cornerDoodles(id) })
        }
    }

    @Test
    fun cardDecorIsDeterministic() {
        val art = themeArt("washi_planner")!!
        val a = cardDecor("washi_planner", art, art.light, "Schedule")
        val b = cardDecor("washi_planner", art, art.light, "Schedule")
        assertEquals(a.doodle, b.doodle)
        assertEquals(a.tape, b.tape)
        assertEquals(a.accent, b.accent)
    }

    @Test
    fun roseCreamHasNoCornerStickers() {
        assertTrue(cornerDoodles("rose_cream").isEmpty())
    }
}
