package com.shibi.commentarii.data

/** Told after schedule rows are saved, so alarms can follow. [touchedIds] = ids saved or removed by this save. */
interface ScheduleListener {
    fun onScheduleChanged(touchedIds: Collection<Long>, entries: List<ScheduleEntryEntity>)
}
