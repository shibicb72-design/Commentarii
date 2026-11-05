package com.shibi.commentarii.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Trap 4: the Cloud Drift sky gradient is built from the scheme bg + ArtColors.sky, so edits can reach the page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CloudDriftSkyTest {
    private val cloud = AppThemes.all.first { it.id == "cloud_drift" }
    private val art = checkNotNull(cloud.art)

    @Test
    fun onlyCloudDriftHasSkyWithTheStockTops() {
        assertEquals(Color(0xFFDCEEFC), art.light.sky)
        assertEquals(Color(0xFF1C3048), art.dark.sky)
        AppThemes.all.filter { it.id != "cloud_drift" }.forEach { t ->
            assertNull(t.id, t.art?.light?.sky)
            assertNull(t.id, t.art?.dark?.sky)
        }
    }

    @Test
    fun editedBgReachesThePageBottomAndEditedSkyTheTop() {
        val deco = checkNotNull(cloud.decoration)
        val edited = Color(0xFF123456)
        val w = 200
        val h = 400
        val bmp = ImageBitmap(w, h)
        val scope = CanvasDrawScope()
        scope.draw(Density(1f), LayoutDirection.Ltr, Canvas(bmp), Size(w.toFloat(), h.toFloat())) {
            with(deco.build(size, Density(1f), false, art, edited)) { draw(0f) }
        }
        val px = bmp.asAndroidBitmap()
        assertEquals(edited.toArgb(), px.getPixel(w - 2, h - 2))
        // stock bg gives the stock bottom colour
        val stock = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(stock), Size(w.toFloat(), h.toFloat())) {
            with(deco.build(size, Density(1f), false, art, Color(0xFFF3F8FD))) { draw(0f) }
        }
        assertEquals(0xFFF3F8FD.toInt(), stock.asAndroidBitmap().getPixel(w - 2, h - 2))
    }
}
