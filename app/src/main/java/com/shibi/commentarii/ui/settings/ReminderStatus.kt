package com.shibi.commentarii.ui.settings

import com.shibi.commentarii.R
import com.shibi.commentarii.domain.AlarmKind

/** Status line for battery optimisation. */
fun batteryStatusRes(unrestricted: Boolean): Int =
    if (unrestricted) R.string.settings_battery_unrestricted else R.string.settings_battery_optimised

/** Snackbar text for a schedule outcome; null for kinds the user needs no message about. */
fun outcomeMessageRes(kind: AlarmKind): Int? = when (kind) {
    AlarmKind.IMMEDIATE -> R.string.outcome_immediate
    AlarmKind.PAST -> R.string.outcome_past
    AlarmKind.SCHEDULED, AlarmKind.BLANK -> null
}
