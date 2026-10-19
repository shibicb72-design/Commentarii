package com.shibi.commentarii.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shibi.commentarii.MainActivity
import com.shibi.commentarii.R

object Notifications {
    const val CHANNEL_ID = "schedule_reminders"
    const val EXTRA_DATE = "com.shibi.commentarii.extra.DATE"
    const val EXTRA_TEXT = "com.shibi.commentarii.extra.TEXT"
    const val EXTRA_ENTRY_ID = "com.shibi.commentarii.extra.ENTRY_ID"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.notif_channel_desc) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Posts "In 10 min: text"; does nothing when notifications are not allowed. */
    fun showReminder(context: Context, entryId: Long, date: String, text: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_DATE, date)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val code = AlarmScheduler.requestCode(entryId)
        val tap = PendingIntent.getActivity(
            context,
            code,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_in_10_min, text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        @Suppress("MissingPermission") // areNotificationsEnabled() was checked above
        manager.notify(code, notification)
    }
}
