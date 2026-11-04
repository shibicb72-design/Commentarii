package com.shibi.commentarii.ui.daily

import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.theme.CommentariiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * #61: each to-do row's Move up / Move down / Remove controls must have their own touch target. They used to stack
 * in one Box with Remove on top, so a tap on an arrow deleted the row. TodoCard is shared by daily, week and month.
 * performClick() injects a real touch at the node's centre, so it hits whatever is on top at that point.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h915dp-xxhdpi")
class TodoCardControlsTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val moves = mutableListOf<Pair<Int, Int>>()
    private val removes = mutableListOf<Int>()

    private fun show() {
        rule.setContent {
            CommentariiTheme(dark = false) {
                TodoCard(
                    List(3) { TaskUi(it.toLong(), "Task $it") },
                    TaskListActions(onMove = { i, delta -> moves += i to delta }, onRemove = { removes += it }),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun control(labelRes: Int, row: Int): SemanticsNodeInteraction =
        rule.onAllNodesWithContentDescription(rule.activity.getString(labelRes))[row]

    private fun bounds(labelRes: Int, row: Int): Rect = control(labelRes, row).fetchSemanticsNode().boundsInRoot

    private fun assertControlsSeparate() {
        for (row in 0..2) {
            val up = bounds(R.string.daily_move_up, row)
            val down = bounds(R.string.daily_move_down, row)
            val remove = bounds(R.string.daily_remove, row)
            assertFalse("row $row: Move up $up overlaps Move down $down", up.overlaps(down))
            assertFalse("row $row: Move down $down overlaps Remove $remove", down.overlaps(remove))
            assertFalse("row $row: Move up $up overlaps Remove $remove", up.overlaps(remove))
        }
    }

    private fun assertTapsReachTheirOwnControl() {
        control(R.string.daily_move_down, 0).performClick()
        assertEquals("Move down on row 0 must not remove", emptyList<Int>(), removes)
        assertEquals(listOf(0 to 1), moves)

        moves.clear()
        control(R.string.daily_move_up, 1).performClick()
        assertEquals("Move up on row 1 must not remove", emptyList<Int>(), removes)
        assertEquals(listOf(1 to -1), moves)

        moves.clear()
        control(R.string.daily_remove, 1).performClick()
        assertEquals(listOf(1), removes)
        assertEquals("Remove must not move", emptyList<Pair<Int, Int>>(), moves)
    }

    /** The three controls must stay on screen and leave the text usable on a narrow screen. */
    private fun assertRowFits() {
        val rootRight = rule.onRoot().fetchSemanticsNode().boundsInRoot.right
        assertTrue("controls must stay on screen", bounds(R.string.daily_remove, 0).right <= rootRight)
        val density = rule.activity.resources.displayMetrics.density
        val textWidth = rule.onAllNodes(hasSetTextAction())[0].fetchSemanticsNode().boundsInRoot.width / density
        assertTrue("text field too narrow: $textWidth dp", textWidth >= 100f)
    }

    @Test
    fun controlsDoNotOverlap() {
        show()
        assertControlsSeparate()
    }

    @Test
    fun tapsReachTheirOwnControl() {
        show()
        assertTapsReachTheirOwnControl()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xxhdpi")
    fun controlsWorkAtCoverWidth() {
        show()
        assertControlsSeparate()
        assertTapsReachTheirOwnControl()
        assertRowFits()
    }

    @Test
    fun arrowsAreDisabledAtTheEnds() {
        show()
        control(R.string.daily_move_up, 0).performClick()
        control(R.string.daily_move_down, 2).performClick()
        assertEquals(emptyList<Pair<Int, Int>>(), moves)
        assertEquals("a tap on a disabled arrow must not remove", emptyList<Int>(), removes)
    }
}
