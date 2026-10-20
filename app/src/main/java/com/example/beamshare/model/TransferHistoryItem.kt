// com/example/beamshare/model/TransferHistoryItem.kt
package com.example.beamshare.model

data class TransferHistoryItem(
    val id: String,
    val title: String,
    val direction: TransferDirection,
    val deviceName: String,
    val totalSize: String,
    val fileCount: Int,
    val status: HistoryStatus,
    val dateTime: String,
    val speed: String? = null,
    val duration: String? = null,
    val method: String? = null,
    val fileType: String = "mixed",
    /** Absolute path on disk (received files) or null for sent files whose URI is no longer stored. */
    val filePath: String? = null
)

enum class TransferDirection {
    SENT,
    RECEIVED
}

enum class HistoryStatus {
    DONE,
    FAILED,
    CANCELLED
}