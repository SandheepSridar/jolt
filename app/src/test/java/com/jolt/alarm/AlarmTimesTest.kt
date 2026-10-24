package com.jolt.alarm

import com.jolt.data.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class AlarmTimesTest {
    private val zone = ZoneId.of("America/New_York")
    private val mon = 1 shl 0
    private val wed = 1 shl 2
    private val sun = 1 shl 6

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone)

    @Test fun oneOffLaterToday() {
        // Wednesday 2026-10-21 06:00
        assertEquals(at(2026, 10, 21, 7, 0), AlarmTimes.nextOccurrence(7, 0, 0, at(2026, 10, 21, 6, 0)))
    }

    @Test fun oneOffAlreadyPassedRollsToTomorrow() {
        assertEquals(at(2026, 10, 22, 7, 0), AlarmTimes.nextOccurrence(7, 0, 0, at(2026, 10, 21, 8, 0)))
    }

    @Test fun exactlyNowIsNotTheNextOccurrence() {
        assertEquals(at(2026, 10, 22, 7, 0), AlarmTimes.nextOccurrence(7, 0, 0, at(2026, 10, 21, 7, 0)))
    }

    @Test fun wrapsPastMidnight() {
        assertEquals(at(2026, 10, 22, 0, 30), AlarmTimes.nextOccurrence(0, 30, 0, at(2026, 10, 21, 23, 50)))
    }

    @Test fun repeatSkipsToNextSelectedDay() {
        // Wednesday after 7:00 with Mon+Wed selected → next Monday.
        assertEquals(at(2026, 10, 26, 7, 0), AlarmTimes.nextOccurrence(7, 0, mon or wed, at(2026, 10, 21, 9, 0)))
    }

    @Test fun repeatSameDayNextWeek() {
        // Only Wednesday, already passed today → next Wednesday.
        assertEquals(at(2026, 10, 28, 7, 0), AlarmTimes.nextOccurrence(7, 0, wed, at(2026, 10, 21, 9, 0)))
    }

    @Test fun repeatAcrossWeekBoundary() {
        // Saturday night, Sunday selected.
        assertEquals(at(2026, 10, 25, 8, 0), AlarmTimes.nextOccurrence(8, 0, sun, at(2026, 10, 24, 22, 0)))
    }

    @Test fun springForwardGapMovesLater() {
        // 2026-03-08 02:30 doesn't exist in New York; Java shifts it to 03:30 EDT.
        val next = AlarmTimes.nextOccurrence(2, 30, 0, at(2026, 3, 8, 1, 0))
        assertEquals(3, next.hour)
        assertEquals(30, next.minute)
        assertEquals(8, next.dayOfMonth)
    }

    @Test fun fallBackOverlapUsesFirstInstant() {
        // 2026-11-01 01:30 happens twice; ring at the first one (EDT, -04:00).
        val next = AlarmTimes.nextOccurrence(1, 30, 0, at(2026, 11, 1, 0, 0))
        assertEquals(-4 * 3600, next.offset.totalSeconds)
    }

    @Test fun earliestPrefersSnoozeOverRegularTime() {
        val now = at(2026, 10, 21, 7, 0).toInstant().toEpochMilli()
        val snoozedOneOff = Alarm(id = 1, hour = 7, minute = 0, enabled = false, snoozedUntil = now + 5 * 60_000)
        val later = Alarm(id = 2, hour = 7, minute = 30)
        val (alarm, time) = AlarmTimes.earliest(listOf(later, snoozedOneOff), now, zone)!!
        assertEquals(1L, alarm.id)
        assertEquals(now + 5 * 60_000, time)
    }

    @Test fun disabledWithoutSnoozeNeverRings() {
        val now = at(2026, 10, 21, 7, 0).toInstant().toEpochMilli()
        assertNull(AlarmTimes.nextTrigger(Alarm(hour = 8, minute = 0, enabled = false), now, zone))
        assertNull(AlarmTimes.nextTrigger(Alarm(hour = 8, minute = 0, enabled = false, snoozedUntil = now - 1), now, zone))
    }

    @Test fun nothingScheduled() {
        assertNull(AlarmTimes.earliest(emptyList(), 0, zone))
    }
}
