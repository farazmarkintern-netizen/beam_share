package com.example.beamshare.videotoaudio.model

data class ConversionConfig(
    val videoFile: VideoFile,
    val outputFormat: String = "MP3",       // currently only MP3 supported
    val bitrateKbps: Int = 192              // 64 / 128 / 192 / 256 / 320
) {
    val estimatedSizeMb: Double get() {
        val durationSec = videoFile.duration / 1000.0
        return (bitrateKbps * durationSec) / (8.0 * 1024)
    }

    val estimatedSizeLabel: String get() {
        val mb = estimatedSizeMb
        return if (mb < 1.0) "%.0f KB".format(mb * 1024) else "%.1f MB".format(mb)
    }
}