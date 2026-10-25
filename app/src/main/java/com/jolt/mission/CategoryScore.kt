package com.jolt.mission

/** One detector result, with its box area as a share (0–1) of the analysed square. */
data class Detection(val label: String, val score: Float, val areaFraction: Float)

/**
 * Smallest share of the viewfinder the object must fill. Stops a sink seen across the
 * room — or from bed, through a doorway — from counting.
 */
const val MIN_AREA_FRACTION = 0.12f

/** Confidence that [category] fills the viewfinder: the best qualifying detection, or 0. */
fun categoryScore(detections: List<Detection>, category: Category): Float =
    detections
        .filter { it.label == category.label && it.areaFraction >= MIN_AREA_FRACTION }
        .maxOfOrNull { it.score } ?: 0f
