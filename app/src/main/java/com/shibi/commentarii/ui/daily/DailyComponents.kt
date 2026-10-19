package com.shibi.commentarii.ui.daily

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.ui.components.FirstLineRow
import com.shibi.commentarii.ui.components.checklistTextStyle
import com.shibi.commentarii.ui.components.firstLineTopPadding
import com.shibi.commentarii.ui.theme.AppColors
import com.shibi.commentarii.ui.theme.HeaderStyle
import com.shibi.commentarii.ui.theme.LabelStyle
import com.shibi.commentarii.ui.theme.LocalAppTheme
import com.shibi.commentarii.ui.theme.ThemedCheckGlyph
import com.shibi.commentarii.ui.theme.ThemedSectionLabel
import com.shibi.commentarii.ui.theme.cardDecor
import com.shibi.commentarii.ui.theme.currentArt
import com.shibi.commentarii.ui.theme.themedCard
import kotlin.math.abs

/**
 * Underlined field that wraps long text over several lines (leading/trailing stay on the first line). Newlines are
 * stripped unless [allowNewlines]. When not [enabled] the text is read-only but keeps its normal colour.
 * Text, placeholder and cursor colours all derive from the surrounding content colour.
 */
@Composable
fun LineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    strike: Boolean = false,
    allowNewlines: Boolean = false,
    minLines: Int = 1,
    enabled: Boolean = true,
) {
    val content = LocalContentColor.current
    val style = checklistTextStyle(MaterialTheme.typography.bodyLarge).copy(
        color = content,
        textDecoration = if (strike) TextDecoration.LineThrough else TextDecoration.None,
    )
    val bottomPad = firstLineTopPadding(style)
    Column(modifier.fillMaxWidth()) {
        FirstLineRow(style, leading, trailing = trailing) { pos ->
            BasicTextField(
                value = value,
                onValueChange = { onValueChange(if (allowNewlines) it else it.replace('\n', ' ')) },
                readOnly = !enabled,
                textStyle = style,
                minLines = minLines,
                keyboardOptions = if (allowNewlines) KeyboardOptions.Default else KeyboardOptions(imeAction = ImeAction.Done),
                cursorBrush = SolidColor(content),
                modifier = Modifier
                    .weight(1f)
                    .then(pos)
                    .padding(bottom = bottomPad),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(placeholder, style = style.copy(color = content.copy(alpha = 0.5f)))
                        }
                        inner()
                    }
                },
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Round checkbox with a tick when checked. */
@Composable
fun CircleCheck(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val primary = MaterialTheme.colorScheme.primary
    val ring = if (checked) primary else LocalContentColor.current.copy(alpha = 0.7f)
    val art = currentArt()
    Box(
        modifier
            .size(40.dp)
            .clip(CircleShape)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() }),
        contentAlignment = Alignment.Center,
    ) {
        if (art != null) {
            ThemedCheckGlyph(checked)
        } else Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) primary else androidx.compose.ui.graphics.Color.Transparent)
                .border(2.dp, ring, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
fun SmallIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = modifier.size(36.dp)) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun AddRowButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Right-end triangular notch of [notchPx], used by the RIBBON header style. */
private fun notchedShape(notchPx: Float) = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width - notchPx, size.height / 2f)
    lineTo(size.width, size.height)
    lineTo(0f, size.height)
    close()
}

/** Per-theme tab styling drawn behind the title; never changes the tab's size. PLAIN draws nothing. */
private fun Modifier.headerDecor(style: HeaderStyle, tabColor: Color, onTab: Color): Modifier = when (style) {
    HeaderStyle.WASHI -> drawBehind {
        val step = 8.dp.toPx()
        val stripe = onTab.copy(alpha = 0.12f)
        clipRect {
            var x = -size.height
            while (x < size.width) {
                drawLine(stripe, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 2.dp.toPx())
                x += step
            }
        }
        val tooth = 3.dp.toPx()
        val pitch = 4.dp.toPx()
        for (edgeX in floatArrayOf(0f, size.width)) {
            val dir = if (edgeX == 0f) 1f else -1f
            val zig = Path().apply {
                moveTo(edgeX, 0f)
                var y = 0f
                var out = true
                while (y < size.height) {
                    y = minOf(y + pitch, size.height)
                    lineTo(edgeX + if (out) dir * tooth else 0f, y)
                    out = !out
                }
            }
            drawPath(zig, stripe, style = Stroke(width = 2.dp.toPx()))
        }
    }
    HeaderStyle.HIGHLIGHTER -> drawBehind {
        val inset = 2.dp.toPx()
        val skew = size.height * 0.07f // about 4 degrees
        val swash = Path().apply {
            moveTo(inset + skew, inset)
            lineTo(size.width - inset, inset)
            lineTo(size.width - inset - skew, size.height - inset)
            lineTo(inset, size.height - inset)
            close()
        }
        drawPath(swash, tabColor.copy(alpha = 0.85f))
        drawPath(swash, tabColor.copy(alpha = 0.85f), style = Stroke(width = 4.dp.toPx(), join = StrokeJoin.Round))
    }
    else -> this
}

private fun artCardRadius(style: LabelStyle) = when (style) {
    LabelStyle.PILL -> 18.dp
    LabelStyle.SWASH -> 16.dp
    LabelStyle.STAMP -> 14.dp
    LabelStyle.ROSE -> 16.dp
}

/** Rounded card with a pink label tab. Content colour comes from the card container. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = AppColors.current
    val theme = LocalAppTheme.current
    val tabColor = colors.tabs[abs(title.hashCode()) % colors.tabs.size]
    val notchPx = with(LocalDensity.current) { 6.dp.toPx() }
    val tabShape = if (theme.headerStyle == HeaderStyle.RIBBON) notchedShape(notchPx) else theme.tabShape
    val highlighter = theme.headerStyle == HeaderStyle.HIGHLIGHTER
    val art = currentArt()
    val decor = art?.let { (a, c) -> remember(theme.id, title, c) { cardDecor(theme.id, a, c, title) } }
    val cardShape = if (art != null) RoundedCornerShape(artCardRadius(art.first.labelStyle)) else theme.cardShape
    Column(modifier.fillMaxWidth()) {
        if (art != null) {
            ThemedSectionLabel(title, tabColor)
        } else Surface(
            color = if (highlighter) Color.Transparent else tabColor,
            contentColor = colors.onTab,
            shape = tabShape,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .headerDecor(theme.headerStyle, tabColor, colors.onTab)
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Surface(
            color = colors.card,
            contentColor = colors.onCard,
            shape = cardShape,
            border = if (art != null) {
                val edge = art.second.cardEdge ?: lerp(tabColor, art.second.ink, 0.18f)
                BorderStroke(1.5.dp, edge)
            } else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = if (decor != null) Modifier.fillMaxWidth().themedCard(decor, cardShape) else Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), content = content)
        }
    }
}
