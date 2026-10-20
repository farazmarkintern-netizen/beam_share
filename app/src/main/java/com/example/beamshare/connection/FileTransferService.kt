package com.example.beamshare.connection

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.beamshare.R
import com.example.beamshare.ui.transfers.TransferProgressActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FileTransferService : Service() {

    companion object {
        private const val TAG = "FileTransferService"
        const val CHANNEL_ID = "beamshare_transfer"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START_RECEIVING = "action_start_receiving"
        const val ACTION_START_SENDING = "action_start_sending"
        const val ACTION_CANCEL = "action_cancel"
        const val EXTRA_HOST_ADDRESS = "host_address"
        const val EXTRA_PORT = "port"
        const val EXTRA_PAIRING_CODE = "pairing_code"
        const val EXTRA_FILE_URIS = "file_uris"
        const val EXTRA_FILE_NAMES = "file_names"
        const val EXTRA_FILE_SIZES = "file_sizes"

        fun startReceiving(
            context: Context,
            pairingCode: String,
            port: Int = ConnectionConfig.DEFAULT_PORT
        ) {
            Log.d(TAG, "startReceiving: port=$port code=$pairingCode")
            val intent = Intent(context, FileTransferService::class.java).apply {
                action = ACTION_START_RECEIVING
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_PAIRING_CODE, pairingCode)
            }
            context.startForegroundService(intent)
        }

        fun startSending(
            context: Context,
            hostAddress: String,
            port: Int,
            fileUris: ArrayList<Uri>,
            fileNames: ArrayList<String>,
            fileSizes: LongArray,
            pairingCode: String = ""
        ) {
            Log.d(TAG, "startSending: host=$hostAddress port=$port files=${fileNames.size} code='$pairingCode'")
            val intent = Intent(context, FileTransferService::class.java).apply {
                action = ACTION_START_SENDING
                putExtra(EXTRA_HOST_ADDRESS, hostAddress)
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_PAIRING_CODE, pairingCode)
                putParcelableArrayListExtra(EXTRA_FILE_URIS, fileUris)
                putStringArrayListExtra(EXTRA_FILE_NAMES, fileNames)
                putExtra(EXTRA_FILE_SIZES, fileSizes)
            }
            context.startForegroundService(intent)
        }
    }

    private val binder = TransferBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wifiDirectManager: WifiDirectManager
    private lateinit var fileTransferSocket: FileTransferSocket

    val connectionState: StateFlow<ConnectionState> get() = wifiDirectManager.connectionState
    val transferState: StateFlow<TransferState> get() = fileTransferSocket.transferState

    // ── Persistent session state ─────────────────────────────────────
    private val _sessionActive = MutableStateFlow(false)
    val sessionActive: StateFlow<Boolean> = _sessionActive.asStateFlow()

    private var sessionPairingCode = ""
    private var sessionGroupOwnerAddress = ""
    private var sessionIsGroupOwner = false
    private var sessionPort = ConnectionConfig.DEFAULT_PORT

    inner class TransferBinder : Binder() {
        fun getService(): FileTransferService = this@FileTransferService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        wifiDirectManager = WifiDirectManager(applicationContext)
        wifiDirectManager.initialize()
        fileTransferSocket = FileTransferSocket(applicationContext)
        startForeground(NOTIFICATION_ID, buildNotification("Preparing transfer…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START_RECEIVING -> {
                val port = intent.getIntExtra(EXTRA_PORT, ConnectionConfig.DEFAULT_PORT)
                val code = intent.getStringExtra(EXTRA_PAIRING_CODE) ?: ""
                Log.i(TAG, "═══ RECEIVE MODE START ═══")
                Log.i(TAG, "  port        = $port")
                Log.i(TAG, "  pairingCode = '$code'  (length=${code.length})")
                if (code.isEmpty()) Log.e(TAG, "  !! pairingCode is EMPTY — handshake will always fail !!")
                startReceiveMode(port, code)
            }
            ACTION_START_SENDING -> {
                val host = intent.getStringExtra(EXTRA_HOST_ADDRESS) ?: run {
                    Log.e(TAG, "ACTION_START_SENDING: missing host address")
                    return START_NOT_STICKY
                }
                val port = intent.getIntExtra(EXTRA_PORT, ConnectionConfig.DEFAULT_PORT)
                @Suppress("DEPRECATION")
                val uris: ArrayList<Uri> = intent.getParcelableArrayListExtra(EXTRA_FILE_URIS)
                    ?: run {
                        Log.e(TAG, "ACTION_START_SENDING: missing file URIs")
                        return START_NOT_STICKY
                    }
                val names = intent.getStringArrayListExtra(EXTRA_FILE_NAMES) ?: run {
                    Log.e(TAG, "ACTION_START_SENDING: missing file names")
                    return START_NOT_STICKY
                }
                val sizes = intent.getLongArrayExtra(EXTRA_FILE_SIZES) ?: run {
                    Log.e(TAG, "ACTION_START_SENDING: missing file sizes")
                    return START_NOT_STICKY
                }
                val sendCode = intent.getStringExtra(EXTRA_PAIRING_CODE) ?: ""
                Log.d(TAG, "Starting send mode: host=$host port=$port files=${names.size} code='$sendCode'")
                startSendMode(host, port, uris, names, sizes.toList(), sendCode)
            }
            ACTION_CANCEL -> {
                Log.d(TAG, "ACTION_CANCEL received")
                cancelTransfer()
            }
        }
        return START_NOT_STICKY
    }

    private fun startReceiveMode(port: Int, expectedCode: String) {
        sessionPairingCode = expectedCode
        sessionPort = port
        sessionIsGroupOwner = true

        serviceScope.launch {
            Log.d(TAG, "startReceiveMode: creating Wi-Fi Direct group on port $port")
            wifiDirectManager.createGroup()

            launch(Dispatchers.IO) {
                Log.i(TAG, "═══ SOCKET startReceiving — expectedCode='$expectedCode' port=$port ═══")
                fileTransferSocket.startReceiving(port, expectedCode)
            }

            var transferStarted = false
            launch {
                fileTransferSocket.transferState.collect { state ->
                    Log.d(TAG, "transferState (receive): $state")
                    when (state) {
                        is TransferState.WaitingForReceiver -> {
                            transferStarted = false
                            if (_sessionActive.value) {
                                updateNotification("Connected · Ready to receive")
                            } else {
                                updateNotification("Waiting for sender…")
                            }
                        }
                        is TransferState.Transferring -> {
                            transferStarted = true
                            val percent = if (state.totalBytes > 0)
                                ((state.currentBytes * 100) / state.totalBytes).toInt() else 0
                            updateNotification("Receiving ${state.fileName} — $percent% (${state.fileIndex}/${state.totalFiles})")
                        }
                        is TransferState.Completed -> {
                            Log.d(TAG, "Receive complete: ${state.successCount} files")
                            _sessionActive.value = true
                            val remoteName = fileTransferSocket.remoteDeviceName.value
                            updateNotification("Connected to $remoteName · Ready to receive")
                            // Auto-relisten so the peer can send again
                            launch(Dispatchers.IO) {
                                delay(1000)
                                fileTransferSocket.resetState()
                                Log.d(TAG, "Auto-relistening for more files on port $sessionPort")
                                fileTransferSocket.startReceiving(sessionPort, sessionPairingCode)
                            }
                        }
                        is TransferState.Failed -> {
                            Log.e(TAG, "Receive failed: ${state.message}")
                            updateNotification("Transfer failed: ${state.message}")
                            if (transferStarted && !_sessionActive.value) {
                                wifiDirectManager.disconnect()
                            } else if (_sessionActive.value) {
                                // Session active — relisten instead of disconnecting
                                launch(Dispatchers.IO) {
                                    delay(1000)
                                    fileTransferSocket.resetState()
                                    Log.d(TAG, "Session active — relistening after failure")
                                    fileTransferSocket.startReceiving(sessionPort, sessionPairingCode)
                                }
                            } else {
                                Log.w(TAG, "Socket failed before transfer began — keeping Wi-Fi group alive")
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun startSendMode(
        hostAddress: String,
        port: Int,
        fileUris: List<Uri>,
        fileNames: List<String>,
        fileSizes: List<Long>,
        pairingCode: String = ""
    ) {
        sessionPairingCode = pairingCode
        sessionPort = port
        sessionGroupOwnerAddress = hostAddress
        sessionIsGroupOwner = false

        serviceScope.launch {
            launch(Dispatchers.IO) {
                Log.d(TAG, "startSendMode: connecting to $hostAddress:$port with ${fileNames.size} files code='$pairingCode'")
                fileTransferSocket.startSending(hostAddress, port, pairingCode, fileUris, fileNames, fileSizes)
            }

            launch {
                fileTransferSocket.transferState.collect { state ->
                    Log.d(TAG, "transferState (send): $state")
                    when (state) {
                        is TransferState.Transferring -> {
                            val percent = if (state.totalBytes > 0)
                                ((state.currentBytes * 100) / state.totalBytes).toInt() else 0
                            val speed = fileTransferSocket.formatSpeed(state.speedBytesPerSec)
                            updateNotification("Sending ${state.fileName} — $percent% at $speed (${state.fileIndex}/${state.totalFiles})")
                        }
                        is TransferState.Completed -> {
                            Log.d(TAG, "Send complete: ${state.successCount} files")
                            _sessionActive.value = true
                            val remoteName = fileTransferSocket.remoteDeviceName.value
                            updateNotification("Connected to $remoteName · Ready to receive")
                            // Non-owner starts listening so the group owner can send files back
                            launch(Dispatchers.IO) {
                                delay(1000)
                                fileTransferSocket.resetState()
                                Log.d(TAG, "Non-owner auto-listening for reverse transfer on port $sessionPort")
                                fileTransferSocket.startReceiving(sessionPort, sessionPairingCode)
                            }
                        }
                        is TransferState.Failed -> {
                            Log.e(TAG, "Send failed: ${state.message}")
                            updateNotification("Transfer failed: ${state.message}")
                            if (!_sessionActive.value) {
                                wifiDirectManager.disconnect()
                            } else {
                                // Session active — stay ready to receive
                                launch(Dispatchers.IO) {
                                    delay(1000)
                                    fileTransferSocket.resetState()
                                    Log.d(TAG, "Non-owner relistening after send failure")
                                    fileTransferSocket.startReceiving(sessionPort, sessionPairingCode)
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    fun cancelTransfer() {
        Log.d(TAG, "cancelTransfer")
        fileTransferSocket.cancel()
        if (!_sessionActive.value) {
            wifiDirectManager.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            // Session active — cancel current transfer but stay connected
            Log.d(TAG, "Session active — cancelled transfer, staying connected")
            val remoteName = fileTransferSocket.remoteDeviceName.value
            updateNotification("Connected to $remoteName")
            // Both devices relisten after cancel — either can receive next
            serviceScope.launch(Dispatchers.IO) {
                delay(500)
                fileTransferSocket.resetState()
                Log.d(TAG, "Relistening after cancel (isGroupOwner=$sessionIsGroupOwner)")
                fileTransferSocket.startReceiving(sessionPort, sessionPairingCode)
            }
        }
    }

    /**
     * Disconnect the persistent session and stop the service entirely.
     * Called only when the user explicitly leaves the session.
     */
    fun disconnectSession() {
        Log.d(TAG, "disconnectSession")
        _sessionActive.value = false
        fileTransferSocket.cancel()
        wifiDirectManager.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Send more files over the existing Wi-Fi Direct connection.
     * Works for BOTH devices:
     *  - Non-owner (original sender): connects to group owner at 192.168.49.1
     *  - Group owner (original receiver): connects to peer's IP captured during the last receive
     */
    fun sendMoreFiles(
        fileUris: List<Uri>,
        fileNames: List<String>,
        fileSizes: List<Long>
    ) {
        if (!_sessionActive.value) {
            Log.e(TAG, "sendMoreFiles: no active session")
            return
        }

        val targetAddress: String
        if (sessionIsGroupOwner) {
            // Group owner wants to send — connect to the peer who connected to us last
            val peerIp = fileTransferSocket.peerAddress.value
            if (peerIp.isEmpty()) {
                Log.e(TAG, "sendMoreFiles: group owner has no peer IP yet — cannot send")
                return
            }
            targetAddress = peerIp
            Log.d(TAG, "sendMoreFiles (group owner → peer): $peerIp:$sessionPort files=${fileNames.size}")
        } else {
            // Non-owner: connect to group owner as usual
            targetAddress = sessionGroupOwnerAddress
            Log.d(TAG, "sendMoreFiles (non-owner → group owner): $targetAddress:$sessionPort files=${fileNames.size}")
        }

        // Stop any active server socket before connecting outbound
        fileTransferSocket.stopListening()
        fileTransferSocket.resetState()

        serviceScope.launch(Dispatchers.IO) {
            fileTransferSocket.startSending(
                targetAddress,
                sessionPort,
                sessionPairingCode,
                fileUris,
                fileNames,
                fileSizes
            )
        }
    }

    fun getWifiDirectManager(): WifiDirectManager = wifiDirectManager
    fun getFileTransferSocket(): FileTransferSocket = fileTransferSocket
    fun getSessionGroupOwnerAddress(): String = sessionGroupOwnerAddress
    fun getSessionPort(): Int = sessionPort
    fun isGroupOwner(): Boolean = sessionIsGroupOwner

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "File Transfer", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Shows file transfer progress" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, TransferProgressActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelIntent = PendingIntent.getService(
            this, 1,
            Intent(this, FileTransferService::class.java).apply { action = ACTION_CANCEL },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BeamShare")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_send)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_close, "Cancel", cancelIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        Log.d(TAG, "Notification: $text")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        fileTransferSocket.cancel()
        wifiDirectManager.release()
        serviceScope.cancel()
    }
}