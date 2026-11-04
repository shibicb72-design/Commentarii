package com.shibi.commentarii.testutil

import android.app.Dialog
import android.view.ViewGroup
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadows.ShadowDialog

/**
 * Gives wrap-content dialog windows a fixed width, so a dialog holding a text field can go idle. For such a window
 * ViewRootImpl.measureHierarchy measures at the preferred dialog width and then at the real one, on every traversal. A text
 * field's scroll state writes during layout, which makes the next measure pending. A device settles this when it draws, but
 * Robolectric never draws a dialog's window and Compose's idling strategy answers a pending measure with requestLayout, so
 * waitForIdle looped until AppNotIdleException. Registered for all tests in robolectric.properties.
 */
@Implements(Dialog::class)
class ShadowFixedWidthDialog : ShadowDialog() {
    @RealObject private lateinit var dialog: Dialog

    @Implementation
    override fun show() {
        val window = dialog.window
        if (window != null && window.attributes.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
            val density = dialog.context.resources.displayMetrics.density
            window.setLayout((WIDTH_DP * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        super.show()
    }

    private companion object {
        /** The minimum width of a Material dialog, which is what these windows already measured to. */
        const val WIDTH_DP = 280
    }
}
