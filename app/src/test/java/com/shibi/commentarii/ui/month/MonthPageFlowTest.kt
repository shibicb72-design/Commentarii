package com.shibi.commentarii.ui.month

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.font.FontWeight
import androidx.core.graphics.ColorUtils
import com.shibi.commentarii.R
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.PeriodPageTestBase
import com.shibi.commentarii.ui.theme.ScriptFont
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/** Checklist #153-#159 and #161-#168: the month page in the real AppNav, with every edit read back from the database. */
class MonthPageFlowTest : PeriodPageTestBase() {
    private val title = "March 2031"
    private val next get() = str(R.string.month_next)
    private val previous get() = str(R.string.month_previous)
    private val calendarTitle get() = str(R.string.section_calendar)
    private val eventsTitle get() = str(R.string.section_events)
    private val reviewTitle get() = str(R.string.section_review)
    private val addEvent get() = str(R.string.event_add)
    private val eventHint get() = str(R.string.event_hint)
    private val hasEntries get() = str(R.string.month_day_has_entries)

    private val dayNumber = SemanticsMatcher("a day number") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.toIntOrNull() != null }
    }

    private fun openMonth(width: WindowWidthSizeClass = WindowWidthSizeClass.Compact) {
        openAppAt(day, width)
        openMonthTab()
    }

    private fun cell(description: String) = hasContentDescription(description, substring = true) and hasClickAction()

    private fun marchCell(dayOfMonth: Int) = cell("March $dayOfMonth")

    /** The number in a cell. Only for days that appear once in the grid. */
    private fun numberNode(text: String) = rule.onNode(hasText(text), useUnmergedTree = true)

    private fun events(of: YearMonth = month) = runBlocking { container.pages.getEvents(monthPageId(of)) }.items

    private fun eventDay(dayOfMonth: Int, of: YearMonth = month) = of.atDay(dayOfMonth).toEpochDay()

    /**
     * MonthSections.kt keeps its date formats in top-level vals, which take the locale of whichever test first touches the file
     * and keep it for the whole sandbox. Loading the file under en-US first stops a foreign-locale test from leaking into the rest.
     */
    private fun loadMonthSectionsInEnglish() {
        val locale = Locale.getDefault()
        Locale.setDefault(Locale.US)
        Class.forName("com.shibi.commentarii.ui.month.MonthSectionsKt")
        Locale.setDefault(locale)
    }

    @Test
    fun item153_headerShowsTheMonthNameWithNoSubtitleAndItsControlsInOrder() {
        openMonth()
        assertEquals("month/$month", routes.last())
        awaitTitle(title)
        assertEquals(ScriptFont, layoutOf(titleText(title).fetchSemanticsNode()).layoutInput.style.fontFamily)
        assertEquals("Previous month", previous)
        assertEquals("Next month", next)
        assertEquals("Pick a date", pickDate)

        val prevBounds = bounds(rule.onNode(hasContentDescription(previous)))
        val titleBounds = bounds(titleText(title))
        val pickBounds = bounds(rule.onNode(hasContentDescription(pickDate)))
        val nextBounds = bounds(rule.onNode(hasContentDescription(next)))
        assertTrue("left arrow first", prevBounds.right <= titleBounds.left)
        assertTrue("calendar icon after the title", titleBounds.right <= pickBounds.left)
        assertTrue("right arrow last", pickBounds.right <= nextBounds.left)

        assertFalse("no ISO line", exists(hasText("ISO", substring = true)))
        // No subtitle: nothing else sits in the header, so the next thing below the title is the first card.
        val texts = rule.onAllNodes(SemanticsMatcher("has text") { SemanticsProperties.Text in it.config }).fetchSemanticsNodes()
            .filter { it.positionInRoot.y > titleBounds.top && it.config[SemanticsProperties.Text].none { t -> t.text == title } }
            .minBy { it.positionInRoot.y }
        assertEquals(calendarTitle, texts.config[SemanticsProperties.Text].first().text)
    }

    @Test
    @Config(qualifiers = "ru-rRU-w412dp-h915dp-xxhdpi")
    fun item153_item156_monthNameIsTheStandaloneOneAndWeekdaysFollowTheLocale() {
        loadMonthSectionsInEnglish()
        showMonth(month, fixedClock(day))
        // "марта" would be the form used inside a date.
        awaitTitle("март 2031")

        val names = DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale("ru", "RU")) }
        assertEquals("пн", names.first().lowercase())
        val lefts = names.map { unclippedBounds(rule.onNode(hasText(it))).left }
        assertEquals("Monday first", lefts.sorted(), lefts)
    }

    @Test
    fun item154_arrowsCrossTheYearAndBackSkipsTheMonths() {
        openAppAt(LocalDate.of(2030, 12, 15))
        openMonthTab()
        val daily = routes.first()
        awaitTitle("December 2030")

        tapDescription(next)
        awaitTitle("January 2031")
        await { routes.last() == "month/2031-01" }
        tapDescription(next)
        awaitTitle("February 2031")
        tapDescription(previous)
        awaitTitle("January 2031")
        tapDescription(previous)
        awaitTitle("December 2030")
        await { routes == listOf(daily, "month/2030-12") }
        tapDescription(previous)
        awaitTitle("November 2030")
        tapDescription(next)
        tapDescription(next)
        awaitTitle("January 2031")
        await { routes == listOf(daily, "month/2031-01") }

        pressBackInApp()
        await { routes == listOf(daily) }
        rule.onNode(tab(R.string.tab_today)).assertIsSelected()
    }

    @Test
    fun item155_pickingAnyDayOpensItsMonth() {
        openMonth()
        awaitTitle(title)
        goToDateWithPicker(LocalDate.of(2031, 7, 19))
        awaitTitle("July 2031")
        await { routes.last() == "month/2031-07" }
    }

    /** All the numbers of the calendar, top row first. */
    private fun gridRows(): List<List<SemanticsNode>> {
        val goals = rule.onNode(sectionTitle(goalsTitle)).fetchSemanticsNode().positionInRoot.y
        val numbers = rule.onAllNodes(dayNumber, useUnmergedTree = true).fetchSemanticsNodes()
            .filter { it.positionInRoot.y < goals }
            .sortedBy { it.positionInRoot.y }
        // A number with a dot slot under it sits a little higher in its row than a faded one, so a row is a band, not one height.
        val rows = mutableListOf<MutableList<SemanticsNode>>()
        for (number in numbers) {
            val row = rows.lastOrNull()
            if (row != null && number.positionInRoot.y - row.first().positionInRoot.y < dp(20)) row += number else rows += mutableListOf(number)
        }
        return rows.map { row -> row.sortedBy { it.positionInRoot.x } }
    }

    private fun numberOf(node: SemanticsNode) = node.config[SemanticsProperties.Text].first().text

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item156_fullMondayFirstWeeksFadedOutsideDaysAndATodayOutline() {
        showMonth(month, fixedClock(day))
        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val headerLefts = names.map { unclippedBounds(rule.onNode(hasText(it))).left }
        assertEquals("Mon..Sun", headerLefts.sorted(), headerLefts)

        // March 2031 runs from a Saturday to a Monday, so it needs both neighbours to make whole weeks.
        val rows = gridRows()
        assertEquals(6, rows.size)
        rows.forEach { assertEquals("a whole week", 7, it.size) }
        assertEquals(listOf("24", "25", "26", "27", "28", "1", "2"), rows.first().map(::numberOf))
        assertEquals(listOf("31", "1", "2", "3", "4", "5", "6"), rows.last().map(::numberOf))

        // The columns line up with the weekday names above them.
        val firstRowCenters = rows.first().map { it.positionInRoot.x + it.size.width / 2f }
        val headerCenters = names.map { unclippedBounds(rule.onNode(hasText(it))).center.x }
        firstRowCenters.indices.forEach { assertEquals(headerCenters[it], firstRowCenters[it], dp(4).toFloat()) }

        val outside = rows.first().take(5) + rows.last().drop(1)
        val inside = rows.flatten() - outside.toSet()
        assertEquals(31, inside.size)
        outside.forEach { assertEquals("faded ${numberOf(it)}", 0.7f, layoutOf(it).layoutInput.style.color.alpha, 0.01f) }
        inside.forEach { assertEquals("full ${numberOf(it)}", 1f, layoutOf(it).layoutInput.style.color.alpha, 0.01f) }

        // Only the month's own days can be tapped.
        assertEquals(31, count(hasClickAction() and hasContentDescription("March", substring = true)))
        rule.onAllNodes(hasText("24"))[0].performClick()
        rule.onAllNodes(hasText("6"))[1].performClick()
        rule.waitForIdle()
        assertEquals("the faded days open nothing", emptyList<LocalDate>(), openedDays)
        rule.onNode(marchCell(13)).performClick()
        rule.waitForIdle()
        assertEquals("control: a day of the month does", listOf(LocalDate.of(2031, 3, 13)), openedDays)

        assertEquals("today's number", FontWeight.Bold, layoutOf(numberNode("12").fetchSemanticsNode()).layoutInput.style.fontWeight)
        assertEquals(FontWeight.Normal, layoutOf(numberNode("13").fetchSemanticsNode()).layoutInput.style.fontWeight)
        settleAnimations()
        val todayCell = rule.onNode(marchCell(12))
        val middle = bounds(todayCell).height / rule.activity.resources.displayMetrics.density / 2
        assertSameColor("today's outline", colors.primary, pixel(todayCell, 0.7f, middle))
        assertSameColor("no outline on another day", appColors.card, pixel(rule.onNode(marchCell(13)), 0.7f, middle))
    }

    /** The dot's colour: it sits under the number, 2 dp below it and 5 dp across. */
    private fun dotPixel(number: String): Color {
        val node = numberNode(number)
        val density = rule.activity.resources.displayMetrics.density
        val bounds = bounds(node)
        return pixel(node, bounds.width / density / 2, bounds.height / density + 4.5f)
    }

    private fun assertDot(number: String, expected: Boolean) {
        val text = layoutOf(numberNode(number).fetchSemanticsNode()).layoutInput.style.color
        assertSameColor("cell $number", if (expected) text else appColors.card, dotPixel(number))
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item157_textOnADayPutsADotUnderItsNumberAndADescription() {
        openMonth()
        settleAnimations()
        assertDot("12", false)

        rule.onNode(marchCell(12)).performClick()
        await { routes.last() == "daily/$day" && exists(hasText("1.")) }
        field(str(R.string.daily_priority_hint, 1)).performTextInput("Anything")
        await { texts(day, TaskSection.PRIORITY) == listOf("Anything") }
        pressBackInApp()

        await { exists(hasContentDescription("March 12, $hasEntries")) }
        assertEquals("has entries", hasEntries)
        settleAnimations()
        assertDot("12", true)

        // A day that was only opened has no text, so no dot.
        rule.onNode(marchCell(13)).performClick()
        await { routes.last() == "daily/${day.plusDays(1)}" && exists(hasText("1.")) }
        pressBackInApp()
        await { exists(marchCell(14)) }
        settleFor(300)
        assertTrue(exists(hasContentDescription("March 13")))
        assertFalse(exists(hasContentDescription("March 13, $hasEntries")))
        settleAnimations()
        assertDot("13", false)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item157_notesAndSeededToDosCountAsEntriesToo() {
        seedTasks(day.plusDays(1), TaskSection.TODO, "A to-do")
        openMonth()
        await { exists(hasContentDescription("March 13, $hasEntries")) }

        rule.onNode(marchCell(12)).performClick()
        await { routes.last() == "daily/$day" && exists(hasText("1.")) }
        scrollTo(hasText(notesHint))
        field(notesHint).performTextInput("A note")
        await { documentOf(pageId(day), SectionKind.NOTES) == "A note" }
        pressBackInApp()
        await { exists(hasContentDescription("March 12, $hasEntries")) }
    }

    /** Readable: the number is drawn in the tint's own on-colour, and that pair has real contrast. */
    private fun assertMoodTints(dark: Boolean) {
        val moods = (1..5).toList()
        // Monday to Friday of the second week; each number appears once in the grid.
        moods.forEach { seedMood(LocalDate.of(2031, 3, 9 + it), it) }
        openAppAt(day, dark = dark)
        openMonthTab()
        await { exists(hasContentDescription(str(R.string.mood_1), substring = true)) && exists(hasContentDescription(str(R.string.mood_5), substring = true)) }
        settleAnimations()

        val tints = moods.map { mood ->
            val number = (9 + mood).toString()
            val square = rule.onNode(marchCell(9 + mood))
            val tint = pixel(square, 5f, 5f)
            assertSameColor("mood $mood's tint", appColors.mood[mood - 1], tint)

            val textColor = layoutOf(numberNode(number).fetchSemanticsNode()).layoutInput.style.color
            assertEquals("mood $mood's number colour", appColors.onMood[mood - 1], textColor)
            val contrast = ColorUtils.calculateContrast(textColor.toArgb(), tint.copy(alpha = 1f).toArgb())
            assertTrue("mood $mood: contrast $contrast of $textColor on $tint", contrast >= 4.5)
            assertTrue("mood $mood: the number is drawn in that colour", numberDrawnIn(numberNode(number), textColor))
            tint
        }
        assertEquals("five distinct tints: $tints", 5, tints.distinct().size)

        // Control: a day with no mood has no tint.
        assertSameColor("no mood", appColors.card, pixel(rule.onNode(marchCell(20)), 5f, 5f))
    }

    private fun numberDrawnIn(node: SemanticsNodeInteraction, color: Color): Boolean {
        val bitmap = windowBitmap()
        val area = bounds(node)
        for (y in area.top.toInt() until area.bottom.toInt()) {
            for (x in area.left.toInt() until area.right.toInt()) {
                val pixel = Color(bitmap.getPixel(x, y))
                if (abs(pixel.red - color.red) < 0.02f && abs(pixel.green - color.green) < 0.02f && abs(pixel.blue - color.blue) < 0.02f) return true
            }
        }
        return false
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item158_moodTintsAreDistinctAndReadableInLightTheme() = assertMoodTints(dark = false)

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item158_moodTintsAreDistinctAndReadableInDarkTheme() = assertMoodTints(dark = true)

    @Test
    fun item159_tappingADayOpensItAndBackReturnsToTheMonth() {
        openMonth()
        val monthRoute = routes.last()
        rule.onNode(marchCell(21)).performClick()
        await { routes.last() == "daily/2031-03-21" && exists(hasText("1.")) }
        titleText("Friday, Mar 21").assertExists()

        pressBackInApp()
        await { routes.last() == monthRoute && exists(hasText(title)) }
        rule.onNode(tab(R.string.tab_month)).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item161_addItemKeepsAddingGoalsWithNoCap() {
        openMonth()
        fun addGoal() {
            inSection(goalsTitle, hasText(addItem)).performClick()
            rule.waitForIdle()
        }
        assertFalse("a month starts with no goal rows", exists(hasText("Goal 1")))

        for (n in 1..5) {
            addGoal()
            val row = field(str(R.string.goal_hint, n))
            checkOf(row).assertIsOff()
            onRowOf(row, hasContentDescription(remove)).assertExists()
            assertEquals(centerY(row.fetchSemanticsNode()), centerY(onRowOf(row, hasContentDescription(remove)).fetchSemanticsNode()), dp(16).toFloat())
        }
        assertFalse(exists(hasText("Goal 6")))
        addGoal()
        field("Goal 6").assertExists()

        field("Goal 6").performTextInput("Six goals")
        await { textsOf(monthPageId(), TaskSection.GOALS) == listOf("Six goals") }
    }

    private fun eventDate(text: String) = hasText(text) and hasClickAction()

    @Test
    fun item162_addEventIsDatedTodayOnThisMonthAndTheFirstOnAnother() {
        val clock = fixedClock(day)
        showMonth(month, clock)
        assertEquals("Add event", addEvent)
        tapText(addEvent)
        await { exists(eventDate("Mar 12")) }
        assertEquals("Event", eventHint)
        field(eventHint).assertExists()
        await { events().map { it.epochDay } == listOf(eventDay(12)) }

        tapDescription(next)
        awaitTitle("April 2031")
        awaitMonthLoaded()
        tapText(addEvent)
        val april = YearMonth.of(2031, 4)
        await { exists(eventDate("Apr 1")) }
        await { events(april).map { it.epochDay } == listOf(eventDay(1, april)) }
    }

    private fun addEventNamed(text: String) {
        tapText(addEvent)
        rule.waitForIdle()
        field(eventHint).performTextInput(text)
        rule.waitForIdle()
    }

    private fun tapDateOf(eventText: String) {
        onRowOf(field(eventText), hasClickAction() and SemanticsMatcher("a date button") { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.matches(Regex("[A-Z][a-z]{2} \\d+")) }
        }).performClick()
        rule.waitForIdle()
    }

    private fun dateShownBeside(eventText: String): String {
        val matcher = hasClickAction() and SemanticsMatcher("a date button") { node ->
            node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { it.text.matches(Regex("[A-Z][a-z]{2} \\d+")) }
        }
        return onRowOf(field(eventText), matcher).fetchSemanticsNode().config[SemanticsProperties.Text].first().text
    }

    @Test
    fun item163_changingAnEventsDateUpdatesItAndReSorts() {
        openMonth()
        addEventNamed("Alpha")
        addEventNamed("Beta")
        await { events().map { it.text } == listOf("Alpha", "Beta") }
        assertEquals("Mar 1", dateShownBeside("Alpha"))
        assertTrue(bounds(field("Alpha")).top < bounds(field("Beta")).top)

        tapDateOf("Alpha")
        pickInOpenDialog(LocalDate.of(2031, 3, 20))
        await { events().map { it.text } == listOf("Beta", "Alpha") }
        assertEquals(listOf(eventDay(1), eventDay(20)), events().map { it.epochDay })
        assertEquals("Mar 20", dateShownBeside("Alpha"))
        assertEquals("Mar 1", dateShownBeside("Beta"))
        assertTrue("re-sorted on screen", bounds(field("Beta")).top < bounds(field("Alpha")).top)
    }

    /** #164 asks only to record this: the event stays on the page it was made on, with a date from the other month. */
    @Test
    fun item164_dateInAnotherMonthStaysListedOnThisMonthsPage() {
        openMonth()
        addEventNamed("Trip")
        addEventNamed("Party")
        await { events().map { it.text } == listOf("Trip", "Party") }

        tapDateOf("Trip")
        pickInOpenDialog(LocalDate.of(2031, 4, 5))
        await { events().map { it.text } == listOf("Party", "Trip") }
        assertEquals("Apr 5", dateShownBeside("Trip"))
        assertTrue("sorted after this month's own dates", bounds(field("Party")).top < bounds(field("Trip")).top)
        titleText(title).assertExists()

        // Saved on this page, shown again after leaving, and not moved to April's page.
        pressBackInApp()
        await { routes.size == 1 }
        openMonthTab()
        await { runCatching { scrollTo(hasText("Trip")) }.isSuccess }
        assertEquals("Apr 5", dateShownBeside("Trip"))
        assertEquals(emptyList<Any>(), events(YearMonth.of(2031, 4)))
    }

    @Test
    fun item165_removingAnEventDeletesItAndEventsNeverSetAReminder() {
        openMonth()
        addEventNamed("Concert")
        await { events().map { it.text } == listOf("Concert") }
        assertEquals("an event sets no reminder", 0, alarms().size)
        assertFalse(container.debugLog.readAll().contains("SCHEDULE id="))

        tapControl(remove, "Concert")
        await { events().isEmpty() }
        assertFalse(exists(hasText("Concert")))
        assertEquals(0, alarms().size)

        // Control: the alarm list does see a reminder, from a schedule row.
        val tomorrow = today.plusDays(1)
        runBlocking {
            val id = pageId(tomorrow)
            container.pages.saveSchedule(id, container.pages.getSchedule(id).map { if (it.minuteOfDay == 540) it.copy(text = "Dentist") else it })
        }
        await { alarms().size == 1 }
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h3000dp-xxhdpi")
    fun item166_toDoNotesAndReviewAreSavedAndComeBack() {
        openMonth()
        val page = monthPageId()
        fun saved() = textsOf(page, TaskSection.TODO)
        fun rows() = count(hasContentDescription(moveUp))
        assertEquals("at least three rows", 3, rows())
        assertEquals("one Review card", 1, count(sectionTitle(reviewTitle)))

        typeInFirst(todoHint, "A")
        typeInFirst(todoHint, "B")
        typeInFirst(todoHint, "C")
        await { saved() == listOf("A", "B", "C") }

        // #61: the arrows must really move the row, not hit the X stacked over them.
        tapControl(moveDown, "A")
        await { saved() == listOf("B", "A", "C") }
        checkOf(field("C")).performClick()
        rule.waitForIdle()
        await { tasksOf(page, TaskSection.TODO).single { it.text == "C" }.done }
        inSection(todoTitle, hasText(addItem)).performClick()
        rule.waitForIdle()
        assertEquals("Add item adds a row", 4, rows())
        tapControl(remove, "B")
        await { saved() == listOf("A", "C") }
        assertEquals(3, rows())

        field(notesHint).performTextInput("month note\nsecond line")
        await { documentOf(page, SectionKind.NOTES) == "month note\nsecond line" }
        inSection(reviewTitle, hasText(reviewHint) and hasSetTextAction()).performTextInput("A good month")
        await { documentOf(page, SectionKind.REVIEW) == "A good month" }

        // Back removes the page and its view model, so what comes back has to come from the database.
        pressBackInApp()
        await { routes.size == 1 }
        openMonthTab()
        await { exists(hasText("A") and hasSetTextAction()) }
        val tops = listOf("A", "C").map { bounds(field(it)).top }
        assertEquals("the moved order", tops.sorted(), tops)
        checkOf(field("C")).assertIsOn()
        checkOf(field("A")).assertIsOff()
        assertEquals(3, rows())
        field("month note\nsecond line").assertExists()
        field("A good month").assertExists()
    }

    @Test
    @Config(qualifiers = "en-rUS-w720dp-h2000dp-xxhdpi")
    fun item167_mediumWidthPutsTheSectionsInTheirTwoColumns() {
        openMonth(WindowWidthSizeClass.Medium)
        val left = assertColumn(listOf(calendarTitle, reviewTitle))
        val right = assertColumn(listOf(goalsTitle, eventsTitle, todoTitle, notesTitle))
        assertTrue("left column $left comes before the right one $right", left < right)
        assertNotEquals(left, right)
    }

    @Test
    @Config(qualifiers = "en-rUS-w720dp-h3000dp-xxhdpi")
    fun item167_positiveControlCompactWidthKeepsOneColumn() {
        openMonth(WindowWidthSizeClass.Compact)
        assertColumn(listOf(calendarTitle, goalsTitle, eventsTitle, todoTitle, notesTitle, reviewTitle))
    }

    @Test
    fun item168_thisMonthOnTheGoalsCardOpensThatMonth() {
        val elsewhere = LocalDate.of(2031, 8, 19)
        seedTasksOn(monthPageId(YearMonth.of(2031, 8)), TaskSection.GOALS, "Save")
        openAppAt(day)
        goToDateWithPicker(elsewhere)
        await { routes.last() == "daily/$elsewhere" }
        val thisMonth = str(R.string.period_card_month)
        await { exists(hasText(thisMonth)) }

        tapInDialog(thisMonth)
        await { routes.last() == "month/2031-08" }
        awaitTitle("August 2031")
        rule.onNode(tab(R.string.tab_month)).assertIsSelected()
    }
}
