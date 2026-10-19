package com.shibi.commentarii.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val text = intent.getStringExtra(Notifications.EXTRA_TEXT).orEmpty()
        val date = intent.getStringExtra(Notifications.EXTRA_DATE).orEmpty()
        if (text.isBlank() || date.isBlank()) return
        Notifications.showReminder(context, intent.getLongExtra(Notifications.EXTRA_ENTRY_ID, 0L), date, text)
    }
}
