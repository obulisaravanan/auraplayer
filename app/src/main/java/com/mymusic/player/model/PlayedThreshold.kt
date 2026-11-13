package com.mymusic.player.model

enum class PlayedThreshold(
    val title: String,
    val description: String
) {
    START(
        title = "When playback starts",
        description = "Marks song played as soon as listening begins (>= 1s)"
    ),
    SECONDS_30(
        title = "After 30 seconds",
        description = "Marks song played after 30 seconds of active playback"
    ),
    PERCENT_50(
        title = "After 50% of song",
        description = "Marks song played after listening to at least half the track"
    ),
    END(
        title = "Only when finished",
        description = "Marks song played only when the entire track finishes"
    );

    /**
     * Determines whether playback has satisfied the played threshold.
     * @param currentPositionMs Current elapsed playback position in milliseconds.
     * @param durationMs Total duration of the song in milliseconds.
     * @param isFinished Whether the track has reached the end of playback.
     */
    fun hasSatisfied(
        currentPositionMs: Long,
        durationMs: Long,
        isFinished: Boolean = false
    ): Boolean {
        if (durationMs <= 0) return isFinished || currentPositionMs > 1000L

        return when (this) {
            START -> currentPositionMs >= 1000L || isFinished
            SECONDS_30 -> {
                val threshold = minOf(30_000L, durationMs)
                currentPositionMs >= threshold || isFinished
            }
            PERCENT_50 -> {
                val halfDuration = durationMs / 2
                currentPositionMs >= halfDuration || isFinished
            }
            END -> {
                isFinished || currentPositionMs >= (durationMs - 1500L)
            }
        }
    }
}
