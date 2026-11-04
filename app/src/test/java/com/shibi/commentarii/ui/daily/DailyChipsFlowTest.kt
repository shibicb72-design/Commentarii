package com.shibi.commentarii.ui.daily

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.DailyPageTestBase
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checklist #46-#55: the folder chip and the tag chips, read back from the database. */
class DailyChipsFlowTest : DailyPageTestBase() {
    private val shown = LocalDate.of(2031, 3, 12)

    private val noFolder get() = str(R.string.daily_no_folder)
    private val newFolder get() = str(R.string.daily_new_folder)
    private val addTag get() = str(R.string.daily_add_tag)
    private val ok get() = str(R.string.action_ok)
    private val cancel get() = str(R.string.action_cancel)

    private fun menuItem(text: String) = hasText(text) and hasAnyAncestor(isPopup())

    private fun tapMenuItem(text: String) {
        rule.onNode(menuItem(text)).performClick()
        rule.waitForIdle()
    }

    private fun openMenu(chip: String) = tapInDialog(chip)

    private fun folders() = runBlocking { container.pages.getFolders() }.map { it.name }

    private fun folderIdOfPage() = runBlocking { container.pages.getPage(pageId(shown))!!.folderId }

    private fun tagsOfPage(date: LocalDate = shown) = runBlocking { container.pages.getTagsForPage(pageId(date)) }.map { it.name }

    private fun allTags() = runBlocking { container.pages.getTags() }.map { it.name }

    private fun nameField() = rule.onNode(hasSetTextAction() and hasAnyAncestor(isDialog()))

    private fun assertDialogClosed() = assertFalse("dialog closed", exists(hasText(ok)))

    private fun startNewFolder(chip: String = noFolder) {
        openMenu(chip)
        tapMenuItem(newFolder)
        nameField().assertExists()
    }

    private fun seedFolder(name: String) = runBlocking { container.pages.createFolder(name) }

    private fun addTagNamed(name: String) {
        tapInDialog(addTag)
        nameField().performTextInput(name)
        tapInDialog(ok)
        assertDialogClosed()
    }

    private fun removeTagLabel(name: String) = str(R.string.daily_remove_tag, name)

    /** Creates a folder through the UI, proving that the earlier "nothing was created" checks were not simply too early. */
    private fun assertOnlyFolderAfterCreating(name: String) {
        startNewFolder()
        nameField().performTextInput(name)
        tapInDialog(ok)
        await { exists(hasText(name)) }
        assertEquals(listOf(name), folders())
    }

    @Test
    fun item046_menuListsNoFolderEveryFolderAndNewFolderInOrder() {
        seedFolder("Alpha")
        seedFolder("Beta")
        open(shown)
        openMenu(noFolder)
        await { exists(menuItem("Beta")) }

        val tops = listOf(noFolder, "Alpha", "Beta", newFolder).map { bounds(rule.onNode(menuItem(it))).top }
        assertEquals("in this order", tops.sorted(), tops)
    }

    @Test
    fun item047_newFolderIsCreatedAssignedShownOnTheChipAndInNotes() {
        open(shown)
        startNewFolder()
        nameField().performTextInput("Work")
        tapInDialog(ok)
        assertDialogClosed()

        await { exists(hasText("Work")) }
        assertFalse("the chip changed", exists(hasText(noFolder)))
        assertEquals(listOf("Work"), folders())
        assertEquals(runBlocking { container.pages.getFolders() }.single().id, folderIdOfPage())
    }

    @Test
    fun item047_newFolderAppearsAsAFilterChipInNotes() {
        openApp()
        tapTab(R.string.tab_notes)
        rule.waitForIdle()
        assertFalse("control: not there before", exists(hasText("Work")))

        tapTab(R.string.tab_today)
        await { exists(hasText(noFolder)) }
        startNewFolder()
        nameField().performTextInput("Work")
        tapInDialog(ok)
        await { exists(hasText("Work")) }

        tapTab(R.string.tab_notes)
        await { exists(hasText("Work")) }
        rule.onNode(tab(R.string.tab_notes)).assertIsSelected()
    }

    @Test
    fun item048_blankNameCreatesNothing() {
        open(shown)
        for (blank in listOf("", "   ")) {
            startNewFolder()
            if (blank.isNotEmpty()) nameField().performTextInput(blank)
            tapInDialog(ok)
            assertDialogClosed()
            rule.onNode(hasText(noFolder)).assertExists()
        }
        assertEquals(emptyList<String>(), folders())
        assertNull(folderIdOfPage())
        assertOnlyFolderAfterCreating("Real")
    }

    @Test
    fun item049_cancelCreatesNothing() {
        open(shown)
        startNewFolder()
        nameField().performTextInput("Not this")
        tapInDialog(cancel)
        assertDialogClosed()

        rule.onNode(hasText(noFolder)).assertExists()
        assertFalse(exists(hasText("Not this")))
        assertOnlyFolderAfterCreating("Real")
    }

    @Test
    fun item050_chipFollowsEachChoiceAndNoFolderClearsThePage() {
        val alpha = seedFolder("Alpha")
        val beta = seedFolder("Beta")
        open(shown)

        openMenu(noFolder)
        await { exists(menuItem("Alpha")) }
        tapMenuItem("Alpha")
        await { exists(hasText("Alpha")) && folderIdOfPage() == alpha.id }

        openMenu("Alpha")
        tapMenuItem("Beta")
        await { exists(hasText("Beta")) && folderIdOfPage() == beta.id }
        assertFalse(exists(hasText("Alpha")))

        openMenu("Beta")
        tapMenuItem(noFolder)
        await { exists(hasText(noFolder)) && folderIdOfPage() == null }
        assertFalse(exists(hasText("Beta")))
        assertEquals("the folders themselves stay", listOf("Alpha", "Beta"), folders().sorted())
    }

    @Test
    fun item051_tagChipWithXAppearsBeforeTheAddChip() {
        open(shown)
        addTagNamed("health")
        await { exists(hasText("health")) }

        rule.onNode(hasContentDescription(removeTagLabel("health"))).assertExists()
        assertEquals(listOf("health"), tagsOfPage())
        assertTrue("before + Tag", bounds(rule.onNode(hasText("health"))).right <= bounds(rule.onNode(hasText(addTag))).left)
    }

    @Test
    fun item052_sameNameReusesTheTagAndDifferentCaseDoesNotMakeASecondOne() {
        open(shown)
        addTagNamed("health")
        await { exists(hasText("health")) }

        addTagNamed("health")
        addTagNamed("Health")
        // A further tag proves the two adds above have been processed by the time the count is read.
        addTagNamed("marker")
        await { exists(hasText("marker")) }

        assertEquals("one chip", 1, count(hasContentDescription(removeTagLabel("health"))))
        assertFalse("no chip for a second spelling", exists(hasText("Health")))
        assertEquals(listOf("health", "marker"), allTags().sorted())
        assertEquals(listOf("health", "marker"), tagsOfPage().sorted())
    }

    @Test
    fun item053_blankTagAddsNothing() {
        open(shown)
        for (blank in listOf("", "   ")) {
            tapInDialog(addTag)
            if (blank.isNotEmpty()) nameField().performTextInput(blank)
            tapInDialog(ok)
            assertDialogClosed()
        }
        assertEquals(0, count(hasContentDescription("Remove tag", substring = true)))

        addTagNamed("marker")
        await { exists(hasText("marker")) }
        assertEquals("only the real add arrived", listOf("marker"), allTags())
    }

    @Test
    fun item054_xRemovesTheChipButTheTagStaysForOtherPages() {
        val other = shown.plusDays(1)
        val tag = runBlocking { container.pages.getOrCreateTag("health") }
        val otherId = pageId(other)
        val shownId = pageId(shown)
        runBlocking {
            container.pages.addTag(shownId, tag.id)
            container.pages.addTag(otherId, tag.id)
        }
        open(shown)
        await { exists(hasText("health")) }

        tapDescription(removeTagLabel("health"))
        await { !exists(hasText("health")) }

        assertEquals(emptyList<String>(), tagsOfPage())
        assertEquals(listOf("health"), allTags())
        assertEquals(listOf("health"), tagsOfPage(other))
    }

    private val chipRow = SemanticsMatcher("the chip row") {
        SemanticsProperties.HorizontalScrollAxisRange in it.config && SemanticsActions.ScrollToIndex !in it.config
    }

    @Test
    fun item055_manyTagsScrollSidewaysWithoutOverlapping() {
        val names = (1..7).map { "long tag name $it" }
        val id = pageId(shown)
        runBlocking { names.forEach { container.pages.addTag(id, container.pages.getOrCreateTag(it).id) } }
        open(shown)
        await { exists(hasText(names.last())) }

        val row = rule.onNode(chipRow).fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
        assertTrue("there is more to scroll to", row.maxValue() > 0f)
        assertTrue("+ Tag starts off screen", unclippedBounds(rule.onNode(hasText(addTag))).right > rootWidth)

        val chips = (listOf(noFolder) + names + addTag).map { unclippedBounds(rule.onNode(hasText(it))) }
        chips.zipWithNext { a, b -> assertTrue("$a overlaps $b", a.right <= b.left) }

        rule.onNode(hasText(addTag)).performScrollTo()
        rule.waitForIdle()
        assertTrue("+ Tag reachable", unclippedBounds(rule.onNode(hasText(addTag))).right <= rootWidth)
    }
}
