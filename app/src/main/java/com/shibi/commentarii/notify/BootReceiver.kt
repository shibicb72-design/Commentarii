package com.shibi.commentarii.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shibi.commentarii.CommentariiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Re-registers all future alarms after reboot, clock/zone change, app update or exact-alarm permission change. */
class BootReceiver : BroadcastReceiver() {
    /** Replaceable in tests. */
    internal var scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    /** The in-flight reschedule, so tests can wait for it. */
    internal var job: Job? = null
        private set

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in ACTIONS) return
        val container = (context.applicationContext as CommentariiApp).container
        // goAsync() is null when the receiver is invoked outside a real broadcast (e.g. in unit tests).
        val pending: PendingResult? = goAsync()
        job = scope.launch {
            try {
                container.rescheduleAllAlarms()
            } finally {
                pending?.finish()
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
