package com.jolt.mission

/**
 * Decides when the mission passes: [required] matching frames in a row, so a single lucky
 * frame (or a blurry swipe past the object) can't dismiss the alarm.
 */
class MatchTracker(
    private val threshold: Float,
    private val required: Int = 3,
    /** Scores below this are treated as "nothing like it". */
    private val floor: Float = 0.35f,
) {
    var streak = 0
        private set

    /** Records one frame's score. Returns true once the mission has passed. */
    fun offer(score: Float): Boolean {
        streak = if (score >= threshold) streak + 1 else 0
        return streak >= required
    }

    /**
     * 0–1 for the UI ring: mostly how close the score is to the threshold, with the last
     * stretch filled by the streak so it visibly "locks on".
     */
    fun progress(score: Float): Float {
        val closeness = ((score - floor) / (threshold - floor)).coerceIn(0f, 1f)
        return if (streak == 0) closeness * 0.7f else 0.7f + 0.3f * streak.coerceAtMost(required) / required
    }
}
