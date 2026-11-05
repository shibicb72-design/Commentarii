package com.shibi.commentarii.ui.search

import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.R
import com.shibi.commentarii.data.BlockDocument
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.PeriodPageTestBase
import com.shibi.commentarii.ui.Routes
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf

/**
 * Checklist #220, #221, #225, #226, #229, #230, #237, #238, #239, #240: Search as a user meets it, inside the real AppNav
 * (real SearchScreen, view model, Room and FTS). The search tab is reached by tapping it. Pages other than the current day, week
 * and month are reached through AppNav's fixed clock, which only decides where the Today, Week and Month tabs go.
 * The view model's debounce is a delay on the main looper, so it is waited out by moving the looper's clock, 50 ms at a time.
 */
class SearchAppFlowTest : PeriodPageTestBase() {
    private val hint get() = str(R.string.sr_hint)
    private val prompt get() = str(R.string.sr_prompt)
    private val clear get() = str(R.string.sr_clear)
    private val nothingFound get() = str(R.string.sr_no_results)
    private val noteLabel get() = str(R.string.sr_type_note)
    private val dailyLabel get() = str(R.string.sr_type_daily)
    private val weekLabel get() = str(R.string.sr_type_week)
    private val monthLabel get() = str(R.string.sr_type_month)
    private val pinnedLabel get() = str(R.string.nt_pinned)
    private val untitled get() = str(R.string.nt_untitled)
    private val dailyTitleFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

    // ---- Driving the screen ----

    private val queryField = hasSetTextAction()

    private fun openSearch() {
        openApp()
        goToSearchTab()
    }

    private fun goToSearchTab() {
        tapTab(R.string.tab_search)
        await { routes.lastOrNull() == Routes.SEARCH && exists(queryField) }
    }

    private fun typeQuery(query: String) {
        val field = rule.onNode(queryField)
        field.performTextClearance()
        field.performTextInput(query)
    }

    private fun queryShown() = rule.onNode(queryField).fetchSemanticsNode().config[SemanticsProperties.EditableText].text

    /** Moves the looper's clock along until [condition] holds: the debounce is a delay on it, which waitUntil alone never passes. */
    private fun awaitSearch(condition: () -> Boolean) {
        rule.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            condition()
        }
        rule.waitForIdle()
    }

    private fun card(title: String, label: String) = hasText(title) and hasContentDescription(label)

    private val anyNode = SemanticsMatcher("any node") { true }
    private val resultLabels get() = listOf(noteLabel, dailyLabel, weekLabel, monthLabel)

    /** The result cards on screen, top to bottom, each as "type label: title". */
    private fun resultCards(): List<String> = rule.onAllNodes(hasClickAction() and hasAnyDescription(resultLabels))
        .fetchSemanticsNodes().sortedBy { it.positionInRoot.y }
        .map { "${it.descriptions().first()}: ${it.texts().first()}" }

    private fun hasAnyDescription(labels: List<String>) = SemanticsMatcher("has one of $labels") { node ->
        node.descriptions().any { it in labels }
    }

    private fun SemanticsNode.role(): Role? = if (SemanticsProperties.Role in config) config[SemanticsProperties.Role] else null

    private fun SemanticsNode.texts() = config.getOrElse(SemanticsProperties.Text) { emptyList() }.map { it.text }

    private fun SemanticsNode.descriptions() = config.getOrElse(SemanticsProperties.ContentDescription) { emptyList() }

    private fun tapCard(title: String, label: String) {
        rule.onNode(card(title, label)).performClick()
        rule.waitForIdle()
    }

    // ---- Seeding what is not the item's subject ----

    private fun seedNote(title: String, body: String, pinned: Boolean = false): Long = runBlocking {
        val pages = container.pages
        val id = pages.createNote(title).id
        pages.saveBlockDocument(id, SectionKind.RICH_TEXT, BlockDocument(listOf(DocBlock(text = body))))
        if (pinned) pages.setPinned(id, true)
        // updatedAt has millisecond precision, and the order of the results depends on it.
        Thread.sleep(5)
        id
    }

    private fun found(word: String) = runBlocking { container.pages.search(word) }.isNotEmpty()

    // ---- 220 ----

    /** Ink on the window within [area] of the field, counted against the area's own top-left pixel. */
    private fun inkMap(area: Rect): List<String> {
        val bitmap = windowBitmap()
        val background = bitmap.getPixel(area.left.toInt(), area.top.toInt())
        return (area.top.toInt() until area.bottom.toInt()).map { y ->
            (area.left.toInt() until area.right.toInt()).joinToString("") { x -> if (bitmap.getPixel(x, y) != background) "#" else "." }
        }
    }

    private fun dpRect(field: Rect, left: Float, top: Float, right: Float, bottom: Float) =
        Rect(field.left + dp(1) * left, field.top + dp(1) * top, field.left + dp(1) * right, field.top + dp(1) * bottom)

    @Test
    fun item220_emptySearchShowsHintPromptAndNoClearButtonThenClearAppearsOnceTyped() {
        openSearch()
        settleAnimations()

        val field = rule.onNode(queryField)
        assertTrue("placeholder in the field", exists(hasText(hint) and queryField))
        assertEquals("Search days and notes", hint)
        assertEquals("", queryShown())
        assertTrue("prompt below it", exists(hasText(prompt)))
        assertEquals("Type to search your days and notes.", prompt)
        assertTrue("prompt is below the field", bounds(rule.onNode(hasText(prompt))).top > bounds(field).bottom)
        assertFalse("no clear button while empty", exists(hasContentDescription(clear)))

        // The search icon has no description, so it is looked for in the pixels of the field's leading area: a magnifier is a
        // ring with a handle toward the lower right, which leaves the other two corners of its square empty.
        val box = bounds(field)
        val fieldHeight = box.height / dp(1)
        val leading = dpRect(box, 12f, (fieldHeight - 24f) / 2, 36f, (fieldHeight + 24f) / 2)
        val glyph = inkMap(leading)
        val ink = glyph.sumOf { row -> row.count { it == '#' } }
        val density = dp(1)
        assertTrue("a glyph is drawn in the leading slot, ink $ink\n${glyph.joinToString("\n")}", ink > 60 * density)
        fun cornerInk(x0: Int, y0: Int, x1: Int, y1: Int) = glyph.subList((y0 * density).toInt(), (y1 * density).toInt())
            .sumOf { row -> row.substring((x0 * density).toInt(), (x1 * density).toInt()).count { it == '#' } }
        assertTrue("handle at the lower right\n${glyph.joinToString("\n")}", cornerInk(16, 16, 22, 22) > 0)
        assertEquals("top right corner empty\n${glyph.joinToString("\n")}", 0, cornerInk(18, 2, 24, 6))
        assertEquals("bottom left corner empty\n${glyph.joinToString("\n")}", 0, cornerInk(2, 18, 6, 24))
        // Control for the pixel reading: the trailing slot is empty now and is drawn on once the clear button is there.
        val trailing = dpRect(box, fieldHeight * 0f + box.width / dp(1) - 40f, (fieldHeight - 24f) / 2, box.width / dp(1) - 16f, (fieldHeight + 24f) / 2)
        assertEquals("trailing slot is empty while the field is", 0, inkMap(trailing).sumOf { row -> row.count { it == '#' } })

        typeQuery("a")
        await { exists(hasContentDescription(clear)) }
        settleAnimations()
        assertTrue("control: the clear button is there once something is typed", exists(hasContentDescription(clear)))
        assertTrue("control: and the same reading sees it", inkMap(trailing).sumOf { row -> row.count { it == '#' } } > 30 * density)
    }

    // ---- 221 ----

    @Test
    fun item221_aWordInANotesBodyGivesANoteCardWithTheNotesTitle() {
        seedNote("Aquarium", "a zebrafish swims here")
        seedNote("Garage", "nothing about fish")
        openSearch()

        typeQuery("zebrafish")
        awaitSearch { exists(card("Aquarium", noteLabel)) }

        assertEquals("Note", noteLabel)
        assertEquals("one card, the matching note", listOf("Note: Aquarium"), resultCards())
        assertFalse("the other note is not a result", exists(hasText("Garage")))
    }

    @Test
    fun item221_aNoteWithNoTitleIsShownAsUntitled() {
        seedNote("", "a quokka naps here")
        openSearch()

        typeQuery("quokka")
        awaitSearch { exists(card(untitled, noteLabel)) }

        assertEquals("Untitled", untitled)
        assertEquals(listOf("Note: Untitled"), resultCards())
    }

    /**
     * The looper's clock stands in for time here; the view model's delay is on it, not on the wall clock. The debounce starts when the
     * query reaches the view model, so that moment is recorded where it happens: typing itself moves the clock (the field's focus
     * animation runs first), so a time read before typing is not the debounce's start. Real view model and Room behind the real content.
     *
     * Looking at the UI moves the clock too, a frame per look, and Room answers on a real thread while the clock stands still. So the
     * edges of the debounce are judged on the view model's state, with the clock moved only by exact amounts and held there.
     */
    @Test
    fun item221_theResultComesOnlyAfterTheDebounce() {
        seedNote("Orchard", "a persimmon tree")
        var queriedAt = -1L
        lateinit var vm: SearchViewModel
        rule.setContent {
            CommentariiTheme(dark = false) {
                vm = viewModel(factory = SearchViewModel.factory(container))
                val state by vm.state.collectAsStateWithLifecycle()
                SearchContent(
                    state,
                    { query ->
                        queriedAt = SystemClock.uptimeMillis()
                        vm.onQuery(query)
                    },
                    { _, _, _ -> },
                )
            }
        }
        await { exists(hasText(prompt)) }

        rule.onNode(queryField).performTextInput("persimmon")
        rule.waitForIdle()
        assertTrue("the typing reached the view model", queriedAt >= 0)
        assertEquals("the query is in the field", "persimmon", queryShown())
        assertFalse("no result on the typed frame", exists(card("Orchard", noteLabel)))
        assertFalse("and no prompt or message either: it is waiting", exists(hasText(prompt)) || exists(hasText(nothingFound)))

        val looper = shadowOf(Looper.getMainLooper())
        val sinceQuery = { SystemClock.uptimeMillis() - queriedAt }

        // 1 ms short of the debounce. A search started by now would have been queued on Room before this one, so this one is the barrier.
        assertTrue("the clock has not passed the debounce yet, ${sinceQuery()} ms", sinceQuery() < 249)
        looper.idleFor(Duration.ofMillis(249 - sinceQuery()))
        assertTrue("Room can answer this query at all", found("persimmon"))
        looper.idle()
        assertEquals("the clock was held short of the debounce", 249L, sinceQuery())
        assertFalse("no search 1 ms short of the debounce", vm.state.value.searched)

        // At the debounce, with the clock held there: only a real wait for Room's thread, which does not move it.
        looper.idleFor(Duration.ofMillis(1))
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (!vm.state.value.searched && System.nanoTime() < deadline) looper.idle()
        assertTrue("the search ran at the 250 ms debounce, with no more time passing", vm.state.value.searched)
        assertEquals("the clock was held at the debounce", 250L, sinceQuery())

        awaitSearch { exists(card("Orchard", noteLabel)) }
        assertEquals(listOf("Note: Orchard"), resultCards())
    }

    // ---- 225 ----

    private class DailyWords(val priority: String, val todo: String, val schedule: String, val dontForget: String, val notes: String) {
        val all get() = listOf(priority, todo, schedule, dontForget, notes)
    }

    private fun typeInEveryDailyField(words: DailyWords) {
        field(str(R.string.daily_priority_hint, 1)).performTextInput(words.priority)
        typeInFirst(todoHint, words.todo)
        typeInFirst(str(R.string.daily_schedule_hint), words.schedule)
        typeInFirst(str(R.string.daily_dont_forget_hint), words.dontForget)
        typeInFirst(notesHint, words.notes)
        rule.waitForIdle()
    }

    /** Daily page of [day], not today: each of the five fields' words is found, as a Daily page card that opens that day. */
    @Test
    fun item225_wordsFromAllFiveDailyFieldsFindThatDaysCardAndTapOpensThatDay() {
        val words = DailyWords("Narwhal", "Quokkas", "Marzipan", "Zeppelin", "Tamarind")
        openAppAt(day)
        typeInEveryDailyField(words)
        await { words.all.all(::found) }
        assertFalse("the day is not today", day == today)
        goToSearchTab()

        val title = "Wednesday, March 12, 2031"
        assertEquals(title, day.format(dailyTitleFormat))
        val saved = mapOf(
            "Priority" to words.priority,
            "To-Do" to words.todo,
            "Schedule" to words.schedule,
            "Don't Forget" to words.dontForget,
            "Notes" to words.notes,
        )
        for ((fieldName, word) in saved) {
            typeQuery(word)
            awaitSearch { exists(card(title, dailyLabel)) }
            assertEquals("$fieldName word $word: one daily card", listOf("Daily page: $title"), resultCards())

            tapCard(title, dailyLabel)
            await { routes.lastOrNull() == Routes.daily(day) }
            // The header names the day without a year, so the route above and the typed word on the page say which day it is.
            await { exists(hasText("Wednesday, Mar 12")) }
            // The header shows before the page has loaded its fields, so scroll to the word on every try.
            await { runCatching { field(word).assertExists() }.isSuccess }
            assertFalse("left the search screen", exists(hasText(prompt)))

            pressBackInApp()
            await { routes.lastOrNull() == Routes.SEARCH && exists(queryField) }
        }
    }

    /** The Today icon is not distinguishable by description: the card's only description is the type label. */
    @Test
    fun item225_aWordFromTodaysPageFindsTodayWithTheFullDateAsItsTitle() {
        openApp()
        typeInFirst(todoHint, "Okapi")
        await { found("Okapi") }
        goToSearchTab()

        typeQuery("Okapi")
        val title = today.format(dailyTitleFormat)
        awaitSearch { exists(card(title, dailyLabel)) }
        assertEquals(listOf("Daily page: $title"), resultCards())
        assertEquals("Daily page", dailyLabel)
        assertEquals("the title is the full date with the year", true, title.contains(today.year.toString()))
    }

    // ---- 226 ----

    @Test
    fun item226_aWeekGoalAndAMonthEventFindTheirPagesAndTapOpensThem() {
        openAppAt(day)
        openWeekTab()
        field(str(R.string.goal_hint, 1)).performTextInput("Kumquat")
        rule.waitForIdle()
        openMonthTab()
        tapText(str(R.string.event_add))
        rule.waitForIdle()
        field(str(R.string.event_hint)).performTextInput("Persimmon")
        rule.waitForIdle()
        await { found("Kumquat") && found("Persimmon") }
        goToSearchTab()

        val weekTitle = "Week of Mar 10 – Mar 16"
        val monthTitle = "March 2031"
        typeQuery("Kumquat")
        awaitSearch { exists(card(weekTitle, weekLabel)) }
        assertEquals(listOf("Weekly page: $weekTitle"), resultCards())
        assertEquals("Weekly page", weekLabel)
        tapCard(weekTitle, weekLabel)
        await { routes.lastOrNull() == Routes.week(monday) }
        await { exists(hasText(weekTitle)) && exists(hasText("Kumquat") and hasSetTextAction()) }
        assertEquals("week/2031-03-10", routes.last())
        assertFalse("left the search screen", exists(hasText(prompt)))
        pressBackInApp()
        await { routes.lastOrNull() == Routes.SEARCH && exists(queryField) }

        typeQuery("Persimmon")
        awaitSearch { exists(card(monthTitle, monthLabel)) }
        assertEquals(listOf("Monthly page: $monthTitle"), resultCards())
        assertEquals("Monthly page", monthLabel)
        tapCard(monthTitle, monthLabel)
        await { routes.lastOrNull() == Routes.month(month) }
        await { exists(hasText(monthTitle)) && exists(hasText("Persimmon") and hasSetTextAction()) }
        assertEquals("month/2031-03", routes.last())
        assertFalse("left the search screen", exists(hasText(prompt)))
    }

    // ---- 229 ----

    @Test
    fun item229_aWordThatMatchesNothingShowsNothingFoundWhileARealWordShowsItsCard() {
        seedNote("Aquarium", "a zebrafish swims here")
        openSearch()
        assertTrue("anchor: the search screen is up", exists(hasText(prompt)))

        typeQuery("zebrafish")
        awaitSearch { exists(card("Aquarium", noteLabel)) }
        assertFalse("control: a real word does not say nothing was found", exists(hasText(nothingFound)))

        typeQuery("zzzqqq")
        awaitSearch { exists(hasText(nothingFound)) }
        assertEquals("Nothing found.", nothingFound)
        assertFalse("no card is left", exists(card("Aquarium", noteLabel)))
        assertFalse("and the prompt is not shown over it", exists(hasText(prompt)))
    }

    // ---- 230 ----

    @Test
    fun item230_tappingTheXEmptiesTheFieldAndBringsBackThePromptWithoutResults() {
        seedNote("Aquarium", "a zebrafish swims here")
        openSearch()
        typeQuery("zebrafish")
        awaitSearch { exists(card("Aquarium", noteLabel)) }
        assertTrue("the X is there with a query", exists(hasContentDescription(clear)))
        assertFalse("no prompt while there are results", exists(hasText(prompt)))

        rule.onNode(hasContentDescription(clear)).performClick()
        rule.waitForIdle()

        assertEquals("field empty", "", queryShown())
        assertTrue("placeholder is back in the field", exists(hasText(hint) and queryField))
        assertTrue("prompt is back", exists(hasText(prompt)))
        assertFalse("results gone", exists(card("Aquarium", noteLabel)))
        assertFalse("X gone", exists(hasContentDescription(clear)))
        // A search that was on its way when the X was tapped must not bring the results back.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        rule.waitForIdle()
        assertFalse("still no results after the debounce would have passed", exists(card("Aquarium", noteLabel)))
        assertTrue(exists(hasText(prompt)))
    }

    // ---- 237 ----

    /** Note behaviour: what Search shows after a matched note is deleted from the editor it was opened in. */
    @Test
    fun item237_afterDeletingAMatchedNoteSearchStillShowsItsCardUntilRetypedAndTappingItSaysItIsGone() {
        val keeper = seedNote("Keeper", "needle one")
        val doomed = seedNote("Doomed", "needle two")
        openSearch()
        typeQuery("needle")
        awaitSearch { exists(card("Doomed", noteLabel)) && exists(card("Keeper", noteLabel)) }
        assertEquals(listOf("Note: Doomed", "Note: Keeper"), resultCards())

        tapCard("Doomed", noteLabel)
        await { routes.lastOrNull() == Routes.note(doomed) && exists(hasContentDescription(str(R.string.ed_pin))) }
        tapDescription(str(R.string.ed_delete))
        await { exists(hasText(str(R.string.ed_delete_title))) }
        rule.onNode(hasText(str(R.string.action_delete)) and hasAnyAncestor(isDialog())).performClick()
        rule.waitForIdle()
        await { routes.lastOrNull() == Routes.SEARCH && exists(queryField) }
        await { runBlocking { container.pages.getPage(doomed) } == null }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(600))
        rule.waitForIdle()

        assertEquals("back on Search with the query", "needle", queryShown())
        val staleCard = exists(card("Doomed", noteLabel))
        println("ITEM237 stale card still shown without retyping: $staleCard; cards = ${resultCards()}")
        assertTrue("the deleted note's card is still there (note behaviour)", staleCard)
        assertTrue("the other result is too", exists(card("Keeper", noteLabel)))

        tapCard("Doomed", noteLabel)
        await { routes.lastOrNull() == Routes.note(doomed) }
        await { exists(hasText(str(R.string.ed_not_found))) }
        assertEquals("This note no longer exists.", str(R.string.ed_not_found))
        tapDescription(str(R.string.ed_back))
        await { routes.lastOrNull() == Routes.SEARCH && exists(queryField) }
        assertTrue("still listed after looking at it", exists(card("Doomed", noteLabel)))

        typeQuery("needle")
        awaitSearch { exists(card("Keeper", noteLabel)) && !exists(card("Doomed", noteLabel)) }
        assertEquals("retyping removes it", listOf("Note: Keeper"), resultCards())
        assertTrue(runBlocking { container.pages.getPage(keeper) } != null)
    }

    // ---- 238 ----

    @Test
    fun item238_backFromAnOpenedNoteOrDailyPageReturnsToSearchWithTheSameQueryAndResults() {
        seedNote("Pantry", "fresh gnocchi tonight")
        seedTasks(day, TaskSection.TODO, "make gnocchi")
        openSearch()
        typeQuery("gnocchi")
        awaitSearch { resultCards().size == 2 }
        val before = resultCards()
        assertEquals(listOf("Daily page: Wednesday, March 12, 2031", "Note: Pantry").sorted(), before.sorted())

        tapCard("Pantry", noteLabel)
        await { routes.lastOrNull()?.startsWith("note/") == true && exists(hasContentDescription(str(R.string.ed_pin))) }
        assertFalse("on the note, not on Search", exists(hasText(prompt)) || exists(hasContentDescription(clear)))
        pressBackInApp()
        await { routes.lastOrNull() == Routes.SEARCH && exists(queryField) }
        assertEquals("query kept after the note", "gnocchi", queryShown())
        assertEquals("results kept after the note", before, resultCards())

        tapCard("Wednesday, March 12, 2031", dailyLabel)
        await { routes.lastOrNull() == Routes.daily(day) && exists(hasText("Wednesday, Mar 12")) }
        pressBackInApp()
        await { routes.lastOrNull() == Routes.SEARCH && exists(queryField) }
        assertEquals("query kept after the daily page", "gnocchi", queryShown())
        assertEquals("results kept after the daily page", before, resultCards())
    }

    // ---- 239 ----

    private fun injectBars(keyboardOpen: Boolean): Int {
        val density = rule.activity.resources.displayMetrics.density
        fun px(dp: Int) = (dp * density).roundToInt()
        val imePx = if (keyboardOpen) px(280 + 24) else 0
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, px(32), 0, 0))
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, px(24)))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, imePx))
            .setVisible(WindowInsetsCompat.Type.ime(), keyboardOpen)
            .build()
        val composeRoot = rule.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        ViewCompat.dispatchApplyWindowInsets(composeRoot, insets)
        rule.waitForIdle()
        return imePx
    }

    private val rootBottom get() = rule.onRoot().fetchSemanticsNode().boundsInRoot.bottom

    private fun barTop() = bounds(rule.onNode(tab(R.string.tab_search))).top

    private fun scrollToLastCard(last: String) {
        rule.onNode(pageList).performScrollToNode(hasText(last))
        rule.waitForIdle()
    }

    /**
     * The field takes focus on a tap. A real keyboard opening is not provable on the JVM, so the keyboard is an injected IME
     * inset (as in ImeGapTest) and the last result is looked at with it closed and open.
     */
    @Test
    fun item239_theLastResultSitsAboveTheKeyboardAndTheBottomBarWhenScrolledToTheEnd() {
        repeat(25) { seedNote("Melon %02d".format(it), "melon number $it") }
        rule.activity.enableEdgeToEdge()
        openSearch()

        rule.onNode(queryField).performClick()
        rule.waitForIdle()
        rule.onNode(queryField).assertIsFocused()

        typeQuery("melon")
        awaitSearch { exists(hasText("Melon 24")) }
        assertEquals("most recent first, so the oldest is last", "Note: Melon 24", resultCards().first())

        // Control: with no keyboard the last card is above the bottom bar.
        injectBars(keyboardOpen = false)
        scrollToLastCard("Melon 00")
        val closedBottom = bounds(rule.onNode(hasText("Melon 00"))).bottom
        assertTrue("closed: last card bottom $closedBottom above the bar ${barTop()}", closedBottom <= barTop() + 1f)

        val imePx = injectBars(keyboardOpen = true)
        val keyboardTop = rootBottom - imePx
        assertTrue("the injected insets reached the bar: it clears the navigation bar", bounds(rule.onNode(tab(R.string.tab_search))).bottom <= rootBottom - dp(24) + 1f)
        scrollToLastCard("Melon 00")
        val openBottom = bounds(rule.onNode(hasText("Melon 00"))).bottom
        assertTrue("open: last card bottom $openBottom must be above the keyboard's top $keyboardTop", openBottom <= keyboardTop + 1f)
        assertTrue("open: last card bottom $openBottom must be above the bar's top ${barTop()}", openBottom <= barTop() + 1f)
    }

    // ---- 240 ----

    private val sortWords = listOf("sort", "order", "menu", "more", "option", "filter")

    private fun assertNoSortControls(where: String, expectedExtraClickables: List<String>, cards: List<String>) {
        val all = rule.onAllNodes(anyNode, useUnmergedTree = true).fetchSemanticsNodes()
        val words = all.flatMap { it.texts() + it.descriptions() }
        assertTrue("$where: the tree was read, ${words.size} labels", words.size > 10)
        val bad = words.filter { w -> sortWords.any { w.contains(it, ignoreCase = true) } }
        assertEquals("$where: no label mentions sorting, ordering, a menu or options", emptyList<String>(), bad)
        assertTrue("$where: no dropdown role", all.none { it.role() == Role.DropdownList })
        val clickable = rule.onAllNodes(hasClickAction() and !hasSetTextAction()).fetchSemanticsNodes()
        val others = clickable.filter { node ->
            node.role() != Role.Tab && node.texts().none { t -> cards.any { it == t } }
        }.map { (it.texts() + it.descriptions()).joinToString("/") }
        assertEquals("$where: the only clickable things besides tabs and result cards", expectedExtraClickables, others)
    }

    private fun assertOrder(where: String, expected: List<String>) {
        val shown = expected.sortedBy { rule.onNode(hasText(it)).fetchSemanticsNode().positionInRoot.y }
        assertEquals("$where: order on screen", expected, shown)
        assertEquals("$where: only the pinned note has the pin", 1, count(hasContentDescription(pinnedLabel)))
        rule.onNode(hasText(expected.first()) and hasContentDescription(pinnedLabel)).assertExists()
    }

    @Test
    fun item240_notesAndSearchHaveNoSortControlAndKeepPinnedThenLastUpdatedOrder() {
        // Created Alpha, Bravo, Charlie, Delta. Alpha is pinned and Charlie edited last, so neither creation order fits.
        val alpha = seedNote("Alpha", "pangolin a")
        seedNote("Bravo", "pangolin b")
        val charlie = seedNote("Charlie", "pangolin c")
        seedNote("Delta", "pangolin d")
        runBlocking {
            container.pages.setPinned(alpha, true)
            Thread.sleep(5)
            container.pages.setTitle(charlie, "Charlie")
        }
        val expected = listOf("Alpha", "Charlie", "Delta", "Bravo")

        openApp()
        tapTab(R.string.tab_notes)
        await { routes.lastOrNull() == Routes.NOTES && expected.all { exists(hasText(it)) } }
        assertTrue("anchor: the Notes screen has its new note button", exists(hasContentDescription(str(R.string.nt_new))))
        assertOrder("Notes", expected)
        assertNoSortControls("Notes", listOf(str(R.string.nt_new)), expected)

        goToSearchTab()
        typeQuery("pangolin")
        awaitSearch { expected.all { exists(card(it, noteLabel)) } }
        assertTrue("anchor: Search with results and the X", exists(hasContentDescription(clear)))
        assertOrder("Search", expected)
        assertNoSortControls("Search", listOf(clear), expected)
        assertEquals(expected.map { "Note: $it" }, resultCards())
    }
}
