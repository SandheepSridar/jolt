package com.jolt.alarm

import com.jolt.data.Alarm
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Pure time maths, kept free of Android types so it can be unit tested. */
object AlarmTimes {

    /**
     * The next time [hour]:[minute] falls on one of [days] strictly after [now].
     * A DST gap pushes the time forward by the gap length; in an overlap the earlier instant wins.
     */
    fun nextOccurrence(hour: Int, minute: Int, days: Int, now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(hour, minute)
        for (offset in 0L..7L) {
            val date = now.toLocalDate().plusDays(offset)
            val dayBit = 1 shl (date.dayOfWeek.value - 1)
            if (days != 0 && days and dayBit == 0) continue
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("No occurrence within a week for days=$days")
    }

    /** When [alarm] should next ring, or null if it never will. Pending snoozes count even for switched-off one-off alarms. */
    fun nextTrigger(alarm: Alarm, nowMillis: Long, zone: ZoneId): Long? {
        val snooze = alarm.snoozedUntil?.takeIf { it > nowMillis }
        if (!alarm.enabled) return snooze
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val regular = nextOccurrence(alarm.hour, alarm.minute, alarm.days, now).toInstant().toEpochMilli()
        return if (snooze != null) minOf(snooze, regular) else regular
    }

    /** The alarm that rings first and when, or null if nothing is scheduled. */
    fun earliest(alarms: List<Alarm>, nowMillis: Long, zone: ZoneId): Pair<Alarm, Long>? =
        alarms.mapNotNull { alarm -> nextTrigger(alarm, nowMillis, zone)?.let { alarm to it } }
            .minByOrNull { it.second }
}
