package com.shibi.commentarii.testutil

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ViewRootForTest
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowDialog

/**
 * Gives dialog windows the draw pass Robolectric leaves out, so a dialog holding a text field can go idle. A text field's measure
 * can leave a remeasure pending. On a device the next draw clears it, but Robolectric never draws a dialog's window, and
 * Compose's idling strategy answers a pending measure with requestLayout, so waitForIdle would loop until AppNotIdleException.
 * Checked directly: a root that had stayed pending until the timeout was clear after one such draw. Use with
 * `@Config(shadows = [ShadowDrawnDialog::class])`.
 */
@Implements(Dialog::class)
class ShadowDrawnDialog : ShadowDialog() {
    @RealObject private lateinit var dialog: Dialog

    @Implementation
    override fun show() {
        super.show()
        val decor = dialog.window?.decorView ?: return
        // Posted, because the remeasure only becomes pending after the traversal that fires this listener has finished.
        decor.viewTreeObserver.addOnGlobalLayoutListener {
            decor.post { composeRoots(decor).filter { it.hasPendingMeasureOrLayout }.forEach { it.view.draw(Canvas(PIXEL)) } }
        }
    }

    private fun composeRoots(view: View): List<ViewRootForTest> = when (view) {
        is ViewRootForTest -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { composeRoots(view.getChildAt(it)) }
        else -> emptyList()
    }

    private companion object {
        val PIXEL: Bitmap by lazy { Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888) }
    }
}
