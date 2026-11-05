package com.shibi.commentarii.ui.theme

import android.graphics.Bitmap
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.graphics.toArgb
import com.shibi.commentarii.R
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.domain.weekStart
import com.shibi.commentarii.testutil.ThemeFlowTestBase
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checklist #262 on the real MainActivity: every theme, light and dark, on Today, Week, Month, Notes and Search. The theme is
 * chosen through the real settings store (the Settings radio is covered by [ThemeSettingsFlowTest]); animated themes are set to
 * "Off" so their designed still frame is what is measured (their loop is the subject of [ThemeAnimatedFlowTest]).
 */
class ThemePagesTest : ThemeFlowTestBase() {
    private val today: LocalDate get() = LocalDate.now(container.clock)

    private class Page(val label: Int, val ready: String)

    private val pages = listOf(
        Page(R.string.tab_today, "Page todo"),
        Page(R.string.tab_week, "Page week goal"),
        Page(R.string.tab_month, "Page month goal"),
        Page(R.string.tab_notes, "Page note"),
        Page(R.string.tab_search, "Page note"),
    )

    private fun seed() = runBlocking {
        val daily = container.pages.getOrCreateDaily(today).id
        val weekly = container.pages.getOrCreateWeekly(weekStart(today)).id
        val monthly = container.pages.getOrCreateMonthly(YearMonth.from(today).atDay(1)).id
        suspend fun tasks(id: Long, section: String, text: String) =
            container.pages.saveTasks(id, section, listOf(TaskItemEntity(pageId = id, section = section, position = 0, text = text)))
        tasks(daily, TaskSection.TODO, "Page todo")
        tasks(weekly, TaskSection.GOALS, "Page week goal")
        tasks(monthly, TaskSection.GOALS, "Page month goal")
        val note = container.pages.createNote("Page note").id
        container.pages.saveBlockDocument(note, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(text = "page note body"))))
    }

    private fun show(page: Page) {
        openTab(page.label)
        if (page.label == R.string.tab_search) {
            await { exists(hasSetTextAction()) }
            rule.onNode(hasSetTextAction()).performTextClearance()
            rule.onNode(hasSetTextAction()).performTextInput("Page")
            rule.waitUntilSearchShows("Page note")
        } else {
            await { exists(page.ready) }
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeTestRule.waitUntilSearchShows(text: String) {
        waitUntil(10_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(50))
            onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
        waitForIdle()
    }

    private fun barTop() = boundsOf(tab(R.string.tab_settings)).top

    private fun collectReadings(page: Page): List<Reading> {
        seed()
        val all = mutableListOf<Reading>()
        for (theme in AppThemes.all) for (dark in listOf(false, true)) {
            applyTheme(theme, dark)
            show(page)
            val found = readings("${theme.id}/${if (dark) "dark" else "light"}/${str(page.label)}", barTop())
            assertTrue("no text found on ${str(page.label)}", found.isNotEmpty())
            all += found
        }
        return all
    }

    /**
     * Checklist #262, the readable-text clause: no text on the page falls under its WCAG threshold (4.5:1, or 3:1 for large text),
     * in any theme or mode.
     */
    private fun assertEveryThemeReadableOn(page: Page) {
        val all = collectReadings(page)
        val failures = all.filterNot { it.ok }.map { it.toString() }
        println("item262 ${str(page.label)}: ${all.size} texts measured, ${failures.size} below threshold")
        assertTrue("${failures.size} of ${all.size} texts below their WCAG threshold:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun item262_todayTextReadableInEveryThemeAndMode() = assertEveryThemeReadableOn(pages[0])

    @Test fun item262_weekTextReadableInEveryThemeAndMode() = assertEveryThemeReadableOn(pages[1])

    @Test fun item262_monthTextReadableInEveryThemeAndMode() = assertEveryThemeReadableOn(pages[2])

    @Test fun item262_notesTextReadableInEveryThemeAndMode() = assertEveryThemeReadableOn(pages[3])

    @Test fun item262_searchTextReadableInEveryThemeAndMode() = assertEveryThemeReadableOn(pages[4])

    /**
     * Checklist #262: the "Dismiss" button of the export reminder on Today is measured on the banner itself. Found failing at 3.08:1 to
     * 4.12:1 in 15 of 16 theme/mode combinations, when it was primary-coloured text on the primaryContainer.
     */
    @Test
    fun item262_todayReminderDismissButtonReadable() {
        val dismiss = collectReadings(pages[0]).filter { it.text == "Dismiss" }
        assertTrue("the reminder's Dismiss button was measured", dismiss.isNotEmpty())
        val failures = dismiss.filterNot { it.ok }.map { it.toString() }
        assertTrue("${failures.size} of ${dismiss.size} Dismiss buttons below 4.5:1:" + failures.joinToString(prefix = " ", separator = "; "), failures.isEmpty())
    }

    /**
     * Checklist #262, the "colours change" clause: on every page, each theme's window has its own paper colour (the scheme's
     * background, different from every other theme's in that mode) and a different overall picture from Rose & Cream's.
     */
    @Test
    fun item262_eachThemeRepaintsEveryPage() {
        seed()
        for (page in pages) {
            for (dark in listOf(false, true)) {
                val shots = mutableMapOf<String, Bitmap>()
                for (theme in AppThemes.all) {
                    applyTheme(theme, dark)
                    show(page)
                    val bitmap = windowBitmap()
                    val mode = if (dark) theme.dark else theme.light
                    assertEquals("${theme.id} bar on ${str(page.label)}", hex(mode.scheme.surfaceContainer.toArgb()), hex(barBackgroundPixel(bitmap)))
                    shots[theme.id] = bitmap
                }
                val papers = AppThemes.all.map { (if (dark) it.dark else it.light).scheme.background.toArgb() }
                assertEquals("every theme has its own paper colour (dark=$dark)", papers.size, papers.toSet().size)
                val base = shots.getValue(AppThemes.Default.id)
                for (theme in AppThemes.all.drop(1)) {
                    val changed = differing(base, shots.getValue(theme.id))
                    assertTrue("${theme.id} (dark=$dark) changes the ${str(page.label)} page: $changed of ${base.width * base.height} pixels", changed > base.width * base.height / 10)
                }
            }
        }
    }

    private fun differing(a: Bitmap, b: Bitmap): Int {
        var n = 0
        for (y in 0 until a.height) for (x in 0 until a.width) if (a.getPixel(x, y) != b.getPixel(x, y)) n++
        return n
    }
}
