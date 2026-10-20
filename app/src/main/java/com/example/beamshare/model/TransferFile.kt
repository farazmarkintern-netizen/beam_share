// com/example/beamshare/model/TransferFile.kt
package com.example.beamshare.model

data class TransferFile(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val status: TransferStatus = TransferStatus.PENDING,
    val progress: Int = 0,
    val errorReason: String? = null
)

enum class TransferStatus {
    PENDING,
    TRANSFERRING,
    DONE,
    FAILED
}