package com.jolt.alarm

import kotlin.random.Random

/** One of [sounds] at random, avoiding [last] when there's a choice, so the same sound doesn't play twice running. */
fun pickRandomSound(sounds: List<String>, last: String?, random: Random = Random.Default): String? {
    val choices = sounds.filter { it != last }.ifEmpty { sounds }
    return choices.randomOrNull(random)
}
