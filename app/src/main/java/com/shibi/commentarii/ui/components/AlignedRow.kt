package com.shibi.commentarii.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val TargetSize = 48.dp

/** [base] with centred line height and no font padding, so glyphs sit in the middle of their line box. */
fun checklistTextStyle(base: TextStyle): TextStyle = base.copy(
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

@Composable
private fun lineHeightDp(textStyle: TextStyle): Dp = with(LocalDensity.current) {
    if (textStyle.lineHeight.isSp) textStyle.lineHeight.toDp() else 24.dp
}

/** Space above the text that centres its first line on a 48 dp leading control (0 when the line is taller). */
@Composable
fun firstLineTopPadding(textStyle: TextStyle): Dp = maxOf(0.dp, (TargetSize - lineHeightDp(textStyle)) / 2)

/**
 * Row for checkbox/bullet style lines: [leading] and [trailing] sit in boxes of at least 48 dp at the top and the first line of
 * [content] is centred on them; further lines of a multi-line text flow below. For one line it equals a centred Row.
 * [content] receives the top padding to apply to the text.
 */
@Composable
fun FirstLineRow(
    textStyle: TextStyle,
    leading: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.(Modifier) -> Unit,
) {
    val lineHeight = lineHeightDp(textStyle)
    val topPad = maxOf(0.dp, (TargetSize - lineHeight) / 2)
    val controlPad = maxOf(0.dp, (lineHeight - TargetSize) / 2)
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (leading != null) {
            Box(
                Modifier.padding(top = controlPad).defaultMinSize(TargetSize, TargetSize),
                contentAlignment = Alignment.Center,
            ) { leading() }
        }
        content(Modifier.padding(top = topPad))
        if (trailing != null) {
            Box(
                Modifier.padding(top = controlPad).defaultMinSize(TargetSize, TargetSize),
                contentAlignment = Alignment.Center,
            ) { trailing() }
        }
    }
}
