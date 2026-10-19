package com.shibi.commentarii.ui.notes

import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEditingTest {
    @Test
    fun toggleBoldAddsAndRemoves() {
        val b = DocBlock(text = "hello world")
        val bold = b.toggleBold(0, 5)
        assertEquals(listOf(TextSpan(0, 5)), bold.spans)
        assertTrue(bold.isBold(1, 4))
        assertEquals(emptyList<TextSpan>(), bold.toggleBold(0, 5).spans)
    }

    @Test
    fun toggleBoldInsideSplitsSpan() {
        val b = DocBlock(text = "hello world", spans = listOf(TextSpan(0, 11)))
        assertEquals(listOf(TextSpan(0, 2), TextSpan(7, 11)), b.toggleBold(2, 7).spans)
    }

    @Test
    fun toggleBoldPartiallyBoldExtends() {
        val b = DocBlock(text = "hello world", spans = listOf(TextSpan(0, 3)))
        assertEquals(listOf(TextSpan(0, 8)), b.toggleBold(2, 8).spans)
    }

    @Test
    fun typingShiftsSpans() {
        val b = DocBlock(text = "ab cd", spans = listOf(TextSpan(3, 5)))
        assertEquals(listOf(TextSpan(5, 7)), b.withText("XXab cd").spans)
        assertEquals(listOf(TextSpan(3, 6)), b.withText("ab cde").spans)
        assertEquals(listOf(TextSpan(3, 4)), b.withText("ab cd".dropLast(1)).spans)
    }

    @Test
    fun deletingEverythingDropsSpans() {
        val b = DocBlock(text = "abc", spans = listOf(TextSpan(0, 3)))
        assertEquals(emptyList<TextSpan>(), b.withText("").spans)
    }

    @Test
    fun splitKeepsListTypeAndSplitsSpans() {
        val b = DocBlock(type = BlockType.CHECKBOX, text = "ab\ncd", checked = true, spans = listOf(TextSpan(1, 5)))
        val (l, r) = splitBlock(b, 2, skip = 1)
        assertEquals("ab", l.text)
        assertEquals(listOf(TextSpan(1, 2)), l.spans)
        assertEquals("cd", r.text)
        assertEquals(listOf(TextSpan(0, 2)), r.spans)
        assertEquals(BlockType.CHECKBOX, r.type)
        assertFalse(r.checked)
    }

    @Test
    fun splitHeadingContinuesAsParagraph() {
        val (_, r) = splitBlock(DocBlock(type = BlockType.H1, text = "Title"), 5)
        assertEquals(BlockType.PARAGRAPH, r.type)
    }

    @Test
    fun mergeShiftsSpans() {
        val m = mergeBlocks(
            DocBlock(text = "ab", spans = listOf(TextSpan(0, 2))),
            DocBlock(text = "cd", spans = listOf(TextSpan(0, 1))),
        )
        assertEquals("abcd", m.text)
        assertEquals(listOf(TextSpan(0, 3)), m.spans)
    }
}
