package com.jolt.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryScoreTest {
    @Test fun bestQualifyingDetectionWins() {
        val detections = listOf(
            Detection("sink", 0.55f, 0.30f),
            Detection("sink", 0.80f, 0.20f),
        )
        assertEquals(0.80f, categoryScore(detections, Category.Sink), 0f)
    }

    @Test fun tooSmallInTheFrameDoesNotCount() {
        // A sink seen across the room: confident, but only 5% of the viewfinder.
        assertEquals(0f, categoryScore(listOf(Detection("sink", 0.9f, 0.05f)), Category.Sink), 0f)
    }

    @Test fun otherLabelsDoNotCount() {
        assertEquals(0f, categoryScore(listOf(Detection("toilet", 0.9f, 0.5f)), Category.Sink), 0f)
    }

    @Test fun areaThresholdIsInclusive() {
        assertEquals(0.6f, categoryScore(listOf(Detection("tv", 0.6f, MIN_AREA_FRACTION)), Category.TV), 0f)
    }

    @Test fun nothingDetected() {
        assertEquals(0f, categoryScore(emptyList(), Category.Fridge), 0f)
    }

    @Test fun displayNamesReadNaturally() {
        assertEquals("a sink", Category.Sink.withArticle)
        assertEquals("a TV", Category.TV.withArticle)
        assertTrue(Category.entries.all { Category.fromName(it.name) == it })
    }
}
