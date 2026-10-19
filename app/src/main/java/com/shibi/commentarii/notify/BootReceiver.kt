package com.shibi.commentarii.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shibi.commentarii.CommentariiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Re-registers all future alarms after reboot, clock/zone change, app update or exact-alarm permission change. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val container = (context.applicationContext as CommentariiApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.rescheduleAllAlarms()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_EXACT_ALARM_STATE_CHANGED = "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
        val ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_EXACT_ALARM_STATE_CHANGED,
        )
    }
}
