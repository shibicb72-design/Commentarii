package com.shibi.commentarii.ui.daily

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.shibi.commentarii.R
import com.shibi.commentarii.data.SettingsRepository
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.domain.shouldShowExportReminder
import com.shibi.commentarii.testutil.DailyPageTestBase
import com.shibi.commentarii.testutil.runWithLooper
import com.shibi.commentarii.ui.AppNav
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.robolectric.Shadows.shadowOf

/** Checklist #92-#97: the export reminder and notifications-blocked banners on the real screen, with real settings. */
class DailyBannersFlowTest : DailyPageTestBase() {
    @get:Rule val tmp = TemporaryFolder()

    private val banner get() = str(R.string.reminder_text)
    private val dismiss get() = str(R.string.reminder_dismiss)
    private val blockedText get() = str(R.string.notif_blocked_message)
    private val blockedButton get() = str(R.string.notif_blocked_button)

    private fun bannerShown() = exists(hasText(banner))

    private fun blockedShown() = exists(hasText(blockedText))

    private fun seedData() = seedTasks(today, TaskSection.DONT_FORGET, "keep me")

    private fun daysAgo(days: Long): Instant = Instant.now().minus(Duration.ofDays(days))

    /** Waits for the page's check to be over: the anchor, then real time for the banner's own flow to settle. */
    private fun assertNoBannerAfterLoad() {
        awaitLoadAnchor()
        settleFor(400)
        assertFalse("no export banner", bannerShown())
    }

    private fun setSetting(block: suspend SettingsRepository.() -> Unit) {
        val settings = container.settings
        runWithLooper { settings.block() }
    }

    private fun openWithAnchor(date: LocalDate = today) {
        seedLoadAnchor(date)
        open(date)
    }

    @Test
    fun item092_bannerWithDismissShowsAtTheTopWithDataAndNoExport() {
        seedData()
        open(today)
        await { bannerShown() }
        rule.onNode(hasText(dismiss)).assertExists()
        assertTrue(
            "above the header",
            bounds(rule.onNode(hasText(banner))).top < bounds(rule.onNode(hasText(str(R.string.daily_no_folder)))).top,
        )
    }

    @Test
    fun item092_onlyOnTodaysPage() {
        seedData()
        openWithAnchor(today.minusDays(1))
        assertNoBannerAfterLoad()

        goToDateWithPicker(today)
        await { bannerShown() }
    }

    @Test
    fun item092_followsTheLastExportAndDismissalAges() {
        seedData()
        openWithAnchor()
        await { bannerShown() }

        setSetting { setLastExportAt(daysAgo(3)) }
        await { !bannerShown() }

        setSetting { setLastExportAt(daysAgo(15)) }
        await { bannerShown() }

        setSetting { setReminderDismissedAt(daysAgo(3)) }
        await { !bannerShown() }

        setSetting { setReminderDismissedAt(daysAgo(15)) }
        await { bannerShown() }
    }

    @Test
    fun item093_dismissHidesItAndItStaysAwayOnReopeningFor14Days() {
        seedData()
        openWithAnchor()
        await { bannerShown() }

        tapInDialog(dismiss)
        await { !bannerShown() }
        val settings = container.settings
        val dismissedAt = runWithLooper { settings.reminderDismissedAt.first() }
        assertNotNull("recorded", dismissedAt)
        assertTrue("recorded as now", abs(Duration.between(dismissedAt, Instant.now()).toMinutes()) < 2)

        reopen()
        assertNoBannerAfterLoad()

        setSetting { setReminderDismissedAt(daysAgo(13)) }
        reopen()
        assertNoBannerAfterLoad()

        setSetting { setReminderDismissedAt(daysAgo(15)) }
        reopen()
        await { bannerShown() }
    }

    /** "Also after relaunch": the time is read back by a second store on the same file, and it still silences the banner. */
    @Test
    fun item093_dismissalSurvivesARelaunchOfTheSettingsStore() = runBlocking {
        val file = File(tmp.root, "settings.preferences_pb")
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = SettingsRepository(PreferenceDataStoreFactory.create(scope = firstScope) { file })
        val at = Instant.parse("2031-03-12T09:00:00Z")
        first.setReminderDismissedAt(at)
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val second = SettingsRepository(PreferenceDataStoreFactory.create(scope = secondScope) { file })
        val read = second.reminderDismissedAt.first()
        secondScope.cancel()

        assertEquals(at, read)
        assertFalse(shouldShowExportReminder(true, true, null, read, at.plus(Duration.ofDays(13))))
        assertTrue(shouldShowExportReminder(true, true, null, read, at.plus(Duration.ofDays(14))))
    }

    /** The export button's file picker answers at once with this uri, as the system's would after the user chose a file. */
    private val backupUri = Uri.parse("content://test/backup.json")

    private val filePicker = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?,
            ) {
                dispatchResult(requestCode, backupUri)
            }
        }
    }

    private fun openAppWithFilePicker() {
        rule.setContent {
            CommentariiTheme(dark = false) {
                colors = MaterialTheme.colorScheme
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides filePicker) {
                    AppNav(container, WindowWidthSizeClass.Compact)
                }
            }
        }
        awaitLoaded()
    }

    @Test
    fun item094_exportingFromSettingsTakesTheBannerAway() {
        val written = ByteArrayOutputStream()
        shadowOf(rule.activity.contentResolver).registerOutputStream(backupUri, written)
        seedData()
        openAppWithFilePicker()
        await { bannerShown() }

        tapTab(R.string.tab_settings)
        rule.onNode(hasText(str(R.string.settings_export))).performScrollTo().performClick()
        await { exists(hasText(str(R.string.settings_msg_export_ok))) }
        assertTrue("a backup was written", written.size() > 0)

        tapTab(R.string.tab_today)
        awaitLoaded()
        settleFor(400)
        assertFalse(bannerShown())
    }

    @Test
    fun item095_turningTheReminderOffInSettingsHidesTheBanner() {
        seedData()
        openApp()
        await { bannerShown() }

        tapTab(R.string.tab_settings)
        val title = rule.onNode(hasText(str(R.string.settings_export_reminder))).performScrollTo()
        rule.waitForIdle()
        checkOf(title).performClick()
        val settings = container.settings
        await { !runWithLooper { settings.exportReminderEnabled.first() } }

        tapTab(R.string.tab_today)
        awaitLoaded()
        settleFor(400)
        assertFalse(bannerShown())
    }

    @Test
    fun item096_noDataNoBannerUntilThereIsData() {
        openWithAnchor()
        assertNoBannerAfterLoad()

        // Control: everything else equal, a line of text brings it on the next load.
        field(str(R.string.daily_priority_hint, 1)).performTextInput("something")
        await { texts(today, TaskSection.PRIORITY) == listOf("something") }
        reopen()
        await { bannerShown() }
    }

    private fun blockNotifications() {
        shadowOf(rule.activity.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        container.refreshNotificationState()
        rule.waitForIdle()
    }

    @Test
    fun item097_blockedNotificationsShowTheErrorBannerAndItsButtonOpensSettings() {
        openWithAnchor()
        awaitLoadAnchor()
        assertFalse("control: not while notifications are on", blockedShown())

        blockNotifications()
        await { blockedShown() }
        rule.onNode(hasText(blockedButton)).assertExists()
        // 6 dp to the left of the message is inside the banner's 12 dp padding.
        val fill = pixel(rule.onNode(hasText(blockedText)), -6f, 2f)
        assertSameColor("error colour", colors.errorContainer, fill)

        rule.onNode(hasText(blockedButton)).performClick()
        rule.waitForIdle()
        val started: Intent = shadowOf(rule.activity).nextStartedActivity
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, started.action)
        assertEquals(rule.activity.packageName, started.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun item097_theExportBannerWinsWhenBothApply() {
        seedData()
        open(today)
        await { bannerShown() }
        blockNotifications()
        settleFor(300)
        assertTrue(bannerShown())
        assertFalse("export banner wins", blockedShown())

        tapInDialog(dismiss)
        await { !bannerShown() && blockedShown() }
    }
}
