package com.jolt.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchTrackerTest {
    @Test fun passesAfterThreeMatchesInARow() {
        val t = MatchTracker(threshold = 0.8f, required = 3)
        assertFalse(t.offer(0.85f))
        assertFalse(t.offer(0.9f))
        assertTrue(t.offer(0.81f))
    }

    @Test fun aMissResetsTheStreak() {
        val t = MatchTracker(threshold = 0.8f, required = 3)
        t.offer(0.9f)
        t.offer(0.9f)
        assertFalse(t.offer(0.5f))
        assertEquals(0, t.streak)
        assertFalse(t.offer(0.9f))
        assertFalse(t.offer(0.9f))
        assertTrue(t.offer(0.9f))
    }

    @Test fun thresholdIsInclusive() {
        val t = MatchTracker(threshold = 0.8f, required = 1)
        assertTrue(t.offer(0.8f))
    }

    @Test fun progressStaysInRangeAndRisesWithStreak() {
        val t = MatchTracker(threshold = 0.8f, required = 3)
        assertEquals(0f, t.progress(0.1f), 0f)
        val near = t.progress(0.75f)
        assertTrue(near in 0f..0.7f)
        t.offer(0.85f)
        val one = t.progress(0.85f)
        t.offer(0.85f)
        val two = t.progress(0.85f)
        assertTrue(one > near && two > one && two <= 1f)
    }

    @Test fun similarityOfNormalisedVectors() {
        val a = floatArrayOf(0.6f, 0.8f)
        assertEquals(1f, similarity(a, a), 1e-6f)
        assertEquals(0f, similarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 1e-6f)
        assertEquals(1f, bestMatch(a, listOf(floatArrayOf(1f, 0f), a)), 1e-6f)
        assertEquals(0f, bestMatch(a, emptyList()), 0f)
    }

    @Test fun customFloorShiftsWhereTheRingStartsFilling() {
        val detector = MatchTracker(threshold = 0.5f, floor = 0.2f)
        assertEquals(0f, detector.progress(0.2f), 0f)
        assertTrue(detector.progress(0.35f) > 0f)
        // The default floor (0.35) would still show an empty ring at 0.35.
        assertEquals(0f, MatchTracker(threshold = 0.5f).progress(0.35f), 0f)
    }
}
