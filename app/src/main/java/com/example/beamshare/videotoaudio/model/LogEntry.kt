package com.example.beamshare.videotoaudio.model

enum class LogStatus { DONE, IN_PROGRESS, PENDING }

data class LogEntry(
    val id: Int,
    val text: String,
    val timestamp: String,    // e.g. "00:01"
    val status: LogStatus = LogStatus.PENDING
)