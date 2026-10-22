// com/example/beamshare/model/TransferHistoryItem.kt
package com.example.beamshare.model

import java.util.UUID

data class TransferHistoryItem(
    val id: String = UUID.randomUUID().toString(),   // ← added for delete/selection
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
    /** Absolute path on disk (received files) or null for sent files. */
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