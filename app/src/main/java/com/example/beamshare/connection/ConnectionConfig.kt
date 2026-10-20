package com.example.beamshare.connection

import android.net.wifi.p2p.WifiP2pDevice

data class ConnectionConfig(
    val deviceName: String,
    val pairingCode: String,
    val groupOwnerAddress: String? = null,
    val port: Int = DEFAULT_PORT,
    /** IP address of the receiver — only used in MODE_IP (hotspot / LAN). */
    val ipAddress: String? = null,
    /** Connection mode: WIFIDIRECT or IP. */
    val mode: String = MODE_WIFIDIRECT
) {
    fun toQrPayload(): String {
        return "$QR_PREFIX$deviceName|$pairingCode|$port|${ipAddress ?: ""}|$mode"
    }

    companion object {
        const val DEFAULT_PORT = 8778
        const val QR_PREFIX = "BEAMSHARE:"
        const val MODE_WIFIDIRECT = "WIFIDIRECT"
        const val MODE_IP = "IP"

        fun fromQrPayload(payload: String): ConnectionConfig? {
            if (!payload.startsWith(QR_PREFIX)) return null
            val parts = payload.removePrefix(QR_PREFIX).split("|")
            if (parts.size < 3) return null
            val ip   = if (parts.size >= 4) parts[3].takeIf { it.isNotBlank() } else null
            val mode = if (parts.size >= 5) parts[4].takeIf { it.isNotBlank() } ?: MODE_WIFIDIRECT
            else MODE_WIFIDIRECT
            return ConnectionConfig(
                deviceName = parts[0],
                pairingCode = parts[1],
                port = parts[2].toIntOrNull() ?: DEFAULT_PORT,
                ipAddress = ip,
                mode = mode
            )
        }

        fun generatePairingCode(): String {
            return (100000..999999).random().toString()
        }
    }
}

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Discovering : ConnectionState()
    data class DevicesFound(val devices: List<WifiP2pDevice>) : ConnectionState()
    data class Connecting(val deviceName: String) : ConnectionState()
    data class Connected(val groupOwnerAddress: String, val isGroupOwner: Boolean) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
    data object Disconnected : ConnectionState()
}

sealed class TransferState {
    data object Idle : TransferState()
    data object WaitingForReceiver : TransferState()
    data class Transferring(
        val fileName: String,
        val currentBytes: Long,
        val totalBytes: Long,
        val fileIndex: Int,
        val totalFiles: Int,
        val speedBytesPerSec: Long
    ) : TransferState()
    data class Completed(
        val successCount: Int,
        val failedCount: Int,
        val totalSize: Long,
        /**
         * Receiver side: absolute paths + sizes of each successfully saved file.
         * Sender side: empty list (sender already has URIs from the original selection).
         */
        val savedFiles: List<Pair<String, Long>> = emptyList()
    ) : TransferState()
    data class Failed(val message: String) : TransferState()
}