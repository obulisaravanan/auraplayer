package com.mymusic.player

import com.mymusic.player.model.PlayedThreshold
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayedThresholdTest {

    @Test
    fun testThresholdStart() {
        val threshold = PlayedThreshold.START

        // Less than 1 second playback should not satisfy
        assertFalse(threshold.hasSatisfied(currentPositionMs = 500L, durationMs = 180_000L, isFinished = false))

        // Reaching 1 second satisfies START threshold
        assertTrue(threshold.hasSatisfied(currentPositionMs = 1000L, durationMs = 180_000L, isFinished = false))
        assertTrue(threshold.hasSatisfied(currentPositionMs = 5000L, durationMs = 180_000L, isFinished = false))

        // Finished flag always satisfies
        assertTrue(threshold.hasSatisfied(currentPositionMs = 200L, durationMs = 180_000L, isFinished = true))
    }

    @Test
    fun testThreshold30Seconds() {
        val threshold = PlayedThreshold.SECONDS_30
        val durationMs = 120_000L // 2 minute track

        // Below 30 seconds
        assertFalse(threshold.hasSatisfied(currentPositionMs = 15_000L, durationMs = durationMs, isFinished = false))
        assertFalse(threshold.hasSatisfied(currentPositionMs = 29_999L, durationMs = durationMs, isFinished = false))

        // Exactly 30 seconds and above
        assertTrue(threshold.hasSatisfied(currentPositionMs = 30_000L, durationMs = durationMs, isFinished = false))
        assertTrue(threshold.hasSatisfied(currentPositionMs = 45_000L, durationMs = durationMs, isFinished = false))

        // Short track (e.g. 20s track) reaches 20s and finishes
        val shortDuration = 20_000L
        assertTrue(threshold.hasSatisfied(currentPositionMs = 20_000L, durationMs = shortDuration, isFinished = true))
    }

    @Test
    fun testThreshold50Percent() {
        val threshold = PlayedThreshold.PERCENT_50
        val durationMs = 200_000L // 200 seconds

        // 49% elapsed
        assertFalse(threshold.hasSatisfied(currentPositionMs = 98_000L, durationMs = durationMs, isFinished = false))

        // Exactly 50% elapsed
        assertTrue(threshold.hasSatisfied(currentPositionMs = 100_000L, durationMs = durationMs, isFinished = false))

        // 75% elapsed
        assertTrue(threshold.hasSatisfied(currentPositionMs = 150_000L, durationMs = durationMs, isFinished = false))
    }

    @Test
    fun testThresholdEnd() {
        val threshold = PlayedThreshold.END
        val durationMs = 100_000L // 100 seconds

        // Mid-song
        assertFalse(threshold.hasSatisfied(currentPositionMs = 50_000L, durationMs = durationMs, isFinished = false))
        // 90%
        assertFalse(threshold.hasSatisfied(currentPositionMs = 90_000L, durationMs = durationMs, isFinished = false))

        // Within 1.5 seconds of ending
        assertTrue(threshold.hasSatisfied(currentPositionMs = 99_000L, durationMs = durationMs, isFinished = false))

        // Finished flag
        assertTrue(threshold.hasSatisfied(currentPositionMs = 100_000L, durationMs = durationMs, isFinished = true))
    }
}
