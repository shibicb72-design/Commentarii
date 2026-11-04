package com.shibi.commentarii.ui.daily

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.shibi.commentarii.R
import com.shibi.commentarii.notify.Notifications
import com.shibi.commentarii.ui.theme.CommentariiTheme
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Item 304: times must follow the phone's 12/24-hour setting (Settings.System.TIME_12_24, set in the Robolectric
 * sandbox only) in the daily schedule, the custom-time picker and the reminder notification.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "en-rUS-w412dp-h915dp-xxhdpi")
class TimeFormatSettingTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val savedZone: TimeZone = TimeZone.getDefault()

    @After
    fun restoreZone() {
        TimeZone.setDefault(savedZone)
    }

    private fun setHourFormat(value: String) {
        Settings.System.putString(rule.activity.contentResolver, Settings.System.TIME_12_24, value)
    }

    /** Newer locale data puts U+202F or U+00A0 before AM/PM; the separator is not what these tests check. */
    private fun plainSpaces(text: String) = text.replace(' ', ' ').replace(' ', ' ')

    private fun hasTime(expected: String) = SemanticsMatcher("shows time \"$expected\"") { node ->
        node.config.getOrElse(SemanticsProperties.Text) { emptyList() }.any { plainSpaces(it.text) == expected }
    }

    private fun showSchedule(vararg minutes: Int) {
        rule.setContent {
            CommentariiTheme(dark = false) {
                ScheduleCard(
                    minutes.map { ScheduleRowUi(it.toLong(), it) },
                    onText = { _, _ -> },
                    onAddCustomTime = {},
                    onRemoveCustomTime = {},
                )
            }
        }
        rule.waitForIdle()
    }

    private fun assertScheduleShows(vararg times: String) {
        times.forEach { rule.onNode(hasTime(it), useUnmergedTree = true).assertExists() }
    }

    private fun reminder(minuteOfDay: Int) =
        plainSpaces(Notifications.reminderText(rule.activity, "Dentist", minuteOfDay))

    private fun openTimePicker() {
        rule.onNodeWithText(rule.activity.getString(R.string.daily_custom_time)).performClick()
        rule.waitForIdle()
    }

    @Test
    fun scheduleUses24HourWhenSettingIs24() {
        setHourFormat("24")
        showSchedule(13 * 60, 5)
        assertScheduleShows("13:00", "00:05")
    }

    @Test
    fun scheduleUses12HourWhenSettingIs12() {
        setHourFormat("12")
        showSchedule(13 * 60, 5)
        assertScheduleShows("1:00 PM", "12:05 AM")
    }

    @Test
    fun reminderUses24HourWhenSettingIs24() {
        setHourFormat("24")
        assertEquals("Now (13:00): Dentist", reminder(13 * 60))
    }

    @Test
    fun reminderUses12HourWhenSettingIs12() {
        setHourFormat("12")
        assertEquals("Now (1:00 PM): Dentist", reminder(13 * 60))
    }

    @Test
    fun timePickerUses24HourWhenSettingIs24() {
        setHourFormat("24")
        showSchedule(13 * 60)
        openTimePicker()
        rule.onAllNodesWithText("PM").assertCountEquals(0)
    }

    @Test
    fun timePickerUses12HourWhenSettingIs12() {
        setHourFormat("12")
        showSchedule(13 * 60)
        openTimePicker()
        rule.onAllNodesWithText("PM").assertCountEquals(1)
    }

    /** A minute of day is a wall-clock label: it must not move with the phone's zone. */
    @Test
    fun timeDoesNotShiftWithTimeZone() {
        setHourFormat("24")
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
        assertEquals("Now (13:00): Dentist", reminder(13 * 60))
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"))
        assertEquals("Now (00:05): Dentist", reminder(5))
    }

    /** With no explicit choice the locale decides, as Android's own clock does. */
    @Test
    @Config(qualifiers = "de-rDE-w412dp-h915dp-xxhdpi")
    fun unsetSettingFollowsLocale() {
        assertEquals("Now (13:00): Dentist", reminder(13 * 60))
    }
}
