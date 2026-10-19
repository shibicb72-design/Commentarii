package com.shibi.commentarii.ui.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class DoodleKitTest {
    private val part1 = DoodleId.entries

    @Test
    fun everyDoodleDrawsPlainAndAsSticker() {
        val ink = DoodleInk(Color(0xFFF9C9D6), Color(0xFFC6EBDA), Color(0xFF2E2633))
        val canvas = Canvas(ImageBitmap(64, 64))
        for (sticker in listOf(false, true)) for (id in part1) {
            CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(64f, 64f)) {
                drawDoodle(id, Offset(32f, 32f), 48f, ink, t = 1.5f, rotation = 6f, sticker = sticker)
            }
        }
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(64f, 64f)) {
            for (p in 0..2) drawWashiTape(Offset(32f, 32f), 40f, 12f, -20f, ink.fill, p, ink.ink)
        }
    }
}
