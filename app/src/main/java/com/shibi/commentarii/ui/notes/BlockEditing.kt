package com.shibi.commentarii.ui.notes

import com.shibi.commentarii.data.BlockType
import com.shibi.commentarii.data.DocBlock
import com.shibi.commentarii.data.TextSpan

/** Pure helpers for editing a [DocBlock]'s text and bold spans. No Android dependencies. */

/** Clamps spans to [length], drops empty/non-bold ones, sorts and merges overlapping or touching spans. */
fun normalizeSpans(spans: List<TextSpan>, length: Int): List<TextSpan> {
    val sorted = spans.asSequence()
        .filter { it.bold }
        .map { TextSpan(it.start.coerceIn(0, length), it.end.coerceIn(0, length)) }
        .filter { it.end > it.start }
        .sortedBy { it.start }
        .toList()
    val out = ArrayList<TextSpan>()
    for (s in sorted) {
        val last = out.lastOrNull()
        if (last != null && s.start <= last.end) {
            out[out.lastIndex] = TextSpan(last.start, maxOf(last.end, s.end))
        } else {
            out += s
        }
    }
    return out
}

/** Replaces the text, moving spans so they keep covering the same characters. Typing at a span's end extends it. */
fun DocBlock.withText(newText: String): DocBlock {
    if (newText == text) return this
    val old = text
    val maxCommon = minOf(old.length, newText.length)
    var p = 0
    while (p < maxCommon && old[p] == newText[p]) p++
    var s = 0
    while (s < maxCommon - p && old[old.length - 1 - s] == newText[newText.length - 1 - s]) s++
    val oldChangedEnd = old.length - s
    val newChangedEnd = newText.length - s
    val delta = newText.length - old.length
    fun mapStart(x: Int) = when {
        x >= oldChangedEnd -> x + delta
        x <= p -> x
        else -> newChangedEnd
    }
    fun mapEnd(x: Int) = when {
        x >= oldChangedEnd -> x + delta
        x <= p -> x
        else -> p
    }
    val moved = spans.map { TextSpan(mapStart(it.start), mapEnd(it.end), it.bold) }
    return copy(text = newText, spans = normalizeSpans(moved, newText.length))
}

/** True when every character in [start, end) is bold. */
fun DocBlock.isBold(start: Int, end: Int): Boolean {
    if (end <= start) return false
    return normalizeSpans(spans, text.length).any { it.start <= start && it.end >= end }
}

/** Makes [start, end) bold, or removes bold from it when it is already entirely bold. */
fun DocBlock.toggleBold(start: Int, end: Int): DocBlock {
    val s = minOf(start, end).coerceIn(0, text.length)
    val e = maxOf(start, end).coerceIn(0, text.length)
    if (e <= s) return this
    val current = normalizeSpans(spans, text.length)
    val result = if (isBold(s, e)) {
        current.flatMap { sp ->
            if (sp.end <= s || sp.start >= e) {
                listOf(sp)
            } else {
                listOfNotNull(
                    if (sp.start < s) TextSpan(sp.start, s) else null,
                    if (sp.end > e) TextSpan(e, sp.end) else null,
                )
            }
        }
    } else {
        current + TextSpan(s, e)
    }
    return copy(spans = normalizeSpans(result, text.length))
}

/**
 * Splits at [at], dropping [skip] characters (the newline) between the halves. A bullet or checkbox continues
 * into the new block (unchecked); other types continue as a paragraph.
 */
fun splitBlock(block: DocBlock, at: Int, skip: Int = 0): Pair<DocBlock, DocBlock> {
    val cut = at.coerceIn(0, block.text.length)
    val restStart = (cut + skip).coerceAtMost(block.text.length)
    val left = DocBlock(
        type = block.type,
        text = block.text.substring(0, cut),
        checked = block.checked,
        spans = normalizeSpans(block.spans, cut),
    )
    val shifted = block.spans.map { TextSpan(it.start - restStart, it.end - restStart, it.bold) }
    val rightText = block.text.substring(restStart)
    val rightType = if (block.type == BlockType.BULLET || block.type == BlockType.CHECKBOX) block.type else BlockType.PARAGRAPH
    val right = DocBlock(
        type = rightType,
        text = rightText,
        checked = false,
        spans = normalizeSpans(shifted, rightText.length),
    )
    return left to right
}

/** Appends [cur] to [prev], keeping prev's type and shifting cur's spans. */
fun mergeBlocks(prev: DocBlock, cur: DocBlock): DocBlock {
    val offset = prev.text.length
    val text = prev.text + cur.text
    val all = prev.spans + cur.spans.map { TextSpan(it.start + offset, it.end + offset, it.bold) }
    return prev.copy(text = text, spans = normalizeSpans(all, text.length))
}
