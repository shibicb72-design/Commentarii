package com.shibi.commentarii.ui.daily

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import com.shibi.commentarii.R
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.testutil.DailyPageTestBase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Checklist #84-#88, #90: the rollover dialog on the real screen. RolloverOfferTest and RolloverTest cover the offer itself.
 * "Gone for today" and "offered again" are read after a reopen, which builds a new view model that runs the check again.
 */
class DailyRolloverDialogTest : DailyPageTestBase() {
    private val yesterday get() = today.minusDays(1)
    private val moveAll get() = str(R.string.rollover_move_all)
    private val pick get() = str(R.string.rollover_pick)
    private val skip get() = str(R.string.rollover_skip)
    private val moveSelected get() = str(R.string.rollover_move_selected)
    private val pickTitle get() = str(R.string.rollover_pick_title)

    private fun dateText(date: LocalDate) = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

    private fun offerTitle(n: Int) = str(R.string.rollover_title, n, dateText(yesterday))

    private fun awaitOffer(n: Int) = await { exists(hasText(offerTitle(n))) }

    private fun assertNoDialog() {
        assertFalse(exists(hasText(moveAll)))
        assertFalse(exists(hasText(pickTitle)))
    }

    private fun seedTwoItems() {
        seedTasks(yesterday, TaskSection.PRIORITY, "Old priority")
        seedTasks(yesterday, TaskSection.TODO, "Old todo")
        seedLoadAnchor(today)
    }

    /** Reopens today and waits until its rollover check is over, then asserts it offered nothing. */
    private fun assertNotOfferedOnReopen() {
        reopen()
        awaitLoadAnchor()
        assertNoDialog()
    }

    private fun assertNothingCopied() {
        assertEquals(emptyList<String>(), texts(today, TaskSection.PRIORITY))
        assertEquals(emptyList<String>(), texts(today, TaskSection.TODO))
        assertEquals(listOf(null), tasks(yesterday, TaskSection.PRIORITY).map { it.carriedTo })
        assertEquals(listOf(null), tasks(yesterday, TaskSection.TODO).map { it.carriedTo })
    }

    @Test
    fun item084_dialogListsTheItemsWithItsThreeButtons() {
        seedTasks(yesterday, TaskSection.PRIORITY, "Old priority")
        seedTasks(yesterday, TaskSection.TODO, "Old todo", "Old second todo")
        seedTasks(yesterday, TaskSection.DONT_FORGET, "Not offered")
        open(today)
        awaitOffer(3)

        for (item in listOf("Old priority", "Old todo", "Old second todo")) rule.onNode(hasText("• $item")).assertExists()
        assertFalse(exists(hasText("• Not offered")))
        rule.onNode(hasText(moveAll)).assertExists()
        rule.onNode(hasText(pick)).assertExists()
        rule.onNode(hasText(skip)).assertExists()
    }

    @Test
    fun item084_oneItemUsesTheSingularTitle() {
        seedTasks(yesterday, TaskSection.TODO, "Old todo")
        seedTasks(yesterday, TaskSection.PRIORITY, "Finished", done = true)
        open(today)
        val title = str(R.string.rollover_title_one, dateText(yesterday))
        await { exists(hasText(title)) }
        rule.onNode(hasText("• Old todo")).assertExists()
        assertFalse("done items are not offered", exists(hasText("• Finished")))
    }

    @Test
    fun item085_moveAllPutsItemsOnTodayAndTheDialogDoesNotReturn() {
        seedTwoItems()
        open(today)
        awaitOffer(2)

        tapInDialog(moveAll)
        await { exists(hasText("Old priority") and hasSetTextAction()) }
        assertNoDialog()
        field("Old priority").assertExists()
        field("Old todo").assertExists()
        await { texts(today, TaskSection.PRIORITY) == listOf("Old priority") && texts(today, TaskSection.TODO) == listOf("Old todo") }

        assertNotOfferedOnReopen()
        field("Old priority").assertExists()
    }

    @Test
    fun item085_prioritiesStayPrioritiesOnlyWhileTodayHasFewerThanThree() {
        seedTasks(yesterday, TaskSection.PRIORITY, "Carried A", "Carried B")
        seedTasks(today, TaskSection.PRIORITY, "Mine 1", "Mine 2")
        open(today)
        awaitOffer(2)

        tapInDialog(moveAll)
        await { texts(today, TaskSection.TODO).isNotEmpty() }
        assertEquals(listOf("Mine 1", "Mine 2", "Carried A"), texts(today, TaskSection.PRIORITY))
        assertEquals(listOf("Carried B"), texts(today, TaskSection.TODO))
    }

    /** Blank rows are what autosave leaves behind once a page has been edited: they are not priorities. */
    @Test
    fun item085_blankSavedRowsDoNotCountAsPriorities() {
        seedTasks(yesterday, TaskSection.PRIORITY, "Carried A")
        seedTasks(today, TaskSection.PRIORITY, "Mine 1", "", "")
        open(today)
        awaitOffer1()

        tapInDialog(moveAll)
        await { exists(hasText("Carried A") and hasSetTextAction()) }
        await { (texts(today, TaskSection.PRIORITY) + texts(today, TaskSection.TODO)).contains("Carried A") }
        assertEquals(listOf("Mine 1", "Carried A"), texts(today, TaskSection.PRIORITY))
        assertEquals(emptyList<String>(), texts(today, TaskSection.TODO))
    }

    private fun awaitOffer1() = await { exists(hasText(str(R.string.rollover_title_one, dateText(yesterday)))) }

    private fun pickBoxes() = rule.onAllNodes(isToggleable() and hasAnyAncestor(isDialog()))

    @Test
    fun item086_pickStartsAllTickedAndOnlyTickedItemsAreCopied() {
        seedTwoItems()
        open(today)
        awaitOffer(2)

        tapInDialog(pick)
        rule.onNode(hasText(pickTitle)).assertExists()
        pickBoxes()[0].assertIsOn()
        pickBoxes()[1].assertIsOn()

        rule.onNode(hasText("Old priority") and hasAnyAncestor(isDialog())).performClick()
        rule.waitForIdle()
        pickBoxes()[0].assertIsOff()
        pickBoxes()[1].assertIsOn()

        tapInDialog(moveSelected)
        await { texts(today, TaskSection.TODO) == listOf("Old todo") }
        assertNoDialog()
        assertEquals(emptyList<String>(), texts(today, TaskSection.PRIORITY))
        assertEquals("the unticked original stays uncarried", null, tasks(yesterday, TaskSection.PRIORITY).single().carriedTo)
        assertEquals(today, tasks(yesterday, TaskSection.TODO).single().carriedTo)
        assertNotOfferedOnReopen()
    }

    @Test
    fun item087_skipCopiesNothingAndTheDialogDoesNotReturn() {
        seedTwoItems()
        open(today)
        awaitOffer(2)
        tapInDialog(skip)
        awaitLoadAnchor()
        assertNoDialog()
        assertNothingCopied()
        assertNotOfferedOnReopen()
        assertNothingCopied()
    }

    @Test
    fun item087_skipInThePickViewDoesTheSame() {
        seedTwoItems()
        open(today)
        awaitOffer(2)
        tapInDialog(pick)
        rule.onNode(hasText(pickTitle)).assertExists()
        tapInDialog(skip)
        assertNoDialog()
        assertNothingCopied()
        assertNotOfferedOnReopen()
        assertNothingCopied()
    }

    private fun assertDismissLeavesItOfferedAgain(dismiss: () -> Unit) {
        seedTwoItems()
        open(today)
        awaitOffer(2)
        dismiss()
        assertNoDialog()
        assertNothingCopied()

        reopen()
        awaitOffer(2)
        assertNothingCopied()
    }

    @Test
    fun item088_backClosesWithoutCopyingAndItIsOfferedAgain() = assertDismissLeavesItOfferedAgain { pressBackInDialog() }

    @Test
    fun item088_tapOutsideClosesWithoutCopyingAndItIsOfferedAgain() = assertDismissLeavesItOfferedAgain { tapOutsideDialog() }

    @Test
    fun item088_backInThePickViewDoesTheSame() = assertDismissLeavesItOfferedAgain {
        tapInDialog(pick)
        rule.onNode(hasText(pickTitle)).assertExists()
        pressBackInDialog()
    }

    private fun assertNoOfferOn(date: LocalDate) {
        seedTasks(date.minusDays(1), TaskSection.TODO, "Earlier")
        seedLoadAnchor(date)
        open(date)
        awaitLoadAnchor()
        assertNoDialog()
    }

    @Test
    fun item090_aPastDateDoesNotOffer() = assertNoOfferOn(today.minusDays(3))

    @Test
    fun item090_aFutureDateDoesNotOffer() = assertNoOfferOn(today.plusDays(3))

    @Test
    fun item090_positiveControlTodayWithTheSameSetupOffers() {
        seedTasks(yesterday, TaskSection.TODO, "Earlier")
        seedLoadAnchor(today)
        open(today)
        awaitOffer1()
        assertNull("nothing moved yet", tasks(yesterday, TaskSection.TODO).single().carriedTo)
    }
}
