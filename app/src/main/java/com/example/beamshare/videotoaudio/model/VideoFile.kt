package com.example.beamshare.videotoaudio.model

/**
 * Represents a video file from MediaStore.
 */
data class VideoFile(
    val id: Long,
    val displayName: String,
    val duration: Long,       // milliseconds
    val size: Long,           // bytes
    val uri: String,          // content:// URI string
    val thumbnailUri: String? = null
) {
    val formattedDuration: String get() {
        val totalSec = duration / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    val formattedSize: String get() {
        return when {
            size >= 1_000_000_000 -> "%.1f GB".format(size / 1_000_000_000.0)
            size >= 1_000_000     -> "%.1f MB".format(size / 1_000_000.0)
            else                  -> "%.0f KB".format(size / 1_000.0)
        }
    }
}