package com.shibi.commentarii.ui.settings

import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Checklist #242 (the setting survives closing the store and opening it again from its file) and #247 (what a "wt" write does to
 * a longer existing file, against what a plain "w" write does, on the file path Robolectric gives the app's export). Neither can
 * be a real process kill or a real Storage Access Framework provider; the screen-level halves are in SettingsAppFlowTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsStoreAndFileModeTest {
    @get:Rule val tmp = TemporaryFolder()

    /** A settings store on [file] with its own scope. [close] cancels the scope, which is as far as a test can go towards a kill. */
    private class Launch(file: File) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })

        fun close() {
            scope.cancel()
            runBlocking { scope.coroutineContext[Job]!!.join() }
        }
    }

    /**
     * Checklist #242: Off is written, the first store is closed, and a second store opened on the same file reads Off. Then On is
     * written through the second store and a third one reads On. Each answer differs from the default (On), so a store that
     * forgot would fail the first read.
     */
    @Test
    fun item242_exportReminderOffThenOnSurvivesClosingAndReopeningTheStore() {
        val file = File(tmp.root, "settings.preferences_pb")

        val first = Launch(file)
        assertTrue("default is On", runBlocking { first.settings.exportReminderEnabled.first() })
        runBlocking { first.settings.setExportReminderEnabled(false) }
        first.close()
        assertTrue("the file exists after the write", file.length() > 0)

        val second = Launch(file)
        assertFalse("Off survives", runBlocking { second.settings.exportReminderEnabled.first() })
        runBlocking { second.settings.setExportReminderEnabled(true) }
        second.close()

        val third = Launch(file)
        assertTrue("On survives", runBlocking { third.settings.exportReminderEnabled.first() })
        third.close()
    }

    /**
     * Checklist #247: the same short write over a longer file. Mode "w" leaves the old tail, mode "wt" does not, so the path under
     * the export really tells the two apart, and the app's "wt" (SettingsViewModel.exportTo) is what removes the junk.
     */
    @Test
    fun item247_wtTruncatesALongerFileAndPlainWDoesNot() {
        val resolver = ApplicationProvider.getApplicationContext<CommentariiApp>().contentResolver
        val short = "{\"ok\":true}"
        val junk = "JUNK".repeat(100)

        val plain = File(tmp.root, "plain.json").also { it.writeText(junk) }
        resolver.openOutputStream(Uri.fromFile(plain), "w")!!.use { it.write(short.toByteArray()) }
        assertEquals("w overwrites the front and keeps the tail", short + junk.substring(short.length), plain.readText())

        val truncated = File(tmp.root, "truncated.json").also { it.writeText(junk) }
        resolver.openOutputStream(Uri.fromFile(truncated), "wt")!!.use { it.write(short.toByteArray()) }
        assertEquals("wt leaves only what was written", short, truncated.readText())
    }
}
