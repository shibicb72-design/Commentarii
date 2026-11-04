package com.shibi.commentarii.ui.settings

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ContentResolver
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.shibi.commentarii.AppContainer
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.R
import com.shibi.commentarii.data.DarkMode
import com.shibi.commentarii.data.TaskItemEntity
import com.shibi.commentarii.data.TaskSection
import com.shibi.commentarii.notify.AlarmScheduler
import com.shibi.commentarii.notify.Notifications
import com.shibi.commentarii.testutil.MutableClock
import com.shibi.commentarii.testutil.TestEnv
import com.shibi.commentarii.testutil.resetToDefaults
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/**
 * Checklist #127, #134, #246, #254, #255, #256: export, import and the test reminder through the real SettingsViewModel and
 * container. Each backup file comes from a separate [TestEnv], without its settings block, so it cannot touch the shared
 * settings store. The failure message's on-screen text is covered by SettingsImportMessageTest.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SettingsBackupFlowTest {
    @get:Rule val tmp = TemporaryFolder()

    private val app = ApplicationProvider.getApplicationContext<CommentariiApp>()
    private val container: AppContainer = app.container
    private val resolver: ContentResolver = app.contentResolver
    private val alarmManager = app.getSystemService(AlarmManager::class.java)
    private val uri = Uri.parse("content://test/backup.json")
    private val tomorrow = LocalDate.now(container.clock).plusDays(1)
    private val collectors = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val envs = mutableListOf<TestEnv>()

    private lateinit var vm: SettingsViewModel

    @Before
    fun setUp() {
        container.settings.resetToDefaults()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        vm = SettingsViewModel(container, "")
        collectors.launch { vm.state.collect {} }
    }

    @After
    fun tearDown() {
        collectors.cancel()
        envs.forEach { it.close() }
        container.settings.resetToDefaults()
    }

    private fun awaitUntil(what: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    private fun awaitMessage(): SettingsMessage {
        awaitUntil("a settings message") { vm.state.value.message != null }
        return vm.state.value.message!!
    }

    private fun alarms() = shadowOf(alarmManager).scheduledAlarms

    @Suppress("DEPRECATION")
    private fun uriOf(a: ShadowAlarmManager.ScheduledAlarm) = shadowOf(a.operation!!).savedIntent.data.toString()

    @Suppress("DEPRECATION")
    private fun textOf(a: ShadowAlarmManager.ScheduledAlarm) =
        shadowOf(a.operation!!).savedIntent.getStringExtra(Notifications.EXTRA_TEXT)

    private fun notifications() = shadowOf(app.getSystemService(NotificationManager::class.java)).allNotifications

    private fun ms(date: LocalDate, hour: Int) =
        date.atTime(LocalTime.of(hour, 0)).atZone(container.zone).toInstant().toEpochMilli()

    /** Existing data with one pending reminder, saved the way the app saves it so the alarm is really registered. */
    private fun seedExistingData(): Long = runBlocking {
        val page = container.pages.getOrCreateDaily(tomorrow)
        container.pages.saveTasks(page.id, TaskSection.TODO, listOf(TaskItemEntity(pageId = page.id, section = "", position = 0, text = "Keep me")))
        val rows = container.pages.getSchedule(page.id)
        val saved = container.pages.saveSchedule(page.id, rows.map { if (it.minuteOfDay == 10 * 60) it.copy(text = "Dentist") else it })
        saved.first { it.minuteOfDay == 10 * 60 }.id
    }

    private fun snapshot(): List<Any> = runBlocking {
        val d = container.db.pageDao()
        listOf(d.getAllPages(), d.getAllTasks(), d.getAllSchedule(), d.getAllBlocks(), d.getAllMoods())
    }

    private fun importBytes(bytes: ByteArray): SettingsMessage {
        shadowOf(resolver).registerInputStream(uri, ByteArrayInputStream(bytes))
        vm.importFrom(uri, resolver)
        return awaitMessage()
    }

    /**
     * A valid file from another store: page dated [date] with [text] at 14:00. No settings block (see class doc). Written through
     * the DAO, because a repository save would reach the real container's alarms through the shared AlarmManager (see TestEnv).
     */
    private fun backupFile(date: LocalDate, text: String = "Imported reminder"): JSONObject = runBlocking {
        val env = TestEnv(tmp, MutableClock(LocalDate.now(container.clock).atTime(9, 0))).also { envs += it }
        val page = env.pages.getOrCreateDaily(date)
        val rows = env.pages.getSchedule(page.id)
        env.db.pageDao().upsertSchedule(rows.map { if (it.minuteOfDay == 14 * 60) it.copy(text = text) else it })
        JSONObject(env.io.export()).also { it.remove("settings") }
    }

    /** Rejected file: the right message, no data changed, and the pending reminder still registered at its time. */
    private fun assertRejected(bytes: ByteArray, expected: SettingsMessage) {
        val id = seedExistingData()
        val before = snapshot()
        assertEquals("commentarii://alarm/$id", uriOf(alarms().single()))

        assertEquals(expected, importBytes(bytes))

        assertEquals(before, snapshot())
        assertEquals("alarms after a rejected import", 1, alarms().size)
        assertEquals("commentarii://alarm/$id", uriOf(alarms().single()))
        assertEquals(ms(tomorrow, 10), alarms().single().triggerAtMs)
        assertEquals("Dentist", textOf(alarms().single()))
    }

    @Test
    fun item254_textFileIsRejected() =
        assertRejected("just some notes\nnot a backup".toByteArray(), SettingsMessage.IMPORT_BAD_FORMAT)

    /** Not valid UTF-8: starts like a JPEG, and carries bytes that can never appear in UTF-8. */
    @Test
    fun item254_nonUtf8BytesAreRejected() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0xC0.toByte(), 0xFE.toByte())
        val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        assertTrue("fixture must not be UTF-8", runCatching { decoder.decode(ByteBuffer.wrap(bytes)) }.isFailure)
        assertRejected(bytes, SettingsMessage.IMPORT_BAD_FORMAT)
    }

    @Test
    fun item255_wrongFormatVersionIsRejected() {
        val file = backupFile(tomorrow.plusDays(1)).put("formatVersion", 2)
        assertRejected(file.toString().toByteArray(), SettingsMessage.IMPORT_BAD_FORMAT)
    }

    @Test
    fun item256_emptyFileIsRejected() = assertRejected(ByteArray(0), SettingsMessage.IMPORT_BAD_FORMAT)

    /** Well-formed file that SQLite refuses part way through: the transaction rolls back and the alarms must stay too. */
    @Test
    fun item256_fileFailingInsideTheTransactionChangesNothing() {
        val file = backupFile(tomorrow.plusDays(1))
        file.getJSONArray("tasks").put(
            JSONObject().put("id", 99).put("pageId", 987_654).put("section", TaskSection.TODO)
                .put("position", 0).put("text", "orphan").put("done", false).put("carriedTo", JSONObject.NULL),
        )
        assertRejected(file.toString().toByteArray(), SettingsMessage.IMPORT_FAILED)
    }

    @Test
    fun item134_importCancelsOldAlarmsAndRegistersTheImportedOnes() {
        val oldId = seedExistingData()
        val date = tomorrow.plusDays(1)
        val file = backupFile(date)
        assertEquals("the old alarm is still there until the import", "commentarii://alarm/$oldId", uriOf(alarms().single()))

        assertEquals(SettingsMessage.IMPORT_OK, importBytes(file.toString().toByteArray()))

        val imported = runBlocking {
            container.pages.getSchedule(container.pages.getOrCreateDaily(date).id).first { it.minuteOfDay == 14 * 60 }
        }
        assertTrue("fixture needs a new id for the old alarm to be stale", imported.id != oldId)
        val alarm = alarms().single()
        assertEquals("commentarii://alarm/${imported.id}", uriOf(alarm))
        assertEquals(ms(date, 14), alarm.triggerAtMs)
        assertEquals("Imported reminder", textOf(alarm))
        assertTrue(container.debugLog.readAll().contains("RESCHEDULE_ALL reason=settings"))

        shadowOf(alarmManager).fireAlarm(alarm)
        shadowOf(Looper.getMainLooper()).idle()
        val posted = notifications().single().extras.getCharSequence("android.text").toString()
        assertTrue(posted, posted.endsWith("Imported reminder"))
    }

    /** Row ids restart in the file, so an old alarm's id can come back as a blank imported row. That alarm must not stay. */
    @Test
    fun item134_oldIdReusedByABlankImportedRowDoesNotKeepRinging() {
        val oldId = seedExistingData()
        val file = backupFile(tomorrow.plusDays(1))
        val rows = file.getJSONArray("schedule")
        val reused = (0 until rows.length()).map { rows.getJSONObject(it) }.first { it.getLong("id") == oldId }
        assertEquals("the fixture's row with the old id is blank", "", reused.getString("text"))
        assertEquals("the old alarm is still there until the import", "commentarii://alarm/$oldId", uriOf(alarms().single()))

        assertEquals(SettingsMessage.IMPORT_OK, importBytes(file.toString().toByteArray()))
        assertFalse(alarms().map { uriOf(it) }.contains("commentarii://alarm/$oldId"))
        assertEquals("Imported reminder", textOf(alarms().single()))
    }

    @Test
    fun item246_exportedFileIsUtf8JsonWithEverySectionAndNoLock() {
        val emoji = "Party 🎉 Ünï"
        runBlocking {
            val page = container.pages.getOrCreateDaily(tomorrow)
            container.pages.saveTasks(page.id, TaskSection.TODO, listOf(TaskItemEntity(pageId = page.id, section = "", position = 0, text = emoji)))
            val rows = container.pages.getSchedule(page.id)
            container.pages.saveSchedule(page.id, rows.map { if (it.minuteOfDay == 10 * 60) it.copy(text = "Dentist") else it })
            container.pages.saveMood(tomorrow, 4)
            container.pages.moveToFolder(page.id, container.pages.createFolder("Work").id)
            container.pages.addTag(page.id, container.pages.getOrCreateTag("urgent").id)
            container.settings.setDarkMode(DarkMode.DARK)
            container.settings.setExportReminderEnabled(false)
            container.settings.setLockEnabled(true)
        }
        val out = ByteArrayOutputStream()
        shadowOf(resolver).registerOutputStream(uri, out)

        vm.exportTo(uri, resolver)
        assertEquals(SettingsMessage.EXPORT_OK, awaitMessage())

        val bytes = out.toByteArray()
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        val needle = emoji.toByteArray(Charsets.UTF_8).toList()
        assertTrue("emoji bytes intact", bytes.toList().windowed(needle.size).any { it == needle })

        val root = JSONObject(text)
        assertEquals(1, root.getInt("formatVersion"))
        for (key in listOf("pages", "tasks", "schedule", "moods", "folders", "tags")) {
            assertTrue("$key is empty", root.getJSONArray(key).length() > 0)
        }
        assertEquals(emoji, root.getJSONArray("tasks").getJSONObject(0).getString("text"))
        val settings = root.getJSONObject("settings")
        assertEquals("DARK", settings.getString("darkMode"))
        assertFalse(settings.getBoolean("exportReminderEnabled"))
        assertTrue(runBlocking { container.settings.lockEnabled.first() })
        assertFalse(text.contains("lockEnabled"))
        assertTrue(settings.keys().asSequence().none { it.contains("lock", ignoreCase = true) })
    }

    @Test
    fun item127_threeQuickTestRemindersGiveOneAlarmAndOneNotification() {
        repeat(3) { vm.sendTestReminder() }
        assertEquals(SettingsMessage.TEST_SCHEDULED, awaitMessage())

        val alarm = alarms().single()
        assertEquals("commentarii://alarm/${AlarmScheduler.TEST_ENTRY_ID}", uriOf(alarm))
        val log = container.debugLog.readAll()
        assertEquals("every tap cancelled the one before", 3, Regex("CANCEL id=-1").findAll(log).count())

        shadowOf(alarmManager).fireAlarm(alarm)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, notifications().size)
        assertEquals(
            app.getString(R.string.notif_now, app.getString(R.string.notif_test_text)),
            notifications().single().extras.getCharSequence("android.text").toString(),
        )
    }
}
