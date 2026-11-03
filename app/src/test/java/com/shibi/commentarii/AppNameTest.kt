package com.shibi.commentarii

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The user-visible name is "NoteToDo"; the Play application ID is com.shibi.notetodo (namespace and Kotlin packages stay com.shibi.commentarii). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNameTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun appNameIsNoteToDo() {
        assertEquals("NoteToDo", context.getString(R.string.app_name))
    }

    @Test fun launcherLabelIsNoteToDo() {
        val pm = context.packageManager
        assertEquals("NoteToDo", context.applicationInfo.loadLabel(pm).toString())
        val activity = pm.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        assertEquals("NoteToDo", activity.loadLabel(pm).toString())
    }

    @Test fun noStringResourceSaysCommentarii() {
        val offenders = R.string::class.java.fields
            .map { it.name to context.getString(it.getInt(null)) }
            .filter { (_, text) -> text.contains("Commentarii", ignoreCase = true) }
        assertTrue("strings still naming the old app: $offenders", offenders.isEmpty())
    }

    @Test fun applicationIdIsNoteToDo() {
        assertEquals("com.shibi.notetodo", context.packageName)
        assertEquals("com.shibi.notetodo", context.applicationInfo.packageName)
    }
}
