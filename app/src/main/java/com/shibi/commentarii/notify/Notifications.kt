package com.shibi.commentarii.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shibi.commentarii.CommentariiApp
import com.shibi.commentarii.MainActivity
import com.shibi.commentarii.R
import com.shibi.commentarii.debug.DebugLog
import java.time.Clock

/** The app's shared debug log (the container's when available, else a file-backed instance on the same file). */
internal fun Context.debugLog(): DebugLog =
    (applicationContext as? CommentariiApp)?.container?.debugLog
        ?: DebugLog(filesDir, Clock.systemDefaultZone())

object Notifications {
    /** v2: the first channel was IMPORTANCE_DEFAULT and importance cannot be raised after creation. */
    const val CHANNEL_ID = "schedule_reminders_v2"
    const val LEGACY_CHANNEL_ID = "schedule_reminders"
    const val EXTRA_DATE = "com.shibi.commentarii.extra.DATE"
    const val EXTRA_TEXT = "com.shibi.commentarii.extra.TEXT"
    const val EXTRA_ENTRY_ID = "com.shibi.commentarii.extra.ENTRY_ID"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notif_channel_desc) }
        manager.createNotificationChannel(channel)
    }

    /** Posts "In 10 min: text"; logs and does nothing when notifications are not allowed. */
    fun showReminder(context: Context, entryId: Long, date: String, text: String) {
        val log = context.debugLog()
        val manager = NotificationManagerCompat.from(context)
        ensureChannel(context)
        val enabled = manager.areNotificationsEnabled()
        val importance = context.getSystemService(NotificationManager::class.java)
            .getNotificationChannel(CHANNEL_ID)?.importance
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        log.log("NOTIFY", "id=$entryId enabled=$enabled channelImportance=$importance postPermission=$granted")
        if (!enabled) {
            log.log("NOTIFY", "id=$entryId skipped: notifications disabled")
            return
        }
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        @Suppress("MissingPermission") // areNotificationsEnabled() was checked above
        manager.notify(code, notification)
        log.log("NOTIFY", "id=$entryId posted")
    }
}
