package com.example.beamshare.session

import android.net.Uri

enum class FileDirection { SENT, RECEIVED }
enum class FileStatus   { ACTIVE, COMPLETED, FAILED, CANCELLED }

data class SessionFile(
    val id: String,                     // unique transfer ID (use UUID or index)
    val fileName: String,
    val fileSize: String,               // formatted: "128 MB"
    val direction: FileDirection,
    val status: FileStatus,
    val progress: Int,                  // 0–100
    val savedUri: Uri? = null           // non-null once COMPLETED (for open-in-folder)
)
