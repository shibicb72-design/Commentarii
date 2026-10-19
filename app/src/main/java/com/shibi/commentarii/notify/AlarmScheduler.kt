package com.shibi.commentarii.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.shibi.commentarii.R
import com.shibi.commentarii.data.ScheduleEntryEntity
import com.shibi.commentarii.data.ScheduleListener
import com.shibi.commentarii.debug.DebugLog
import com.shibi.commentarii.domain.Alarm
import com.shibi.commentarii.domain.AlarmKind
import com.shibi.commentarii.domain.classifyEntry
import com.shibi.commentarii.domain.selectAlarms
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What happened to one entry when it was scheduled; only IMMEDIATE and PAST are worth telling the user about. */
data class ScheduleOutcome(val entryId: Long, val kind: AlarmKind)

/** Registers one AlarmManager alarm per schedule entry, 10 minutes before the entry time. */
class AlarmScheduler(
    private val context: Context,
    private val clock: Clock,
    private val debugLog: DebugLog = context.debugLog(),
) : ScheduleListener {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val reportedPast = HashSet<String>()
    private val _outcomes = MutableSharedFlow<ScheduleOutcome>(extraBufferCapacity = 8)

    /** IMMEDIATE / PAST outcomes of edits (not of reschedule-all) for entries with text. */
    val outcomes: SharedFlow<ScheduleOutcome> = _outcomes.asSharedFlow()

    /** True when exact alarms may be set (always before API 31, where no permission exists). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun onScheduleChanged(touchedIds: Collection<Long>, entries: List<ScheduleEntryEntity>) {
        try {
            cancel(touchedIds)
            for (o in schedule(entries)) {
                if (o.kind == AlarmKind.IMMEDIATE || o.kind == AlarmKind.PAST) _outcomes.tryEmit(o)
            }
        } catch (t: Throwable) {
            debugLog.log("ERROR", "onScheduleChanged failed: $t")
        }
    }

    /**
     * Registers alarms for the selectable [entries] and reports, per entry with text, what happened: SCHEDULED,
     * IMMEDIATE (newly set to fire within seconds) or PAST (first time seen). An immediate alarm already
     * registered for the same entry/time/text is not repeated (so boot or resume does not re-fire it) and is omitted.
     */
    fun schedule(entries: List<ScheduleEntryEntity>): List<ScheduleOutcome> = scheduleCounting(entries).first

    private fun scheduleCounting(entries: List<ScheduleEntryEntity>): Pair<List<ScheduleOutcome>, Int> {
        val now = clock.instant()
        val exact = canScheduleExact()
        val outcomes = ArrayList<ScheduleOutcome>()
        var registered = 0
        val selected = selectAlarms(entries, now, clock.zone)
        val selectedIds = selected.map { it.entryId }.toSet()
        for (alarm in selected) {
            if (alarm.immediate) {
                val key = immediateKey(alarm)
                val fired = prefs.getStringSet(KEY_FIRED, emptySet()).orEmpty()
                if (key in fired) {
                    debugLog.log("SCHEDULE", "id=${alarm.entryId} immediate already used, skipped")
                    continue
                }
                prefs.edit().putStringSet(KEY_FIRED, (fired + key).toList().takeLast(MAX_KEYS).toSet()).apply()
            }
            val ok = register(
                alarm.entryId,
                alarm.date.toString(),
                alarm.text,
                alarm.triggerAt,
                exact,
                alarm.immediate,
                alarm.minuteOfDay,
            )
            if (ok) {
                registered++
                val kind = if (alarm.immediate) AlarmKind.IMMEDIATE else AlarmKind.SCHEDULED
                outcomes += ScheduleOutcome(alarm.entryId, kind)
            }
        }
        for (e in entries) {
            if (e.id == 0L || e.id in selectedIds) continue
            val past = classifyEntry(e.date, e.minuteOfDay, e.text, clock.zone, now) == AlarmKind.PAST
            if (past && reportedPast.add("${e.id}|${e.date}|${e.minuteOfDay}|${e.text.hashCode()}")) {
                debugLog.log("SCHEDULE", "id=${e.id} date=${e.date} minute=${e.minuteOfDay} past, no alarm")
                outcomes += ScheduleOutcome(e.id, AlarmKind.PAST)
            }
        }
        return outcomes to registered
    }

    /** Fires a notification 10 s from now through the normal alarm path (entry id [TEST_ENTRY_ID]). */
    fun scheduleTestReminder() {
        try {
            cancel(listOf(TEST_ENTRY_ID))
            val at = clock.instant().plusSeconds(TEST_DELAY_SECONDS)
            register(
                TEST_ENTRY_ID,
                LocalDate.now(clock).toString(),
                context.getString(R.string.notif_test_text),
                at,
                canScheduleExact(),
                false,
                -1,
            )
        } catch (t: Throwable) {
            debugLog.log("ERROR", "scheduleTestReminder failed: $t")
        }
    }

    private fun register(
        entryId: Long,
        date: String,
        text: String,
        at: Instant,
        exact: Boolean,
        immediate: Boolean,
        minuteOfDay: Int,
    ): Boolean {
        val pi = pendingIntent(entryId, date, text, create = true) ?: return false
        val ms = at.toEpochMilli()
        var usedExact = exact
        try {
            if (exact) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
        } catch (_: SecurityException) {
            usedExact = false
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
        debugLog.log(
            "SCHEDULE",
            "id=$entryId date=$date minute=$minuteOfDay triggerAt=${LOCAL.format(at.atZone(clock.zone))} " +
                "${if (usedExact) "exact" else "inexact"} immediate=$immediate textLen=${text.length}",
        )
        return true
    }

    fun cancel(entryIds: Collection<Long>) {
        for (id in entryIds) {
            val pi = pendingIntent(id, "", "", create = false)
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
            debugLog.log("CANCEL", "id=$id existed=${pi != null}")
        }
    }

    /** Cancels then re-registers; used after boot, time/zone change, app update, resume and permission change. */
    fun rescheduleAll(entries: List<ScheduleEntryEntity>, reason: String = "") {
        try {
            cancel(entries.map { it.id })
            val count = scheduleCounting(entries).second
            debugLog.log(
                "RESCHEDULE_ALL",
                "reason=$reason entries=${entries.size} registered=$count exact=${canScheduleExact()}",
            )
        } catch (t: Throwable) {
            debugLog.log("ERROR", "rescheduleAll failed: $t")
        }
    }

    private fun immediateKey(a: Alarm) = "${a.entryId}|${a.date}|${a.minuteOfDay}|${a.text.hashCode()}"

    /**
     * Per-entry identity: action + data URI make the Intents differ under filterEquals, and the request code is the
     * entry id itself (ids are small autogenerated Longs, so toInt() is lossless; the test reminder uses -1).
     */
    private fun pendingIntent(entryId: Long, date: String, text: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .setData(Uri.parse("commentarii://alarm/$entryId"))
        if (create) {
            intent.putExtra(Notifications.EXTRA_ENTRY_ID, entryId)
                .putExtra(Notifications.EXTRA_DATE, date)
                .putExtra(Notifications.EXTRA_TEXT, text)
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or
            (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(context, requestCode(entryId), intent, flags)
    }

    companion object {
        const val ACTION_REMINDER = "com.shibi.commentarii.action.REMINDER"
        const val TEST_ENTRY_ID = -1L
        const val TEST_DELAY_SECONDS = 10L
        private const val PREFS = "alarm_state"
        private const val KEY_FIRED = "fired_immediate"
        private const val MAX_KEYS = 200
        private val LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")

        fun requestCode(entryId: Long): Int = entryId.toInt()
    }
}
