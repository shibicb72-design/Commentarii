package com.shibi.commentarii.ui.settings

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.resetToDefaults
import com.shibi.commentarii.testutil.waitUntilWithLooper
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.io.ByteArrayInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Checklist #254, #255, #256: the words the user sees for a rejected file, on the real SettingsScreen. The host fetches the
 * same view model the screen uses, because the system file picker cannot run here. Data and alarms: SettingsBackupFlowTest.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class SettingsImportMessageTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val container get() = (rule.activity.application as CommentariiApp).container
    private val uri = Uri.parse("content://test/backup.json")
    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        container.settings.resetToDefaults()
        rule.setContent {
            CommentariiTheme(dark = false) {
                vm = viewModel(factory = SettingsViewModel.factory(container, ""))
                SettingsScreen(container)
            }
        }
        rule.waitForIdle()
    }

    @After
    fun tearDown() {
        container.settings.resetToDefaults()
    }

    private fun assertSnackbarFor(bytes: ByteArray, expected: String) {
        rule.onNodeWithText(rule.activity.getString(R.string.tab_settings)).assertExists()
        shadowOf(rule.activity.contentResolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        vm.importFrom(uri, rule.activity.contentResolver)
        rule.waitUntilWithLooper { rule.onAllNodes(hasText(expected)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun item254_snackbarForANonJsonFile() {
        assertEquals("That file is not a valid NoteToDo backup.", rule.activity.getString(R.string.settings_msg_import_bad_format))
        assertSnackbarFor(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()), "That file is not a valid NoteToDo backup.")
    }

    @Test
    fun item255_snackbarForAWrongVersion() =
        assertSnackbarFor("""{"formatVersion": 2}""".toByteArray(), "That file is not a valid NoteToDo backup.")

    @Test
    fun item256_snackbarForAnEmptyFile() = assertSnackbarFor(ByteArray(0), "That file is not a valid NoteToDo backup.")
}
