package com.example.beamshare.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.beamshare.R
import com.example.beamshare.connection.ConnectionConfig
import com.example.beamshare.connection.ConnectionState
import com.example.beamshare.connection.WifiDirectManager
import com.example.beamshare.data.TransferHistoryRepository
import com.example.beamshare.model.HistoryStatus
import com.example.beamshare.model.TransferDirection as HistoryDirection
import com.example.beamshare.model.TransferHistoryItem
import com.example.beamshare.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Foreground service that owns a BeamShare GROUP session for its whole lifetime.
 *
 * Unlike the old FileTransferService (one socket per transfer, relisten after each),
 * this starts ONE [SessionConnection] and keeps it open until the user leaves or the
 * link really drops. It is the bridge between the Wi-Fi Direct layer, the duplex
 * engine, and [SessionManager] (which the UI observes).
 *
 * Phase 1 scope: hosting, joining, bidirectional send, auto-receive, heartbeat,
 * history write. The discovery/code screens hand off to this in Phase 2.
 */
class GroupSessionService : Service() {

    companion object {
        private const val TAG = "GroupSessionService"
        const val CHANNEL_ID = "beamshare_session"
        const val NOTIFICATION_ID = 2001

        const val ACTION_HOST = "action_host"
        const val ACTION_JOIN = "action_join"
        const val ACTION_HOST_IP = "action_host_ip"
        const val ACTION_JOIN_IP = "action_join_ip"
        const val EXTRA_CODE = "extra_code"
        const val EXTRA_HOST_ADDRESS = "extra_host_address"
        const val EXTRA_PORT = "extra_port"

        // Reconnect config
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 16_000L

        fun host(context: Context, code: String) {
            context.startForegroundService(
                Intent(context, GroupSessionService::class.java).apply {
                    action = ACTION_HOST
                    putExtra(EXTRA_CODE, code)
                }
            )
        }

        fun join(context: Context, code: String) {
            context.startForegroundService(
                Intent(context, GroupSessionService::class.java).apply {
                    action = ACTION_JOIN
                    putExtra(EXTRA_CODE, code)
                }
            )
        }

        /** Host in IP/hotspot mode — skips Wi-Fi Direct, goes straight to TCP accept. */
        fun hostIp(context: Context, code: String) {
            context.startForegroundService(
                Intent(context, GroupSessionService::class.java).apply {
                    action = ACTION_HOST_IP
                    putExtra(EXTRA_CODE, code)
                }
            )
        }

        /** Join via direct TCP — skips Wi-Fi Direct, connects to the given address. */
        fun joinIp(context: Context, code: String, hostAddress: String, port: Int = ConnectionConfig.DEFAULT_PORT) {
            context.startForegroundService(
                Intent(context, GroupSessionService::class.java).apply {
                    action = ACTION_JOIN_IP
                    putExtra(EXTRA_CODE, code)
                    putExtra(EXTRA_HOST_ADDRESS, hostAddress)
                    putExtra(EXTRA_PORT, port)
                }
            )
        }
    }

    private val binder = LocalBinder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wifi: WifiDirectManager
    private lateinit var history: TransferHistoryRepository

    private var connection: SessionConnection? = null
    private var sessionCode: String = ""
    private val selfName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}"

    // ── Reconnect state ──────────────────────────────────────────────────────
    private var isHost = false
    private var peerAddress: String? = null
    private val reconnectAttempt = AtomicInteger(0)
    private val isReconnecting = AtomicBoolean(false)
    private val userLeft = AtomicBoolean(false)

    // ── Session options ──────────────────────────────────────────────────────
    private var autoAcceptEnabled = true
    private var deviceName: String = selfName
    /** Callbacks keyed by transfer id — resolved by acceptTransfer / rejectTransfer */
    private val pendingAcceptCallbacks = mutableMapOf<Long, (Boolean) -> Unit>()

    // ── Send queue (one file at a time) ─────────────────────────────────────
    private val sendQueue = ArrayDeque<Uri>()
    private var isSending = false

    inner class LocalBinder : Binder() {
        fun getService(): GroupSessionService = this@GroupSessionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "═══ SERVICE onCreate ═══")
        createChannel()
        wifi = WifiDirectManager(applicationContext)
        wifi.initialize()
        history = TransferHistoryRepository.getInstance(applicationContext)
        startForeground(NOTIFICATION_ID, buildNotification("Starting group…"))
        Log.i(TAG, "startForeground called OK")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "═══ SERVICE onStartCommand ═══")
        Log.i(TAG, "  action = '${intent?.action}'")
        sessionCode = intent?.getStringExtra(EXTRA_CODE).orEmpty()
        Log.i(TAG, "  code   = '$sessionCode' (len=${sessionCode.length})")
        when (intent?.action) {
            ACTION_HOST    -> startHosting(sessionCode)
            ACTION_JOIN    -> startJoining(sessionCode)
            ACTION_HOST_IP -> startHostingIp(sessionCode)
            ACTION_JOIN_IP -> {
                val host = intent.getStringExtra(EXTRA_HOST_ADDRESS)
                val port = intent.getIntExtra(EXTRA_PORT, ConnectionConfig.DEFAULT_PORT)
                Log.i(TAG, "  host   = '$host'")
                Log.i(TAG, "  port   = $port")
                if (host == null) {
                    Log.e(TAG, "ACTION_JOIN_IP missing EXTRA_HOST_ADDRESS — aborting")
                    SessionManager.setEnded("Missing host address")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startJoiningIp(sessionCode, host, port)
            }
        }
        return START_NOT_STICKY
    }

    // ── Hosting ──────────────────────────────────────────────────────────────

    private fun startHosting(code: String) {
        isHost = true
        SessionManager.setHosting(code, selfName)
        updateNotification("Group open · code $code")
        scope.launch {
            Log.d(TAG, "startHosting: creating Wi-Fi Direct group…")
            wifi.createGroup()
            val connected = wifi.connectionState.first {
                it is ConnectionState.Connected && it.isGroupOwner
            } as ConnectionState.Connected
            Log.i(TAG, "═══ WIFI DIRECT GROUP FORMED ═══ ownerAddr=${connected.groupOwnerAddress}")
            acceptClient(code)
        }
    }

    private fun acceptClient(code: String) {
        scope.launch(Dispatchers.IO) {
            var server: ServerSocket? = null
            try {
                Log.d(TAG, "acceptClient: binding ServerSocket on port ${ConnectionConfig.DEFAULT_PORT}")
                server = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(ConnectionConfig.DEFAULT_PORT))
                    soTimeout = 60_000
                }
                Log.i(TAG, "acceptClient: ServerSocket bound — waiting for client…")
                val socket = server.accept()
                val remoteAddr = socket.remoteSocketAddress
                Log.i(TAG, "═══ CLIENT TCP CONNECTED ═══ from=$remoteAddr")
                server.close()
                bringUp(socket, isHost = true, code = code)
            } catch (e: Exception) {
                Log.e(TAG, "acceptClient: accept failed: ${e.javaClass.simpleName}: ${e.message}", e)
                server?.close()
                if (!isReconnecting.get()) {
                    SessionManager.setEnded(e.message ?: "Could not start group")
                    stopEverything()
                }
            }
        }
    }

    // ── Joining ──────────────────────────────────────────────────────────────

    private fun startJoining(code: String) {
        isHost = false
        SessionManager.setJoining()
        updateNotification("Joining group…")
        scope.launch {
            Log.d(TAG, "startJoining: waiting for Wi-Fi Direct group…")
            val connected = wifi.connectionState.first {
                it is ConnectionState.Connected && !it.isGroupOwner
            } as ConnectionState.Connected
            val host = connected.groupOwnerAddress
            peerAddress = host
            Log.i(TAG, "═══ WIFI DIRECT GROUP JOINED ═══ ownerAddr=$host")
            connectToHost(host, code)
        }
    }

    private fun connectToHost(host: String, code: String) {
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "connectToHost: opening TCP socket to $host:${ConnectionConfig.DEFAULT_PORT}…")
                val socket = Socket().apply {
                    connect(InetSocketAddress(host, ConnectionConfig.DEFAULT_PORT), 30_000)
                }
                Log.i(TAG, "═══ TCP SOCKET CONNECTED ═══ to=$host")
                bringUp(socket, isHost = false, code = code)
            } catch (e: Exception) {
                Log.e(TAG, "connectToHost: failed: ${e.javaClass.simpleName}: ${e.message}", e)
                if (!isReconnecting.get()) {
                    SessionManager.setEnded(e.message ?: "Could not join group")
                    stopEverything()
                }
            }
        }
    }

    // ── IP-only modes (no Wi-Fi Direct) ─────────────────────────────────────────

    private fun startHostingIp(code: String) {
        isHost = true
        SessionManager.setHosting(code, selfName)
        updateNotification("Group open · code $code")
        Log.i(TAG, "═══ HOST IP START ═══")
        Log.i(TAG, "  code = '$code'")
        Log.i(TAG, "  port = ${ConnectionConfig.DEFAULT_PORT}")
        acceptClient(code)
    }

    private fun startJoiningIp(code: String, host: String, port: Int) {
        isHost = false
        peerAddress = host
        SessionManager.setJoining()
        updateNotification("Joining group…")
        Log.i(TAG, "═══ JOIN IP START ═══")
        Log.i(TAG, "  host = '$host'")
        Log.i(TAG, "  port = $port")
        Log.i(TAG, "  code = '$code' (len=${code.length})")
        scope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "joinIp: opening TCP socket to $host:$port…")
                val socket = Socket().apply {
                    connect(InetSocketAddress(host, port), 30_000)
                }
                Log.i(TAG, "═══ TCP SOCKET CONNECTED ═══ to=$host:$port")
                bringUp(socket, isHost = false, code = code)
            } catch (e: Exception) {
                Log.e(TAG, "joinIp: FAILED: ${e.javaClass.simpleName}: ${e.message}", e)
                if (!isReconnecting.get()) {
                    SessionManager.setEnded(e.message ?: "Could not join group")
                    stopEverything()
                }
            }
        }
    }

    // ── Shared bring-up ────────────────────────────────────────────────────────

    private suspend fun bringUp(socket: Socket, isHost: Boolean, code: String) {
        Log.i(TAG, "═══ HANDSHAKE START ═══")
        Log.i(TAG, "  isHost = $isHost")
        Log.i(TAG, "  code   = '$code' (len=${code.length})")
        Log.i(TAG, "  selfName = '$selfName'")
        val conn = SessionConnection(
            socket = socket,
            selfName = selfName,
            incomingFactory = AndroidIncomingFileFactory(applicationContext),
            scope = scope,
            listener = bridge
        )
        val result =
            if (isHost) conn.handshakeAsHost(expectedCode = code, triesLeft = 3)
            else conn.handshakeAsClient(code = code)

        Log.i(TAG, "═══ HANDSHAKE RESULT ═══ $result")
        when (result) {
            is HandshakeResult.Ok -> {
                connection = conn
                conn.start()
                reconnectAttempt.set(0)
                isReconnecting.set(false)
                Log.i(TAG, "╔══════════════════════════════════════╗")
                Log.i(TAG, "║   SESSION CONNECTED                  ║")
                Log.i(TAG, "║   peer = '${result.peerName}'")
                Log.i(TAG, "╚══════════════════════════════════════╝")
                SessionManager.setConnected(result.peerName)
                if (isHost) SessionManager.addMember(result.peerName)
                updateNotification("Connected · ${result.peerName}")

                if (sendQueue.isNotEmpty() && !isSending) {
                    processNextInQueue()
                }
            }
            is HandshakeResult.Failed -> {
                Log.e(TAG, "bringUp: HANDSHAKE FAILED — wrong code (triesLeft=${result.triesLeft})")
                if (!isReconnecting.get()) {
                    SessionManager.setEnded("Wrong pairing code")
                    stopEverything()
                }
            }
            is HandshakeResult.Error -> {
                Log.e(TAG, "bringUp: HANDSHAKE ERROR — ${result.message}")
                if (!isReconnecting.get()) {
                    SessionManager.setEnded(result.message)
                    stopEverything()
                }
            }
        }
    }

    // ── Public API (via binder) ────────────────────────────────────────────────

    /**
     * Enqueue [uris] for sending. Files are sent one at a time in order;
     * the next file starts only after the previous one completes or fails.
     */
    fun sendUris(uris: List<Uri>) {
        if (connection == null) {
            Log.w(TAG, "sendUris: no active connection — dropping ${uris.size} uris")
            return
        }
        sendQueue.addAll(uris)
        Log.d(TAG, "sendUris: queued ${uris.size} files, queue size=${sendQueue.size}, isSending=$isSending")
        if (!isSending) processNextInQueue()
    }

    fun cancelTransfer(id: Long) {
        SessionManager.cancelTransfer(id)
    }

    fun leave() {
        userLeft.set(true)
        connection?.leave()
    }

    // ── Send queue processor ───────────────────────────────────────────────────

    private fun processNextInQueue() {
        val conn = connection
        if (conn == null) {
            isSending = false
            sendQueue.clear()
            Log.d(TAG, "processNextInQueue: no connection — clearing queue and resetting isSending")
            return
        }
        val next = sendQueue.removeFirstOrNull()
        if (next == null) {
            isSending = false
            Log.d(TAG, "processNextInQueue: queue empty — idle")
            return
        }
        isSending = true
        Log.d(TAG, "processNextInQueue: starting send for $next, remaining=${sendQueue.size}")
        scope.launch(Dispatchers.IO) {
            try {
                val file = toOutgoing(next)
                if (file != null) {
                    conn.sendFiles(listOf(file))
                } else {
                    Log.w(TAG, "processNextInQueue: skipping unreadable uri $next")
                    scope.launch(Dispatchers.Main) { processNextInQueue() }
                }
            } catch (e: Exception) {
                Log.e(TAG, "processNextInQueue: exception sending $next", e)
                scope.launch(Dispatchers.Main) { processNextInQueue() }
            }
        }
    }

    // ── SessionConnection -> SessionManager + history bridge ───────────────────

    private val bridge = object : SessionConnection.Listener {
        override fun onIncomingStart(id: Long, name: String, size: Long) {
            Log.d(TAG, "bridge: incomingStart id=$id name='$name' size=$size autoAccept=$autoAcceptEnabled")
            SessionManager.onTransferStart(id, name, size, TransferDirection.IN)
            if (!autoAcceptEnabled) {
                // Hold the transfer — ask the bound activity to confirm
                // The callback resumes or cancels the transfer when the user decides
                pendingAcceptCallbacks[id] = { accepted ->
                    if (!accepted) {
                        connection?.cancelIncomingTransfer(id)
                    }
                }
                // Find the bound activity and show the dialog on the main thread
                scope.launch(Dispatchers.Main) {
                    val activity = getBoundSessionActivity()
                    if (activity != null) {
                        activity.showIncomingFileAcceptDialog(name, formatSize(size), id)
                    } else {
                        // No activity visible — auto-accept as fallback
                        Log.w(TAG, "onIncomingStart: no bound activity — auto-accepting id=$id")
                        pendingAcceptCallbacks.remove(id)
                    }
                }
            }
        }

        override fun onProgress(id: Long, direction: SessionConnection.Direction, name: String, sent: Long, total: Long) {
            SessionManager.onTransferProgress(id, name, total, direction.toModel(), sent)
        }

        override fun onComplete(id: Long, direction: SessionConnection.Direction, name: String, size: Long, path: String?) {
            Log.i(TAG, "bridge: complete id=$id dir=$direction name='$name' size=$size path='$path'")
            SessionManager.onTransferComplete(id, name, size, direction.toModel(), path)
            writeHistory(name, size, direction.toModel(), HistoryStatus.DONE, path)
            // If this was an outgoing transfer, advance the send queue
            if (direction == SessionConnection.Direction.OUT) {
                scope.launch(Dispatchers.Main) { processNextInQueue() }
            }
        }

        override fun onFailed(id: Long, direction: SessionConnection.Direction, name: String, reason: String) {
            Log.w(TAG, "bridge: failed id=$id dir=$direction name='$name' reason='$reason'")
            SessionManager.onTransferFailed(id, name, 0, direction.toModel(), reason)
            writeHistory(name, 0, direction.toModel(), HistoryStatus.FAILED, null)
            // Advance queue even on failure so the rest still send
            if (direction == SessionConnection.Direction.OUT) {
                scope.launch(Dispatchers.Main) { processNextInQueue() }
            }
        }

        override fun onPeerLeft() {
            Log.i(TAG, "bridge: peer left")
            SessionManager.setEnded("Peer left the group")
            stopEverything()
        }

        override fun onHeartbeatTimeout() {
            Log.w(TAG, "bridge: heartbeat timeout — attempting reconnect")
            attemptReconnect("heartbeat timeout")
        }

        override fun onDisconnected(reason: String) {
            Log.w(TAG, "bridge: disconnected reason='$reason' userLeft=${userLeft.get()}")
            if (userLeft.get()) {
                SessionManager.setEnded("You left the group")
                stopEverything()
                return
            }
            attemptReconnect(reason)
        }
    }

    // ── Auto-reconnect ─────────────────────────────────────────────────────────

    private fun attemptReconnect(reason: String) {
        if (userLeft.get()) return
        if (!isReconnecting.compareAndSet(false, true)) return

        sendQueue.clear()
        isSending = false
        connection?.close("reconnecting")
        connection = null

        SessionManager.setReconnecting()
        updateNotification("Reconnecting…")

        scope.launch(Dispatchers.IO) {
            while (reconnectAttempt.get() < MAX_RECONNECT_ATTEMPTS && !userLeft.get()) {
                val attempt = reconnectAttempt.incrementAndGet()
                val backoff = (INITIAL_BACKOFF_MS * (1L shl (attempt - 1).coerceAtMost(4)))
                    .coerceAtMost(MAX_BACKOFF_MS)

                Log.d(TAG, "Reconnect attempt $attempt/$MAX_RECONNECT_ATTEMPTS in ${backoff}ms (reason: $reason)")
                updateNotification("Reconnecting ($attempt/$MAX_RECONNECT_ATTEMPTS)…")

                delay(backoff)

                if (userLeft.get()) break

                try {
                    if (isHost) {
                        val server = ServerSocket().apply {
                            reuseAddress = true
                            bind(InetSocketAddress(ConnectionConfig.DEFAULT_PORT))
                            soTimeout = 30_000
                        }
                        val socket = try { server.accept() } finally { server.close() }
                        bringUp(socket, isHost = true, code = sessionCode)
                    } else {
                        val host = peerAddress
                        if (host == null) {
                            Log.e(TAG, "No peer address saved for client reconnect")
                            break
                        }
                        val socket = Socket().apply {
                            connect(InetSocketAddress(host, ConnectionConfig.DEFAULT_PORT), 15_000)
                        }
                        bringUp(socket, isHost = false, code = sessionCode)
                    }

                    if (!isReconnecting.get()) {
                        Log.i(TAG, "Reconnect succeeded on attempt $attempt")
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reconnect attempt $attempt failed: ${e.message}")
                }
            }

            if (!userLeft.get()) {
                isReconnecting.set(false)
                SessionManager.setEnded("Could not reconnect after $MAX_RECONNECT_ATTEMPTS attempts")
                stopEverything()
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun toOutgoing(uri: Uri): OutgoingFile? {
        return try {
            var name = "file"
            var size = 0L
            contentResolver.query(uri, null, null, null, null)?.use { c ->
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (nameIdx >= 0) name = c.getString(nameIdx) ?: name
                    if (sizeIdx >= 0) size = c.getLong(sizeIdx)
                }
            }
            val mime = contentResolver.getType(uri) ?: ""
            UriOutgoingFile(applicationContext, uri, name, size, mime)
        } catch (e: Exception) {
            Log.e(TAG, "toOutgoing failed for $uri", e)
            null
        }
    }

    private fun writeHistory(
        name: String, size: Long, direction: TransferDirection, status: HistoryStatus, path: String?
    ) {
        val item = TransferHistoryItem(
            id = UUID.randomUUID().toString(),
            title = name,
            direction = if (direction == TransferDirection.OUT) HistoryDirection.SENT else HistoryDirection.RECEIVED,
            deviceName = SessionManager.peerName().ifBlank { "Group" },
            totalSize = formatSize(size),
            fileCount = 1,
            status = status,
            dateTime = TransferHistoryRepository.todayLabel(),
            fileType = AndroidIncomingFileFactory.subFolderFor(name).lowercase(),
            filePath = path
        )
        runCatching { history.addItem(item) }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> String.format(java.util.Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024          -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
        else                   -> "$bytes B"
    }

    /**
     * Returns the currently bound [SessionActivity] if it is in a resumed state, or null.
     * Used to forward incoming-file dialogs to the visible UI.
     */
    private fun getBoundSessionActivity(): SessionActivity? {
        // Walk the app's activity stack via ActivityManager is not reliable;
        // instead we keep a weak reference updated by the activity itself.
        return boundActivity?.get()
    }

    /** Weak reference to the currently bound SessionActivity (set/cleared by the activity). */
    private var boundActivity: java.lang.ref.WeakReference<SessionActivity>? = null

    /** Called by SessionActivity.onServiceConnected / onDestroy to register itself. */
    fun registerActivity(activity: SessionActivity) {
        boundActivity = java.lang.ref.WeakReference(activity)
    }

    fun unregisterActivity() {
        boundActivity = null
    }

    private fun stopEverything() {
        sendQueue.clear()
        isSending = false
        connection = null
        runCatching { wifi.disconnect() }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "BeamShare Group", NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Active group session" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val tap = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BeamShare")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_send)
            .setContentIntent(tap)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "═══ SERVICE onDestroy ═══")
        // Guarantee the state is always terminal when the service dies.
        // scope.cancel() below can kill the reconnect coroutine before it calls
        // setEnded(), leaving the UI permanently stuck on Reconnecting/Connected.
        val s = SessionManager.state.value
        if (s !is SessionState.Ended && s !is SessionState.Idle) {
            SessionManager.setEnded("Session ended")
        }
        connection?.close("service destroyed")
        wifi.release()
        scope.cancel()
    }
    // ── Public API — session options ──────────────────────────────────────────

    /** Toggle auto-accept. When false, the bound activity is asked to confirm each incoming file. */
    fun setAutoAccept(enabled: Boolean) {
        autoAcceptEnabled = enabled
    }

    /** Update the name this device advertises to peers (takes effect on next connection). */
    fun setDeviceName(name: String) {
        deviceName = name
    }

    /**
     * Accept a pending incoming transfer that was held because autoAccept = false.
     * Resumes the SessionConnection receive path for [transferId].
     */
    fun acceptTransfer(transferId: Long) {
        val cb = pendingAcceptCallbacks.remove(transferId)
        if (cb != null) {
            cb(true)
        } else {
            Log.w(TAG, "acceptTransfer: no pending callback for id=$transferId")
        }
    }

    /**
     * Reject a pending incoming transfer.
     */
    fun rejectTransfer(transferId: Long) {
        val cb = pendingAcceptCallbacks.remove(transferId)
        if (cb != null) {
            cb(false)
            SessionManager.onTransferFailed(transferId, "", 0, TransferDirection.IN, "Declined by user")
        } else {
            Log.w(TAG, "rejectTransfer: no pending callback for id=$transferId")
        }
    }

    private fun SessionConnection.Direction.toModel(): TransferDirection =
        if (this == SessionConnection.Direction.OUT) TransferDirection.OUT else TransferDirection.IN
}