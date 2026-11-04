package com.shibi.commentarii.ui.week

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.toSize
import com.shibi.commentarii.R
import com.shibi.commentarii.data.SectionKind
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.PeriodPageTestBase
import com.shibi.commentarii.ui.theme.ScriptFont
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.annotation.Config

/** Checklist #135-#150 and #152: the week page in the real AppNav, with every edit read back from the database. */
class WeekPageFlowTest : PeriodPageTestBase() {
    private val title = "Week of Mar 10 – Mar 16"
    private val next get() = str(R.string.week_next)
    private val previous get() = str(R.string.week_previous)
    private val theWeek get() = str(R.string.section_week_days)
    private val moodTitle get() = str(R.string.section_mood_week)
    private val reviewWell get() = str(R.string.section_review_well)
    private val reviewImprove get() = str(R.string.section_review_improve)
    private val nothingPlanned get() = str(R.string.week_day_empty)
    private val noMood get() = str(R.string.mood_none)

    private fun iso(week: Int) = str(R.string.iso_week_fmt, week)

    private fun card(dayName: String) = hasText(dayName) and hasClickAction()

    private fun openWeek(width: WindowWidthSizeClass = WindowWidthSizeClass.Compact) {
        openAppAt(day, width)
        openWeekTab()
    }

    private fun strikeOf(text: String) = layoutOf(field(text).fetchSemanticsNode()).layoutInput.style.textDecoration

    private fun goals() = tasksOf(weekPageId(), TaskSection.GOALS)

    @Test
    fun item135_headerShowsTitleSubtitleAndControlsInOrder() {
        openWeek()
        assertEquals("tab goes to the Monday of the week", "week/$monday", routes.last())
        awaitTitle(title)
        assertEquals(ScriptFont, layoutOf(titleText(title).fetchSemanticsNode()).layoutInput.style.fontFamily)
        rule.onNode(hasText(iso(11))).assertExists()
        assertEquals("ISO W11", iso(11))
        assertEquals("Previous week", previous)
        assertEquals("Next week", next)
        assertEquals("Pick a date", pickDate)

        val prevBounds = bounds(rule.onNode(hasContentDescription(previous)))
        val titleBounds = bounds(titleText(title))
        val pickBounds = bounds(rule.onNode(hasContentDescription(pickDate)))
        val nextBounds = bounds(rule.onNode(hasContentDescription(next)))
        assertTrue("left arrow first", prevBounds.right <= titleBounds.left)
        assertTrue("calendar icon after the title", titleBounds.right <= pickBounds.left)
        assertTrue("right arrow last", pickBounds.right <= nextBounds.left)
        assertTrue("subtitle under the title", titleBounds.bottom <= bounds(rule.onNode(hasText(iso(11)))).top + 1f)
    }

    @Test
    fun item136_arrowsMoveOneWeekPerTapAndBackSkipsTheWeeks() {
        openWeek()
        val daily = routes.first()
        awaitTitle(title)

        tapDescription(next)
        awaitTitle("Week of Mar 17 – Mar 23")
        tapDescription(next)
        awaitTitle("Week of Mar 24 – Mar 30")
        tapDescription(previous)
        awaitTitle("Week of Mar 17 – Mar 23")
        await { routes == listOf(daily, "week/2031-03-17") }

        pressBackInApp()
        await { routes == listOf(daily) }
        rule.onNode(tab(R.string.tab_today)).assertIsSelected()
        assertFalse("no week left on screen", exists(hasText("Week of Mar 10 – Mar 16")))
    }

    @Test
    fun item137_pickingAThursdayInAnotherMonthOpensItsMondayFirstWeek() {
        openWeek()
        awaitTitle(title)
        val thursday = LocalDate.of(2031, 4, 17)
        assertEquals(DayOfWeek.THURSDAY, thursday.dayOfWeek)

        goToDateWithPicker(thursday)
        awaitTitle("Week of Apr 14 – Apr 20")
        await { routes.last() == "week/2031-04-14" }

        // The ends of a week: a Sunday still belongs to the Monday before it.
        goToDateWithPicker(LocalDate.of(2031, 5, 4))
        awaitTitle("Week of Apr 28 – May 4")
        await { routes.last() == "week/2031-04-28" }
    }

    @Test
    fun item138_cancelKeepsTheWeek() {
        openWeek()
        awaitTitle(title)
        val before = routes

        tapDescription(pickDate)
        typeInOpenDialog(LocalDate.of(2031, 4, 17))
        rule.onNode(hasText(str(R.string.action_ok))).assertExists()
        tapInDialog(str(R.string.action_cancel))

        assertFalse("the picker closed", exists(hasText(str(R.string.action_cancel))))
        settleFor(200)
        assertEquals(before, routes)
        titleText(title).assertExists()
    }

    @Test
    fun item139_weekOverNewYearSpansTheYearsWithTheRightIsoNumbers() {
        openAppAt(LocalDate.of(2026, 12, 30))
        openWeekTab()
        await { routes.last() == "week/2026-12-28" }
        awaitTitle("Week of Dec 28 – Jan 3")
        rule.onNode(hasText(iso(53))).assertExists()
        scrollTo(hasText(theWeek))
        val days = listOf("Mon" to "28", "Tue" to "29", "Wed" to "30", "Thu" to "31", "Fri" to "1", "Sat" to "2", "Sun" to "3")
        for ((name, number) in days) rule.onNode(card(name) and hasText(number)).assertExists()

        tapDescription(next)
        awaitTitle("Week of Jan 4 – Jan 10")
        rule.onNode(hasText(iso(1))).assertExists()
        await { routes.last() == "week/2027-01-04" }

        // A New Year's week that belongs to the next year's week 1, and the week before it.
        goToDateWithPicker(LocalDate.of(2030, 1, 1))
        awaitTitle("Week of Dec 31 – Jan 6")
        rule.onNode(hasText(iso(1))).assertExists()
        tapDescription(previous)
        awaitTitle("Week of Dec 24 – Dec 30")
        rule.onNode(hasText(iso(52))).assertExists()
    }

    @Test
    fun item140_threeGoalRowsWithCheckAndRemoveAndNoAddItem() {
        openWeek()
        assertEquals(listOf("Goal 1", "Goal 2", "Goal 3"), (1..3).map { str(R.string.goal_hint, it) })
        for (i in 1..3) {
            val row = field(str(R.string.goal_hint, i))
            onRowOf(row, hasContentDescription(remove)).assertExists()
            checkOf(row).assertIsOff()
            // Each row has its own controls, not one shared by the rows.
            assertEquals(centerY(row.fetchSemanticsNode()), centerY(onRowOf(row, hasContentDescription(remove)).fetchSemanticsNode()), dp(16).toFloat())
        }
        assertFalse(exists(hasText("Goal 4")))

        val goalsTop = rule.onNode(sectionTitle(goalsTitle)).fetchSemanticsNode().positionInRoot.y
        val weekTop = rule.onNode(sectionTitle(theWeek)).fetchSemanticsNode().positionInRoot.y
        val addButtons = rule.onAllNodes(hasText(addItem)).fetchSemanticsNodes().map { it.positionInRoot.y }
        assertTrue("no Add item among the goals: $addButtons", addButtons.none { it in goalsTop..weekTop })

        // Round: a ticked check fills a 22 dp disc in the middle of its 40 dp target, and that disc leaves its square's corners empty.
        checkOf(field("Goal 1")).performClick()
        settleAnimations()
        val check = checkOf(field("Goal 1"))
        assertSameColor("top of the disc", colors.primary, pixel(check, 20f, 10f))
        assertSameColor("corner of the disc's square", appColors.card, pixel(check, 10.5f, 10.5f))

        // Control: that search does see the same button one card further down.
        scrollTo(hasText(todoTitle))
        assertTrue(exists(hasText(addItem)))
    }

    @Test
    fun item141_tickingStrikesAndRemovingClearsButThreeRowsRemain() {
        openWeek()
        field("Goal 1").performTextInput("Run")
        rule.waitForIdle()
        checkOf(field("Run")).assertIsOff()
        assertEquals(TextDecoration.None, strikeOf("Run"))

        checkOf(field("Run")).performClick()
        rule.waitForIdle()
        checkOf(field("Run")).assertIsOn()
        assertEquals(TextDecoration.LineThrough, strikeOf("Run"))
        await { goals().any { it.text == "Run" && it.done } }

        checkOf(field("Run")).performClick()
        rule.waitForIdle()
        checkOf(field("Run")).assertIsOff()
        assertEquals(TextDecoration.None, strikeOf("Run"))
        await { goals().any { it.text == "Run" && !it.done } }

        field("Goal 2").performTextInput("Read")
        rule.waitForIdle()
        tapControl(remove, "Run")
        await { textsOf(weekPageId(), TaskSection.GOALS) == listOf("Read") }
        field("Read").assertExists()
        assertFalse("the removed goal is gone", exists(hasText("Run")))
        // The rows move up and blank ones pad the card back to three.
        field("Goal 2").assertExists()
        field("Goal 3").assertExists()
        assertFalse(exists(hasText("Goal 1")))

        tapControl(remove, "Read")
        await { textsOf(weekPageId(), TaskSection.GOALS).isEmpty() }
        for (i in 1..3) field(str(R.string.goal_hint, i)).assertExists()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item142_sevenCardsMondayFirstTodayFilledAndBoldEmptyDaysSayNothingPlanned() {
        val tuesday = monday.plusDays(1)
        seedTasks(tuesday, TaskSection.PRIORITY, "Call home")
        showWeek(monday, fixedClock(day))
        await { exists(hasText("1. Call home")) }

        val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val cards = names.mapIndexed { i, name -> rule.onNode(card(name) and hasText((10 + i).toString())) }
        cards.forEach { it.assertExists() }
        val tops = cards.map { unclippedBounds(it).top }
        assertEquals("Monday first", tops.sorted(), tops)

        assertEquals("every card but Tuesday's", 6, count(hasText(nothingPlanned)))
        assertFalse(exists(card("Tue") and hasText(nothingPlanned)))

        fun weight(number: String): FontWeight? =
            layoutOf(rule.onNode(hasText(number), useUnmergedTree = true).fetchSemanticsNode()).layoutInput.style.fontWeight
        assertEquals("today's number", FontWeight.Bold, weight("12"))
        for (other in listOf("10", "11", "13", "14", "15", "16")) assertEquals(FontWeight.Normal, weight(other))

        settleAnimations()
        names.forEachIndexed { i, name ->
            val expected = if (name == "Wed") colors.primaryContainer else colors.surfaceContainer
            assertSameColor("$name's fill", expected, pixel(cards[i], 200f, 3f))
        }
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item143_typedPrioritiesAndToDosShowOnTheDaysCard() {
        openWeek()
        rule.onNode(card("Tue")).performClick()
        val tuesday = monday.plusDays(1)
        await { routes.last() == "daily/$tuesday" && exists(hasText("1.")) }

        field(str(R.string.daily_priority_hint, 1)).performTextInput("First thing")
        field(str(R.string.daily_priority_hint, 2)).performTextInput("Second thing")
        rule.waitForIdle()
        typeInFirst(todoHint, "Alpha")
        typeInFirst(todoHint, "Beta")
        typeInFirst(todoHint, "Gamma")
        checkOf(field("Beta")).performClick()
        rule.waitForIdle()
        // A blank row on top of the three typed ones: it must not count.
        tapText(addItem)
        await { tasks(tuesday, TaskSection.TODO).count { it.done } == 1 && tasks(tuesday, TaskSection.TODO).size == 4 }
        await { texts(tuesday, TaskSection.PRIORITY) == listOf("First thing", "Second thing") }

        pressBackInApp()
        await { exists(hasText("1. First thing")) }
        rule.onNode(card("Tue") and hasText("1. First thing") and hasText("2. Second thing")).assertExists()
        rule.onNode(card("Tue") and hasText("1/3 to-dos done")).assertExists()
        assertEquals("1/3 to-dos done", str(R.string.week_todo_fmt, 1, 3))
        assertFalse("nothing planned is for empty days", exists(card("Tue") and hasText(nothingPlanned)))
        assertEquals(6, count(hasText(nothingPlanned)))
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item143_aCardShowsAtMostThreePrioritiesOfTwoLinesEach() {
        val friday = monday.plusDays(4)
        val long = "A very long priority line that needs several lines on this card, ".repeat(4).trim()
        seedTasks(friday, TaskSection.PRIORITY, long, "Two", "Three", "Four")
        openWeek()
        await { exists(hasText("1. $long")) }

        for (shown in listOf("2. Two", "3. Three")) rule.onNode(hasText(shown)).assertExists()
        assertFalse("three lines at most", exists(hasText("4. Four")))
        val layout = layoutOf(rule.onNode(hasText("1. $long"), useUnmergedTree = true).fetchSemanticsNode())
        assertEquals("two lines each", 2, layout.lineCount)
        assertTrue("the rest is cut off", layout.hasVisualOverflow)
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h2000dp-xxhdpi")
    fun item144_tappingADayOpensItAndBackReturnsToTheWeek() {
        openWeek()
        val week = routes.last()
        val friday = monday.plusDays(4)

        rule.onNode(card("Fri")).performClick()
        await { routes.last() == "daily/$friday" && exists(hasText("1.")) }
        titleText("Friday, Mar 14").assertExists()

        pressBackInApp()
        await { routes.last() == week }
        await { exists(hasText(title)) }
        rule.onNode(tab(R.string.tab_week)).assertIsSelected()
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h3000dp-xxhdpi")
    fun item145_toDoAddCheckMoveAndRemoveWithAtLeastThreeRows() {
        openWeek()
        val page = weekPageId()
        fun saved() = textsOf(page, TaskSection.TODO)
        fun rows() = count(hasContentDescription(moveUp))
        assertEquals(3, rows())

        typeInFirst(todoHint, "A")
        typeInFirst(todoHint, "B")
        typeInFirst(todoHint, "C")
        tapText(addItem)
        assertEquals("Add item adds a row", 4, rows())
        typeInFirst(todoHint, "D")
        await { saved() == listOf("A", "B", "C", "D") }

        checkOf(field("C")).performClick()
        rule.waitForIdle()
        checkOf(field("C")).assertIsOn()
        assertEquals(TextDecoration.LineThrough, strikeOf("C"))
        await { tasksOf(page, TaskSection.TODO).single { it.text == "C" }.done }

        // #61: the arrows must really move the row, not hit the X stacked over them.
        onRowOf(field("A"), hasContentDescription(moveUp)).assertIsNotEnabled()
        tapControl(moveDown, "A")
        await { saved() == listOf("B", "A", "C", "D") }
        tapControl(moveUp, "D")
        await { saved() == listOf("B", "A", "D", "C") }
        val tops = listOf("B", "A", "D", "C").map { bounds(field(it)).top }
        assertEquals("rows moved on screen", tops.sorted(), tops)
        assertEquals("nothing was removed", 4, rows())

        tapControl(remove, "A")
        await { saved() == listOf("B", "D", "C") }
        assertEquals(3, rows())
        tapControl(remove, "B")
        await { saved() == listOf("D", "C") }
        assertEquals("a blank row keeps the minimum", 3, rows())
    }

    @Test
    @Config(qualifiers = "en-rUS-w412dp-h3000dp-xxhdpi")
    fun item145_dontForgetAddLineAndRemoveWithAtLeastThreeRows() {
        openWeek()
        val page = weekPageId()
        val hint = str(R.string.daily_dont_forget_hint)
        fun saved() = textsOf(page, TaskSection.DONT_FORGET)
        fun rows(): Int {
            val top = rule.onNode(sectionTitle(dontForgetTitle)).fetchSemanticsNode().positionInRoot.y
            val bottom = rule.onNode(sectionTitle(moodTitle)).fetchSemanticsNode().positionInRoot.y
            return rule.onAllNodes(hasContentDescription(remove)).fetchSemanticsNodes().count { it.positionInRoot.y in top..bottom }
        }
        assertEquals(3, rows())

        typeInFirst(hint, "Keys")
        typeInFirst(hint, "Wallet")
        typeInFirst(hint, "Phone")
        tapText(str(R.string.daily_add_line))
        assertEquals("Add line adds a row", 4, rows())
        typeInFirst(hint, "Bag")
        await { saved() == listOf("Keys", "Wallet", "Phone", "Bag") }

        tapControl(remove, "Wallet")
        await { saved() == listOf("Keys", "Phone", "Bag") }
        assertEquals(3, rows())
        tapControl(remove, "Keys")
        await { saved() == listOf("Phone", "Bag") }
        assertEquals("a blank row keeps the minimum", 3, rows())
    }

    /** The slots of the Mood card from Monday to Sunday: their descriptions are all that tell them apart. */
    private fun moodSlots() = (listOf(noMood) + (1..5).map { str(moodLabel(it)) })
        .flatMap { rule.onAllNodes(hasContentDescription(it)).fetchSemanticsNodes() }
        .sortedBy { it.positionInRoot.x }

    private fun moodLabel(mood: Int) = when (mood) {
        5 -> R.string.mood_5
        4 -> R.string.mood_4
        3 -> R.string.mood_3
        2 -> R.string.mood_2
        else -> R.string.mood_1
    }

    @Test
    fun item146_moodRowHasSevenSlotsFacesForSetDaysAndADashForTheRest() {
        seedMood(monday.plusDays(1), 5)
        seedMood(monday.plusDays(3), 2)
        openWeek()
        scrollTo(hasText(moodTitle))
        await { exists(hasContentDescription(str(R.string.mood_5))) && exists(hasContentDescription(str(R.string.mood_2))) }

        val slots = moodSlots()
        assertEquals(7, slots.size)
        assertEquals(
            listOf("No mood", "Very happy", "No mood", "Sad", "No mood", "No mood", "No mood"),
            slots.map { it.config[SemanticsProperties.ContentDescription].single() },
        )
        val letters = listOf("M", "T", "W", "T", "F", "S", "S")
        slots.forEachIndexed { i, slot ->
            val text = slot.config[SemanticsProperties.Text].map { it.text }
            val moodSet = i == 1 || i == 3
            assertEquals("slot $i", if (moodSet) listOf(letters[i]) else listOf(letters[i], "-"), text)
        }

        // A face has no text or semantics of its own, so read its pixels under the weekday letter.
        val bitmap = windowBitmap()
        fun ink(slot: Int): Int {
            val node = slots[slot]
            val area = Rect(node.positionInRoot, node.size.toSize())
            return inkIn(bitmap, Rect(area.left, area.top + dp(26), area.right, area.bottom))
        }
        val dash = ink(0)
        assertTrue("faces are bigger than a dash: ${ink(1)} and ${ink(3)} against $dash", ink(1) > 2 * dash && ink(3) > 2 * dash)
    }

    @Test
    fun item147_tappingASlotOpensThatDayAndChangesNoMood() {
        val tuesday = monday.plusDays(1)
        val friday = monday.plusDays(4)
        seedMood(tuesday, 5)
        openWeek()
        scrollTo(hasText(moodTitle))
        await { exists(hasContentDescription(str(R.string.mood_5))) }
        assertEquals("this card offers no mood choices", 0, count(hasContentDescription(str(R.string.mood_1))))

        rule.onNode(hasContentDescription(str(R.string.mood_5))).performClick()
        await { routes.last() == "daily/$tuesday" && exists(hasText("1.")) }
        assertEquals(5, runBlocking { container.pages.getMood(tuesday) }?.mood)
        pressBackInApp()

        await { runCatching { scrollTo(hasText(moodTitle)) }.isSuccess && moodSlots().size == 7 }
        val fridaySlot = moodSlots()[4]
        assertEquals(listOf(noMood), fridaySlot.config[SemanticsProperties.ContentDescription])
        rule.onAllNodes(hasContentDescription(noMood))[3].performClick()
        await { routes.last() == "daily/$friday" && exists(hasText("1.")) }
        assertNull(runBlocking { container.pages.getMood(friday) })
    }

    @Test
    fun item148_multiLineNotesAreSavedAndComeBack() {
        openWeek()
        scrollTo(hasText(notesHint))
        field(notesHint).performTextInput("first line\nsecond line")
        await { documentOf(weekPageId(), SectionKind.NOTES) == "first line\nsecond line" }

        // Back removes the page and its view model, so what comes back has to come from the database.
        pressBackInApp()
        await { routes.size == 1 }
        openWeekTab()
        await { runCatching { scrollTo(hasText("first line\nsecond line")) }.isSuccess }
        field("first line\nsecond line").assertExists()
    }

    @Test
    fun item149_bothReviewsAreSavedIndependently() {
        openWeek()
        assertEquals("Write a few lines", reviewHint)
        inSection(reviewWell, hasText(reviewHint) and hasSetTextAction()).performTextInput("Kept my mornings")
        await { documentOf(weekPageId(), SectionKind.REVIEW_WELL) == "Kept my mornings" }
        assertEquals("", documentOf(weekPageId(), SectionKind.REVIEW_IMPROVE))

        inSection(reviewImprove, hasText(reviewHint) and hasSetTextAction()).performTextInput("Sleep earlier")
        await { documentOf(weekPageId(), SectionKind.REVIEW_IMPROVE) == "Sleep earlier" }
        assertEquals("Kept my mornings", documentOf(weekPageId(), SectionKind.REVIEW_WELL))

        pressBackInApp()
        await { routes.size == 1 }
        openWeekTab()
        await { runCatching { scrollTo(hasText("Kept my mornings")) }.isSuccess }
        scrollTo(hasText(reviewImprove))
        val wellTop = rule.onNode(sectionTitle(reviewWell)).fetchSemanticsNode().positionInRoot.y
        val improveTop = rule.onNode(sectionTitle(reviewImprove)).fetchSemanticsNode().positionInRoot.y
        val firstTop = rule.onNode(hasText("Kept my mornings") and hasSetTextAction()).fetchSemanticsNode().positionInRoot.y
        val secondTop = rule.onNode(hasText("Sleep earlier") and hasSetTextAction()).fetchSemanticsNode().positionInRoot.y
        assertTrue("each text under its own title", firstTop in wellTop..improveTop && secondTop > improveTop)
    }

    @Test
    @Config(qualifiers = "en-rUS-w720dp-h2000dp-xxhdpi")
    fun item150_mediumWidthPutsTheSectionsInTheirTwoColumns() {
        openWeek(WindowWidthSizeClass.Medium)
        val left = assertColumn(listOf(goalsTitle, todoTitle, dontForgetTitle, moodTitle))
        val right = assertColumn(listOf(theWeek, notesTitle, reviewWell, reviewImprove))
        assertTrue("left column $left comes before the right one $right", left < right)
        assertTrue("right column starts in the page's right half", right > (rootWidth + left) / 2 - dp(40))
    }

    @Test
    @Config(qualifiers = "en-rUS-w720dp-h3000dp-xxhdpi")
    fun item150_positiveControlCompactWidthKeepsOneColumn() {
        openWeek(WindowWidthSizeClass.Compact)
        assertColumn(listOf(goalsTitle, theWeek, todoTitle, dontForgetTitle, moodTitle, notesTitle, reviewWell, reviewImprove))
    }

    @Test
    fun item152_thisWeekOnTheGoalsCardOpensTheWeekContainingThatDay() {
        val sunday = LocalDate.of(2031, 3, 16)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
        seedTasksOn(weekPageId(monday), TaskSection.GOALS, "Walk")
        openAppAt(day)
        goToDateWithPicker(sunday)
        await { routes.last() == "daily/$sunday" }
        val thisWeek = str(R.string.period_card_week)
        await { exists(hasText(thisWeek)) }

        tapInDialog(thisWeek)
        await { routes.last() == "week/$monday" }
        awaitTitle(title)
        rule.onNode(tab(R.string.tab_week)).assertIsSelected()
    }
}
