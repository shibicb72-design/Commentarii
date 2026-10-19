package com.shibi.commentarii.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shibi.commentarii.data.ScheduleEntryEntity
import com.shibi.commentarii.data.ScheduleListener
import com.shibi.commentarii.domain.selectAlarms
import java.time.Clock

/** Registers one AlarmManager alarm per schedule entry, 10 minutes before the entry time. */
class AlarmScheduler(private val context: Context, private val clock: Clock) : ScheduleListener {
    private val alarmManager: AlarmManager = context.getSystemService(AlarmManager::class.java)

    /** True when exact alarms may be set (always before API 31, where no permission exists). */
    fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    override fun onScheduleChanged(touchedIds: Collection<Long>, entries: List<ScheduleEntryEntity>) {
        cancel(touchedIds)
        schedule(entries)
    }

    /** Registers alarms for the selectable (future, non-blank) [entries]. */
    fun schedule(entries: List<ScheduleEntryEntity>) {
        val exact = canScheduleExact()
        for (alarm in selectAlarms(entries, clock.instant(), clock.zone)) {
            val pi = pendingIntent(alarm.entryId, alarm.date.toString(), alarm.text, create = true) ?: continue
            val at = alarm.triggerAt.toEpochMilli()
            try {
                if (exact) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                } else {
                    alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                }
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }
    }

    fun cancel(entryIds: Collection<Long>) {
        for (id in entryIds) {
            val pi = pendingIntent(id, "", "", create = false) ?: continue
            alarmManager.cancel(pi)
            pi.cancel()
        }
    }

    /** Cancels then re-registers; used after boot, time/zone change, app update and permission change. */
    fun rescheduleAll(entries: List<ScheduleEntryEntity>) {
        cancel(entries.map { it.id })
        schedule(entries)
    }

    private fun pendingIntent(entryId: Long, date: String, text: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
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
        fun requestCode(entryId: Long): Int = (entryId xor (entryId ushr 32)).toInt()
    }
}
