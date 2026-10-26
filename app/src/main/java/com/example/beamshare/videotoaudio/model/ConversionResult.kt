package com.example.beamshare.videotoaudio.model

data class ConversionResult(
    val outputPath: String,
    val outputFileName: String,
    val fileSizeBytes: Long,
    val durationMs: Long,
    val bitrateKbps: Int
) {
    val formattedSize: String get() {
        return when {
            fileSizeBytes >= 1_000_000 -> "%.1f MB".format(fileSizeBytes / 1_000_000.0)
            else                       -> "%.0f KB".format(fileSizeBytes / 1_000.0)
        }
    }

    val formattedDuration: String get() {
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}