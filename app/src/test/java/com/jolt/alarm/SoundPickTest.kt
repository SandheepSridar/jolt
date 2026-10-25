package com.jolt.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class SoundPickTest {
    private val sounds = listOf("a", "b", "c")

    @Test fun neverRepeatsTheLastSoundWhenThereIsAChoice() {
        val random = Random(42)
        repeat(200) { assertNotEquals("b", pickRandomSound(sounds, last = "b", random = random)) }
    }

    @Test fun singleSoundAlwaysPlaysEvenIfItWasLast() {
        assertEquals("a", pickRandomSound(listOf("a"), last = "a"))
    }

    @Test fun usesEveryOptionOverTime() {
        val random = Random(7)
        val seen = (1..200).mapNotNull { pickRandomSound(sounds, last = null, random = random) }.toSet()
        assertEquals(sounds.toSet(), seen)
    }

    @Test fun emptyListPicksNothing() {
        assertNull(pickRandomSound(emptyList(), last = null))
    }
}
