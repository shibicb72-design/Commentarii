package com.shibi.commentarii.ui.lock

import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performClick
import com.shibi.commentarii.R
import com.shibi.commentarii.testutil.MainActivityTestBase
import com.shibi.commentarii.testutil.runWithLooper
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowBiometricManager

/**
 * Checklist #34-#36 on the real MainActivity, with ShadowBiometricManager standing in for what the device has enrolled. A shown
 * system prompt is recognised by the BiometricFragment that androidx.biometric adds for it. No real fingerprint or screen lock
 * is involved, and what a phone reports once its lock is removed is not exercised.
 */
class LockUnavailableFlowTest : MainActivityTestBase() {
    private val unlock get() = str(R.string.lock_unlock)
    private val explanation get() = str(R.string.lock_unavailable_explanation)
    private val openSecurity get() = str(R.string.lock_open_security_settings)
    private val dialogTitle get() = str(R.string.lock_unavailable_title)
    private val notNow get() = str(R.string.lock_dialog_close)

    /** The switch on the lock setting's row: a row holds no semantic link between its title and its switch, only a position. */
    private val lockSwitch: SemanticsNodeInteraction
        get() {
            val title = rule.onNode(hasText(str(R.string.settings_lock))).fetchSemanticsNode().boundsInRoot.center.y
            val nodes = rule.onAllNodes(isToggleable()).fetchSemanticsNodes()
            return rule.onAllNodes(isToggleable())[nodes.indices.minBy { abs(nodes[it].boundsInRoot.center.y - title) }]
        }

    private fun setAuthAvailable(available: Boolean) {
        Shadow.extract<ShadowBiometricManager>(rule.activity.getSystemService(BiometricManager::class.java)).setCanAuthenticate(available)
    }

    private fun promptShown() =
        rule.activity.supportFragmentManager.findFragmentByTag("androidx.biometric.BiometricFragment") != null

    private fun lockEnabled() = runWithLooper { container.settings.lockEnabled.first() }

    /** The app comes back locked: the setting is on and nothing has unlocked this container's fresh AppLock. */
    private fun showLockScreen() {
        runWithLooper { container.settings.setLockEnabled(true) }
        await { exists(unlock) }
    }

    private fun assertLockedWithExplanation() {
        assertTrue(container.lock.locked.value)
        assertTrue("explanation", exists(explanation))
        assertTrue("security settings button", exists(openSecurity))
        assertTrue("Unlock button", exists(unlock))
        assertFalse("the app stays hidden", exists(tab(R.string.tab_today)))
    }

    private fun openLockSetting() {
        openTab(R.string.tab_settings)
        await { exists(str(R.string.settings_lock)) }
    }

    private fun lastStartedIntent(): Intent? = shadowOf(rule.activity).nextStartedActivity

    @Test
    fun item034_nothingEnrolledExplainsAndUnlockNeverUnlocks() {
        setAuthAvailable(false)
        leaveAndReturn()
        showLockScreen()
        assertLockedWithExplanation()
        assertEquals(
            "No fingerprint or screen lock is set up on this device, so NoteToDo cannot check who is opening it. Set one up in your device settings, then come back.",
            explanation,
        )

        repeat(2) {
            tapText(unlock)
            assertLockedWithExplanation()
            assertFalse("nothing to prompt with", promptShown())
        }
    }

    @Test
    fun item034_positiveControlWithALockSetUpThereIsNoExplanationAndAPromptInstead() {
        setAuthAvailable(true)
        leaveAndReturn()
        showLockScreen()
        await { promptShown() }
        assertTrue(exists(unlock))
        assertFalse(exists(explanation))
        assertFalse(exists(openSecurity))
        assertTrue("showing the prompt does not unlock", container.lock.locked.value)
    }

    @Test
    fun item035_buttonOpensEnrolmentAndReturningReChecksAvailability() {
        setAuthAvailable(false)
        leaveAndReturn()
        showLockScreen()
        assertLockedWithExplanation()

        tapText(openSecurity)
        val intent = checkNotNull(lastStartedIntent())
        assertEquals(Settings.ACTION_BIOMETRIC_ENROLL, intent.action)
        assertEquals(
            BIOMETRIC_WEAK or DEVICE_CREDENTIAL,
            intent.getIntExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, 0),
        )

        // A lock was set up on that page. Nothing can notice until the activity comes back.
        setAuthAvailable(true)
        rule.waitForIdle()
        assertLockedWithExplanation()
        assertFalse(promptShown())

        leaveAndReturn()
        await { !exists(explanation) }
        assertFalse(exists(openSecurity))
        assertTrue(exists(unlock))
        await { promptShown() }
        assertTrue("still locked until the prompt succeeds", container.lock.locked.value)

        // And the other way: the lock was removed while the app was away.
        setAuthAvailable(false)
        leaveAndReturn()
        await { exists(explanation) }
        assertLockedWithExplanation()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.Q], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
    fun item035_belowApi30TheButtonOpensSecuritySettings() {
        setAuthAvailable(false)
        leaveAndReturn()
        showLockScreen()
        assertLockedWithExplanation()
        tapText(openSecurity)
        assertEquals(Settings.ACTION_SECURITY_SETTINGS, checkNotNull(lastStartedIntent()).action)
    }

    @Test
    fun item036_nothingEnrolledShowsTheDialogAndLeavesTheSwitchOff() {
        setAuthAvailable(false)
        openLockSetting()
        lockSwitch.assertIsOff()

        lockSwitch.performClick()
        await { exists(dialogTitle) }
        assertEquals("Set up a screen lock", dialogTitle)
        val inDialog = hasAnyAncestor(isDialog())
        assertTrue(exists(hasText(explanation) and inDialog))
        assertTrue(exists(hasText(openSecurity) and inDialog))
        assertTrue(exists(hasText(notNow) and inDialog))
        lockSwitch.assertIsOff()
        assertFalse(lockEnabled())
        assertFalse("no prompt behind it", promptShown())

        tapText(notNow)
        assertFalse(exists(dialogTitle))
        assertNull("Not now opens nothing", lastStartedIntent())
        lockSwitch.assertIsOff()
        assertFalse(lockEnabled())

        lockSwitch.performClick()
        await { exists(dialogTitle) }
        tapText(openSecurity)
        assertFalse(exists(dialogTitle))
        assertEquals(Settings.ACTION_BIOMETRIC_ENROLL, checkNotNull(lastStartedIntent()).action)
        lockSwitch.assertIsOff()
        assertFalse(lockEnabled())
    }

    @Test
    fun item036_withALockSetUpThereIsNoDialogAndThePromptOpens() {
        setAuthAvailable(true)
        openLockSetting()
        lockSwitch.assertIsOff()

        lockSwitch.performClick()
        await { promptShown() }
        assertFalse(exists(dialogTitle))
        assertFalse(exists(hasText(notNow)))
        lockSwitch.assertIsOff()
        assertFalse("nothing changes before the prompt succeeds", lockEnabled())
    }
}
