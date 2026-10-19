package com.shibi.commentarii.ui.daily

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shibi.commentarii.R
import com.shibi.commentarii.ui.theme.Glyph
import com.shibi.commentarii.ui.theme.ThemedPage
import com.shibi.commentarii.ui.theme.currentArt
import com.shibi.commentarii.ui.theme.drawGlyph

/**
 * Stateless daily page. Compact width: one scrolling column of all sections.
 * Medium/Expanded: two columns (left: priorities..mood, right: schedule and notes), footer spanning.
 * [banner] is an optional slot shown above the header (export reminder etc.).
 */
@Composable
fun DailyPageContent(
    state: DailyUiState,
    actions: DailyActions,
    windowWidthClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    banner: (@Composable () -> Unit)? = null,
) {
    val twoColumns = windowWidthClass != WindowWidthSizeClass.Compact
    ThemedPage(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (banner != null) item { banner() }
            item { DailyHeader(state, actions) }
            item { PeriodGoalsCard(state.weekGoals, state.monthGoals, actions.onOpenWeek, actions.onOpenMonth) }
            if (twoColumns) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            PrioritiesCard(state.priorities, actions.priorities)
                            TodoCard(state.todo, actions.todo)
                            DontForgetCard(state.dontForget, actions.dontForget)
                            MoodCard(state.mood, actions.onMood)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Schedule(state, actions)
                            NotesCard(state.notes, actions.onNotes)
                        }
                    }
                }
            } else {
                item { PrioritiesCard(state.priorities, actions.priorities) }
                item { TodoCard(state.todo, actions.todo) }
                item { Schedule(state, actions) }
                item { DontForgetCard(state.dontForget, actions.dontForget) }
                item { MoodCard(state.mood, actions.onMood) }
                item { NotesCard(state.notes, actions.onNotes) }
            }
            item { DailyFooter() }
        }
    }
}

/** Footer line; themed pages get a pastel pill with diamond glyphs behind the unchanged string. */
@Composable
private fun DailyFooter() {
    val art = currentArt()
    val textWidth = remember { mutableFloatStateOf(0f) }
    val gaps = remember { androidx.compose.runtime.mutableStateOf(emptyList<Offset>()) }
    val pill = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp)
    val deco = if (art == null) {
        pill
    } else {
        val (_, colors) = art
        val fill = colors.accents[2].copy(alpha = 0.85f)
        val diamond = lerp(colors.accents[2], colors.ink, 0.45f)
        pill.drawBehind {
            val w = textWidth.floatValue + 32.dp.toPx()
            val h = size.height
            val left = (size.width - w) / 2f
            drawRoundRect(fill, Offset(left, 0f), Size(w, h), CornerRadius(h / 2f))
            val bw = 1.dp.toPx()
            drawRoundRect(
                diamond, Offset(left + bw / 2f, bw / 2f), Size(w - bw, h - bw), CornerRadius(h / 2f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(bw),
            )
            val d = 6.dp.toPx()
            val textLeft = left + 16.dp.toPx()
            for (gap in gaps.value) drawGlyph(Glyph.DIAMOND, Offset(textLeft + gap.x, h / 2f), d * 0.75f, diamond)
            drawGlyph(Glyph.DIAMOND, Offset(left + 12.dp.toPx(), h / 2f), d, diamond)
            drawGlyph(Glyph.DIAMOND, Offset(left + w - 12.dp.toPx(), h / 2f), d, diamond)
        }
    }
    Text(
        stringResource(R.string.daily_footer),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        onTextLayout = { r ->
            textWidth.floatValue = (0 until r.lineCount).maxOf { r.getLineRight(it) - r.getLineLeft(it) }
            val t = r.layoutInput.text.text
            gaps.value = if (art == null || r.lineCount != 1) emptyList() else t.indices
                .filter { t[it] == ' ' }
                .map { i -> r.getBoundingBox(i).center.let { Offset(it.x - r.getLineLeft(0), it.y) } }
        },
        modifier = deco,
    )
}

@Composable
private fun Schedule(state: DailyUiState, actions: DailyActions) = ScheduleCard(
    rows = state.schedule,
    onText = actions.onScheduleText,
    onAddCustomTime = actions.onAddCustomTime,
    onRemoveCustomTime = actions.onRemoveCustomTime,
)
