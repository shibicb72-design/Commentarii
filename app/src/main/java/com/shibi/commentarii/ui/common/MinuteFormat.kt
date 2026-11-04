package com.shibi.commentarii.ui.common

import android.content.Context
import android.text.format.DateFormat
import java.util.Date
import java.util.TimeZone

/**
 * Formats minutes of the day with the phone's locale and 12/24-hour setting. A minute of day is a wall-clock label, so
 * it is formatted in UTC from the epoch: the time zone or a daylight-saving change cannot shift it.
 */
fun minuteFormatter(context: Context): (minuteOfDay: Int) -> String {
    val format = DateFormat.getTimeFormat(context).apply { timeZone = TimeZone.getTimeZone("UTC") }
    return { format.format(Date(it * 60_000L)) }
}
