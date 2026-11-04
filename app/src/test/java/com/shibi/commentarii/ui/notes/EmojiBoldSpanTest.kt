package com.shibi.commentarii.ui.notes

import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checklist #302: bold over text with emoji (two or more UTF-16 units each) stays on the right characters; spans are clamped. */
class EmojiBoldSpanTest {
    private val party = "🎉"
    private val confetti = "🎊" // Shares its high surrogate with 🎉.
    private val family = "👨‍👩‍👧"

    private fun DocBlock.boldText() = spans.map { text.substring(it.start, it.end) }

    @Test
    fun item302_boldCoversTheSelectedEmojiAndSurvivesEditsAround() {
        val start = "Hi $party there"
        val bold = DocBlock(text = start).toggleBold(3, 3 + party.length + " there".length)
        assertEquals(listOf("$party there"), bold.boldText())

        val typedBefore = bold.withText("Oh Hi $party there")
        assertEquals(listOf("$party there"), typedBefore.boldText())

        val typedAfter = typedBefore.withText("Oh Hi $party there!")
        assertEquals("typing at a span's end extends it", listOf("$party there!"), typedAfter.boldText())

        val shortened = typedBefore.withText("Hi $party there")
        assertEquals(listOf("$party there"), shortened.boldText())

        val insideWord = bold.withText("Hi $party thXere")
        assertEquals(listOf("$party thXere"), insideWord.boldText())
    }

    @Test
    fun item302_familyEmojiSequenceStaysWhole() {
        val text = "a $family b"
        val bold = DocBlock(text = text).toggleBold(2, 2 + family.length)
        assertEquals(listOf(family), bold.boldText())
        assertEquals(listOf(family), bold.withText("xx$text").boldText())
        assertTrue(bold.isBold(2, 2 + family.length))
        assertEquals(emptyList<String>(), bold.toggleBold(2, 2 + family.length).boldText())
    }

    @Test
    fun item302_typingAnEmojiRightBeforeABoldEmojiLeavesTheNewOnePlain() {
        val bold = DocBlock(text = "Hi $party").toggleBold(3, 3 + party.length)
        val typed = bold.withText("Hi $confetti$party")
        assertEquals(listOf(party), typed.boldText())
    }

    @Test
    fun item302_deletingAnEmojiRightBeforeABoldEmojiKeepsItsBold() {
        val bold = DocBlock(text = "Hi $confetti$party").toggleBold(3 + confetti.length, 3 + confetti.length + party.length)
        assertEquals(listOf(party), bold.boldText())
        val deleted = bold.withText("Hi $party")
        assertEquals(listOf(party), deleted.boldText())
    }

    @Test
    fun item302_swappingABoldEmojiForAnotherKeepsBoldOnTheNewOne() {
        val bold = DocBlock(text = "Hi $party!").toggleBold(3, 3 + party.length)
        assertEquals(listOf(confetti), bold.withText("Hi $confetti!").boldText())
    }

    @Test
    fun item302_outOfRangeSpansAreClampedNotCrashes() {
        val text = "$party ok"
        assertEquals(listOf(TextSpan(0, text.length)), normalizeSpans(listOf(TextSpan(-5, 999)), text.length))
        assertEquals(emptyList<TextSpan>(), normalizeSpans(listOf(TextSpan(50, 60)), text.length))

        val stale = DocBlock(text = text, spans = listOf(TextSpan(2, 500)))
        assertEquals(listOf(" ok"), stale.withText(text + " ").boldText().map { it.trimEnd() })
        assertEquals(text, DocBlock(text = text).toggleBold(-3, 900).boldText().single())
        assertEquals(emptyList<TextSpan>(), stale.withText("").spans)
    }

    @Test
    fun item302_selectionEndingInsideAnEmojiDoesNotCrashAndCanBeUndone() {
        val block = DocBlock(text = "$party$party")
        val split = block.toggleBold(1, 3)
        assertEquals(listOf(TextSpan(1, 3)), split.spans)
        assertEquals(emptyList<TextSpan>(), split.toggleBold(1, 3).spans)
    }
}
