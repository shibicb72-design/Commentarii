package com.shibi.commentarii.testutil

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference

/** A settable [Clock] for tests that cross midnight. Safe to read from the background threads autosave and Room use. */
class MutableClock private constructor(
    private val current: AtomicReference<Instant>,
    private val zone: ZoneId,
) : Clock() {
    constructor(start: LocalDateTime, zone: ZoneId = ZoneOffset.UTC) :
        this(AtomicReference(start.atZone(zone).toInstant()), zone)

    fun set(dateTime: LocalDateTime) = current.set(dateTime.atZone(zone).toInstant())

    override fun getZone(): ZoneId = zone

    /** The returned clock shares this clock's time, so moving either moves both. */
    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    override fun instant(): Instant = current.get()
}
