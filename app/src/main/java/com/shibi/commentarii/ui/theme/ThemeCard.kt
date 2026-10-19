package com.shibi.commentarii.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// Themes v2 (S4): the per-card corner sticker, washi tape, soft shadow, themed bullets, check glyphs
// and numbered badges. Everything draws outside layout (drawWithCache / drawBehind); paths are cached.

@Immutable
class CardDecor(
    val doodle: DoodleId,
    val accent: Color,
    val ink: DoodleInk,
    val tape: Boolean,
    val tapePattern: Int,
    val sticker: Boolean,
    val shadowInk: Color,
    val tilt: Float = 8f,
    val stickerScale: Float = 1f,
    val doodle2: DoodleId = doodle,
    val paper: Paper = Paper.NONE,
    val paperColor: Color = Color.Transparent,
    val paperSeed: Int = 0,
)

private val plannerStickers = listOf(
    DoodleId.CROWN, DoodleId.BOBA, DoodleId.TULIP_JAR, DoodleId.CALENDAR,
    DoodleId.BAG_HEART, DoodleId.CACTUS, DoodleId.SPEECH_HEART, DoodleId.PEN,
)
private val sunnyStickers = listOf(
    DoodleId.SUN_FACE, DoodleId.MUG_HEART, DoodleId.CANDLE_JAR, DoodleId.CAMERA,
    DoodleId.NOTE_CARD, DoodleId.LEAF_SPRIG, DoodleId.DAISY, DoodleId.PLANT_POT,
)
private val cloudStickers = listOf(
    DoodleId.CLOUD_FACE, DoodleId.SUN_FACE, DoodleId.RAINBOW, DoodleId.RAINDROP, DoodleId.KITE, DoodleId.UMBRELLA,
)
private val petalStickers = listOf(
    DoodleId.BLOSSOM, DoodleId.BRANCH, DoodleId.BUD, DoodleId.PETAL, DoodleId.DAISY, DoodleId.BOW,
)
private val heartStickers = listOf(
    DoodleId.HEART_FACE, DoodleId.BALLOON, DoodleId.ENVELOPE, DoodleId.BOW, DoodleId.SPARKLE_CLUSTER, DoodleId.BEAR_PEEK,
)
private val matchaStickers = listOf(
    DoodleId.TEACUP_FACE, DoodleId.MOCHI, DoodleId.ONIGIRI, DoodleId.DANGO, DoodleId.TEAPOT, DoodleId.TEA_LEAF,
)
private val moonStickers = listOf(
    DoodleId.MOON_FACE, DoodleId.STAR_FACE, DoodleId.PLANET, DoodleId.LAVENDER, DoodleId.CANDLE_JAR, DoodleId.CLOUD_FACE,
)

/** Corner stickers for a theme, cycled by card index; empty for rose_cream and unknown ids. */
fun cornerDoodles(themeId: String): List<DoodleId> = when (themeId) {
    "washi_planner" -> plannerStickers
    "sunny_journal" -> sunnyStickers
    "cloud_drift" -> cloudStickers
    "petal_fall" -> petalStickers
    "floating_hearts" -> heartStickers
    "matcha_tea" -> matchaStickers
    "lavender_moon" -> moonStickers
    else -> emptyList()
}

/** Daily's six card titles get fixed, distinct slots (every theme has >= 6 stickers); others hash. */
private val knownTitles = listOf("Today's Top Priorities", "To-Do List", "Schedule", "Don't Forget", "Today's Mood", "Notes")

private fun cardIndex(title: String, salt: Int, n: Int): Int {
    val known = knownTitles.indexOf(title.replace('’', '\''))
    return if (known >= 0) known % n else abs(title.hashCode() + salt) % n
}

private fun tapePatternOf(themeId: String): Int = when (themeId) {
    "washi_planner" -> 0
    "sunny_journal" -> 1
    else -> 2
}

fun cardDecor(themeId: String, art: ThemeArt, colors: ArtColors, title: String): CardDecor {
    val stickers = cornerDoodles(themeId)
    val idx = cardIndex(title, art.cardSalt, stickers.size.coerceAtLeast(1))
    val n = colors.accents.size
    val accent = colors.accents[abs(title.hashCode()) % n]
    val second = colors.accents[(abs(title.hashCode()) + 2) % n]
    return CardDecor(
        doodle = stickers.getOrElse(idx) { DoodleId.BLOSSOM },
        accent = accent,
        ink = DoodleInk(fill = accent, second = second, ink = colors.ink, alpha = colors.decoAlpha),
        tape = art.tape && (idx % 3 == 1 || (themeId == "washi_planner" && idx == 2)),
        tapePattern = tapePatternOf(themeId),
        sticker = themeId == "washi_planner" || themeId == "sunny_journal",
        shadowInk = colors.ink,
        tilt = if (idx % 2 == 0) 8f else -8f,
        stickerScale = if (themeId == "lavender_moon") 1.6f else 1f,
        doodle2 = stickers.getOrElse((idx + 3) % stickers.size.coerceAtLeast(1)) { DoodleId.BLOSSOM },
        // LINED would draw its rules at >= 0.45 alpha, so tall cards fall back to a dot grid there
        paper = if (art.paper == Paper.LINED) Paper.DOT_GRID else art.paper,
        paperColor = colors.paper,
        paperSeed = themeId.hashCode(),
    )
}

/**
 * Soft layered shadow behind the card, the corner sticker (40dp at (w-26dp, -12dp)) and, on tape themes,
 * a washi strip on the bottom-right corner over the content. Put it BEFORE the Surface's own clip.
 */
fun Modifier.themedCard(decor: CardDecor, shape: Shape): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val s1 = 1.dp.toPx()
    val stickerSize = 40.dp.toPx() * decor.stickerScale
    val stickerAt = Offset(size.width - 26.dp.toPx() * decor.stickerScale, -12.dp.toPx() * decor.stickerScale)
    val tapeAt = Offset(size.width - 2.dp.toPx(), size.height - 2.dp.toPx())
    val tapeLen = 52.dp.toPx()
    val tapeThick = 16.dp.toPx()
    val sh1 = decor.shadowInk.copy(alpha = 0.05f)
    val sh2 = decor.shadowInk.copy(alpha = 0.035f)
    val sh3 = decor.shadowInk.copy(alpha = 0.02f)
    val tapeColor = decor.accent.copy(alpha = 0.9f)
    // Cards taller than ~600dp (Schedule) get a faint paper texture and a second edge sticker.
    val tall = size.height > 600.dp.toPx() && decor.paper != Paper.NONE
    val texture = if (tall) PaperCache(size, this, decor.paper, decor.paperSeed) else null
    val clip = if (tall) Path().apply {
        when (outline) {
            is Outline.Rectangle -> addRect(outline.rect)
            is Outline.Rounded -> addRoundRect(outline.roundRect)
            is Outline.Generic -> addPath(outline.path)
        }
    } else null
    val sticker2At = Offset(size.width - 20.dp.toPx() * decor.stickerScale, size.height * 0.5f)
    onDrawWithContent {
        translate(0f, 3 * s1) { drawOutline(outline, sh3) }
        translate(0f, 2 * s1) { drawOutline(outline, sh2) }
        translate(0f, s1) { drawOutline(outline, sh1) }
        drawContent()
        if (texture != null && clip != null) {
            clipPath(clip) { drawPaper(texture, decor.paperColor, 0.06f) }
        }
        drawDoodle(decor.doodle, stickerAt, stickerSize, decor.ink, rotation = decor.tilt, sticker = decor.sticker)
        if (tall) {
            drawDoodle(decor.doodle2, sticker2At, stickerSize, decor.ink, rotation = -decor.tilt, sticker = decor.sticker)
        }
        if (decor.tape) {
            drawWashiTape(tapeAt, tapeLen, tapeThick, -32f, tapeColor, decor.tapePattern, decor.shadowInk)
        }
    }
}

private fun bulletColor(colors: ArtColors, index: Int): Color =
    lerp(colors.accents[index % colors.accents.size], colors.ink, 0.25f)

/** List bullet: keeps the 6dp footprint of the plain dot and draws a 12dp themed glyph over it. */
@Composable
fun ThemedBullet(index: Int, modifier: Modifier = Modifier) {
    val pair = currentArt()
    if (pair == null) {
        Box(modifier.size(6.dp).background(LocalContentColor.current, CircleShape))
        return
    }
    val (art, colors) = pair
    val glyph = art.bullets[index % art.bullets.size]
    val color = bulletColor(colors, index)
    Box(
        modifier.size(6.dp).drawBehind {
            drawGlyph(glyph, center, 14.dp.toPx(), color)
        },
    )
}

/** Inner 22dp checkbox: outline glyph when open, filled accent glyph with a white tick when checked. */
@Composable
fun ThemedCheckGlyph(checked: Boolean, modifier: Modifier = Modifier) {
    val pair = currentArt()
    val ink = LocalContentColor.current
    Box(
        modifier.size(22.dp).drawBehind {
            val (art, colors) = pair ?: return@drawBehind
            val sz = size.minDimension
            if (!checked) {
                drawGlyph(art.check, center, sz, ink.copy(alpha = 0.7f), filled = false)
            } else {
                drawGlyph(art.check, center, sz, lerp(colors.accents.first(), colors.ink, 0.6f))
                val o = Offset(center.x - sz / 2f, center.y - sz / 2f)
                val w = 2.2.dp.toPx()
                val a = o + Offset(0.28f * sz, 0.52f * sz)
                val b = o + Offset(0.44f * sz, 0.67f * sz)
                val d = o + Offset(0.74f * sz, 0.34f * sz)
                drawLine(Color.White, a, b, w, StrokeCap.Round)
                drawLine(Color.White, b, d, w, StrokeCap.Round)
            }
        },
    )
}

/** 24dp pastel circle behind the "1." priority number; drawn outside layout so the 26dp width is untouched. */
fun Modifier.themedBadge(index: Int, colors: ArtColors): Modifier = drawBehind {
    val c = colors.accents[index % colors.accents.size]
    drawCircle(c, 12.dp.toPx(), Offset(7.dp.toPx(), size.height / 2f))
    drawCircle(lerp(c, colors.ink, 0.25f), 12.dp.toPx(), Offset(7.dp.toPx(), size.height / 2f),
        style = Stroke(1.dp.toPx()))
}
