package com.mymusic.player.model

data class LibraryStats(
    val totalSongs: Int = 0,
    val unplayedCount: Int = 0,
    val playedCount: Int = 0,
    val favoritesCount: Int = 0,
    val totalPlaylists: Int = 0,
    val totalDurationMs: Long = 0L
) {
    val unplayedPercentage: Float
        get() = if (totalSongs > 0) (unplayedCount.toFloat() / totalSongs.toFloat()) * 100f else 0f

    val totalDurationFormatted: String
        get() {
            val totalHours = totalDurationMs / (1000 * 60 * 60)
            val remainingMinutes = (totalDurationMs % (1000 * 60 * 60)) / (1000 * 60)
            return if (totalHours > 0) "${totalHours}h ${remainingMinutes}m" else "${remainingMinutes}m"
        }
}

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val levelMillibels: Int,
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500
) {
    val displayFrequency: String
        get() = if (centerFreqHz >= 1000) "${centerFreqHz / 1000} kHz" else "$centerFreqHz Hz"
}

data class EqualizerState(
    val enabled: Boolean = false,
    val currentPreset: String = "Normal",
    val availablePresets: List<String> = listOf("Normal", "Classical", "Dance", "Flat", "Folk", "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock"),
    val bands: List<EqualizerBand> = emptyList(),
    val bassBoost: Int = 0 // 0 to 1000 millibels
)
