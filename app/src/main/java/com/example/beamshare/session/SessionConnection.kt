package com.example.beamshare.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Owns ONE already-connected [Socket] for the whole session and runs it full-duplex.
 *
 * Design that fixes the old "disconnect after every transfer" bug:
 *  - The socket is opened once and kept open. Nothing here ever closes it after a
 *    file; it closes only on LEAVE, a dead heartbeat, or a real I/O error.
 *  - A single reader coroutine reads frames and dispatches them.
 *  - Every outgoing frame is written under [writeMutex], one frame at a time, so a
 *    file being SENT and a file being RECEIVED interleave their chunks freely. Both
 *    directions are live at once.
 *  - A failed file fails only that file; the session stays up.
 *
 * Pure Kotlin/JVM — no Android imports — so it is unit-testable over a loopback socket.
 */
class SessionConnection(
    private val socket: Socket,
    private val selfName: String,
    private val incomingFactory: IncomingFileFactory,
    private val scope: CoroutineScope,
    private val listener: Listener,
    private val pingIntervalMs: Long = 5_000,
    private val pongTimeoutMs: Long = 30_000
) {

    interface Listener {
        /** A new incoming file started. */
        fun onIncomingStart(id: Long, name: String, size: Long) {}
        /** Progress for an in-flight transfer (both directions). Already throttled. */
        fun onProgress(id: Long, direction: Direction, name: String, sent: Long, total: Long) {}
        /** A transfer finished. [path] is the saved path for incoming files, null for outgoing. */
        fun onComplete(id: Long, direction: Direction, name: String, size: Long, path: String?) {}
        /** A transfer failed (that file only — the session stays connected). */
        fun onFailed(id: Long, direction: Direction, name: String, reason: String) {}
        /** The peer tapped "Leave session". */
        fun onPeerLeft() {}
        /** No PONG within the timeout — the link looks dead; caller should go to Reconnecting. */
        fun onHeartbeatTimeout() {}
        /** The socket closed or errored. */
        fun onDisconnected(reason: String) {}
    }

    enum class Direction { IN, OUT }

    private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
    private val writeMutex = Mutex()

    private val nextId = AtomicLong(1)
    private val incoming = ConcurrentHashMap<Long, IncomingState>()
    @Volatile private var lastPongAt = System.currentTimeMillis()
    @Volatile private var closed = false

    private var readerJob: Job? = null
    private var heartbeatJob: Job? = null

    private class IncomingState(
        val file: IncomingFile,
        var received: Long = 0,
        var lastEmit: Long = 0,
        var lastPct: Int = -1
    )

    // ── Handshake (run once, before start) ───────────────────────────────────

    /** Host side: read the client's HELLO, verify the code, reply. */
    suspend fun handshakeAsHost(expectedCode: String, triesLeft: Int): HandshakeResult =
        withContext(Dispatchers.IO) {
            try {
                if (input.readInt() != SessionProtocol.MAGIC) return@withContext HandshakeResult.Error("Bad stream")
                if (input.readByte().toInt() != SessionProtocol.Frame.HELLO) return@withContext HandshakeResult.Error("Expected HELLO")
                input.readInt() // protocol version (reserved)
                val peerName = SessionProtocol.readString(input)
                val code = SessionProtocol.readString(input).trim()
                if (code != expectedCode.trim()) {
                    writeFrameBlocking {
                        it.writeByte(SessionProtocol.Frame.HELLO_FAIL)
                        it.writeInt(triesLeft)
                    }
                    return@withContext HandshakeResult.Failed(triesLeft)
                }
                writeFrameBlocking {
                    it.writeByte(SessionProtocol.Frame.HELLO_OK)
                    SessionProtocol.writeString(it, selfName)
                }
                HandshakeResult.Ok(peerName)
            } catch (e: Exception) {
                HandshakeResult.Error(e.message ?: "Handshake failed")
            }
        }

    /** Client side: send HELLO, await the host's verdict. */
    suspend fun handshakeAsClient(code: String): HandshakeResult =
        withContext(Dispatchers.IO) {
            try {
                writeFrameBlocking {
                    it.writeInt(SessionProtocol.MAGIC)
                    it.writeByte(SessionProtocol.Frame.HELLO)
                    it.writeInt(SessionProtocol.PROTOCOL_VERSION)
                    SessionProtocol.writeString(it, selfName)
                    SessionProtocol.writeString(it, code.trim())
                }
                when (input.readByte().toInt()) {
                    SessionProtocol.Frame.HELLO_OK -> HandshakeResult.Ok(SessionProtocol.readString(input))
                    SessionProtocol.Frame.HELLO_FAIL -> HandshakeResult.Failed(input.readInt())
                    else -> HandshakeResult.Error("Unexpected handshake reply")
                }
            } catch (e: Exception) {
                HandshakeResult.Error(e.message ?: "Handshake failed")
            }
        }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    fun start() {
        lastPongAt = System.currentTimeMillis()
        readerJob = scope.launch(Dispatchers.IO) { readerLoop() }
        heartbeatJob = scope.launch(Dispatchers.IO) { heartbeatLoop() }
    }

    /**
     * Cancel an incoming transfer that we have already started receiving
     * (e.g. user rejected the file when autoAccept is OFF).
     * Removes the local state, aborts the temp file, and tells the sender to stop.
     */
    fun cancelIncomingTransfer(id: Long) {
        val state = incoming.remove(id) ?: return
        sinks.remove(state)?.runCatching { close() }
        state.file.abort()
        // Notify the sender so it stops streaming chunks for this id
        scope.launch(Dispatchers.IO) {
            runCatching {
                writeFrame {
                    it.writeByte(SessionProtocol.Frame.FILE_CANCEL)
                    it.writeLong(id)
                }
            }
        }
        listener.onFailed(id, Direction.IN, state.file.name, "Declined by user")
    }

    /** Send LEAVE to the peer, then close. */
    fun leave() {
        scope.launch(Dispatchers.IO) {
            try { writeFrame { it.writeByte(SessionProtocol.Frame.LEAVE) } } catch (_: Exception) {}
            close("left")
        }
    }

    fun close(reason: String) {
        if (closed) return
        closed = true
        readerJob?.cancel()
        heartbeatJob?.cancel()
        incoming.values.forEach { runCatching { it.file.abort() } }
        incoming.clear()
        runCatching { socket.close() }
        // FIX 1: Do NOT call onDisconnected here when it was a clean user-initiated close.
        // Only call onDisconnected for unexpected closes (reader/heartbeat errors call it
        // directly). For "left" and "service destroyed" we skip the callback to avoid
        // spurious reconnect attempts after intentional shutdown.
        if (reason != "left" && reason != "service destroyed") {
            listener.onDisconnected(reason)
        }
    }

    // ── Sending ──────────────────────────────────────────────────────────────

    /**
     * Send a list of files sequentially (one at a time).
     * GroupSessionService already sends one file per call via its send queue,
     * so this just launches the single file's coroutine.
     */
    fun sendFiles(files: List<OutgoingFile>) {
        for (file in files) {
            val id = nextId.getAndIncrement()
            scope.launch(Dispatchers.IO) { sendOne(id, file) }
        }
    }

    private suspend fun sendOne(id: Long, file: OutgoingFile) {
        try {
            writeFrame {
                it.writeByte(SessionProtocol.Frame.FILE_OFFER)
                it.writeLong(id)
                it.writeLong(file.size)
                SessionProtocol.writeString(it, file.name)
                SessionProtocol.writeString(it, file.mime)
            }
            var sent = 0L
            var lastEmit = 0L
            var lastPct = -1
            val buffer = ByteArray(SessionProtocol.CHUNK_SIZE)
            file.openInput().use { stream ->
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    // FIX 2: write only the bytes actually read — never a full-size
                    // buffer with stale tail bytes when the last read is smaller.
                    writeFrame {
                        it.writeByte(SessionProtocol.Frame.FILE_CHUNK)
                        it.writeLong(id)
                        it.writeInt(read)
                        it.write(buffer, 0, read)
                    }
                    sent += read
                    val now = System.currentTimeMillis()
                    val pct = if (file.size > 0) ((sent * 100) / file.size).toInt() else 0
                    if (now - lastEmit >= 200 || pct != lastPct) {
                        lastEmit = now; lastPct = pct
                        listener.onProgress(id, Direction.OUT, file.name, sent, file.size)
                    }
                }
            }
            writeFrame {
                it.writeByte(SessionProtocol.Frame.FILE_DONE)
                it.writeLong(id)
            }
            listener.onProgress(id, Direction.OUT, file.name, file.size, file.size)
            listener.onComplete(id, Direction.OUT, file.name, file.size, null)
        } catch (e: Exception) {
            if (!closed) {
                runCatching {
                    writeFrame {
                        it.writeByte(SessionProtocol.Frame.FILE_CANCEL)
                        it.writeLong(id)
                    }
                }
            }
            listener.onFailed(id, Direction.OUT, file.name, e.message ?: "Send failed")
            // FIX 3: a send exception must NOT close the whole session.
            // The session stays alive; GroupSessionService's queue will move to the next file.
        }
    }

    // ── Reading ──────────────────────────────────────────────────────────────

    private suspend fun readerLoop() {
        try {
            while (scope.isActive && !closed) {
                val type = input.readByte().toInt()
                when (type) {
                    SessionProtocol.Frame.FILE_OFFER  -> handleOffer()
                    SessionProtocol.Frame.FILE_CHUNK  -> handleChunk()
                    SessionProtocol.Frame.FILE_DONE   -> handleDone()
                    SessionProtocol.Frame.FILE_CANCEL -> handleCancel()
                    SessionProtocol.Frame.PING -> writeFrame { it.writeByte(SessionProtocol.Frame.PONG) }
                    SessionProtocol.Frame.PONG -> lastPongAt = System.currentTimeMillis()
                    SessionProtocol.Frame.LEAVE -> {
                        listener.onPeerLeft()
                        closed = true          // mark closed BEFORE calling close() to suppress onDisconnected
                        readerJob?.cancel()
                        heartbeatJob?.cancel()
                        incoming.values.forEach { runCatching { it.file.abort() } }
                        incoming.clear()
                        runCatching { socket.close() }
                        return
                    }
                    else -> { /* unknown frame type: ignore for forward-compat */ }
                }
            }
        } catch (e: EOFException) {
            if (!closed) {
                closed = true
                listener.onDisconnected("connection closed")
            }
        } catch (e: Exception) {
            if (!closed) {
                closed = true
                listener.onDisconnected(e.message ?: "read error")
            }
        }
    }

    private fun handleOffer() {
        val id = input.readLong()
        val size = input.readLong()
        val name = SessionProtocol.readString(input)
        val mime = SessionProtocol.readString(input)
        val file = incomingFactory.create(name, size, mime)
        incoming[id] = IncomingState(file)
        listener.onIncomingStart(id, name, size)
    }

    private fun handleChunk() {
        val id = input.readLong()
        val len = input.readInt()
        val buffer = ByteArray(len)
        input.readFully(buffer)
        val state = incoming[id] ?: return // cancelled/unknown: drain and drop
        try {
            sinkOf(state).write(buffer, 0, len)
            state.received += len
            val now = System.currentTimeMillis()
            val pct = if (state.file.size > 0) ((state.received * 100) / state.file.size).toInt() else 0
            if (now - state.lastEmit >= 200 || pct != state.lastPct) {
                state.lastEmit = now; state.lastPct = pct
                listener.onProgress(id, Direction.IN, state.file.name, state.received, state.file.size)
            }
        } catch (e: Exception) {
            incoming.remove(id)?.file?.abort()
            listener.onFailed(id, Direction.IN, state.file.name, e.message ?: "Write failed")
        }
    }

    // Open the sink once and cache it on the state.
    private val sinks = ConcurrentHashMap<IncomingState, java.io.OutputStream>()
    private fun sinkOf(state: IncomingState): java.io.OutputStream =
        sinks.getOrPut(state) { state.file.output() }

    private fun handleDone() {
        val id = input.readLong()
        val state = incoming.remove(id) ?: return
        try {
            sinks.remove(state)?.flush()
            val path = state.file.finish()
            listener.onProgress(id, Direction.IN, state.file.name, state.file.size, state.file.size)
            listener.onComplete(id, Direction.IN, state.file.name, state.file.size, path)
        } catch (e: Exception) {
            state.file.abort()
            listener.onFailed(id, Direction.IN, state.file.name, e.message ?: "Finalize failed")
        }
    }

    private fun handleCancel() {
        val id = input.readLong()
        val state = incoming.remove(id) ?: return
        sinks.remove(state)
        state.file.abort()
        listener.onFailed(id, Direction.IN, state.file.name, "Cancelled by sender")
    }

    // ── Heartbeat ────────────────────────────────────────────────────────────

    private suspend fun heartbeatLoop() {
        while (scope.isActive && !closed) {
            delay(pingIntervalMs)
            if (closed) return
            try {
                writeFrame { it.writeByte(SessionProtocol.Frame.PING) }
            } catch (_: Exception) {
                // Socket already dead — reader will catch the real error
                return
            }
            if (System.currentTimeMillis() - lastPongAt > pongTimeoutMs) {
                if (!closed) {
                    closed = true
                    listener.onHeartbeatTimeout()
                    runCatching { socket.close() }
                }
                return
            }
        }
    }

    // ── Frame writing (every outgoing frame goes through here) ────────────────

    private suspend fun writeFrame(block: (DataOutputStream) -> Unit) {
        writeMutex.withLock {
            block(output)
            output.flush()
        }
    }

    /** Blocking variant for the handshake, which runs before coroutine loops start. */
    private fun writeFrameBlocking(block: (DataOutputStream) -> Unit) {
        synchronized(output) {
            block(output)
            output.flush()
        }
    }
}