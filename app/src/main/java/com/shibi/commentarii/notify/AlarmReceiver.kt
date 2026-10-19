package com.shibi.commentarii.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val log = context.debugLog()
        try {
            val id = intent.getLongExtra(Notifications.EXTRA_ENTRY_ID, 0L)
            val text = intent.getStringExtra(Notifications.EXTRA_TEXT).orEmpty()
            val date = intent.getStringExtra(Notifications.EXTRA_DATE).orEmpty()
            log.log("FIRE", "id=$id action=${intent.action} date=$date textLen=${text.length}")
            if (text.isBlank() || date.isBlank()) {
                log.log("FIRE", "id=$id dropped: blank text or date")
                return
            }
            Notifications.showReminder(context, id, date, text)
        } catch (t: Throwable) {
            log.log("ERROR", "receiver failed: $t")
        }
    }
}
