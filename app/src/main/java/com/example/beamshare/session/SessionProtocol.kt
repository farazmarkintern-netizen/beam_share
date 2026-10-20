package com.example.beamshare.session

import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * BeamShare group-session wire protocol.
 *
 * ONE persistent TCP socket carries every message for the whole session, in BOTH
 * directions at the same time. Every message is a self-contained "frame":
 *
 *     [1 byte: frame type][type-specific body...]
 *
 * All multi-byte numbers are big-endian (DataOutputStream defaults). Strings are
 * written as [int length][UTF-8 bytes].
 *
 * Because file chunks are small, independent frames, the reader can interleave an
 * incoming file's chunks with an outgoing file's chunks — so a phone can send and
 * receive simultaneously. Each frame is written atomically (see SessionConnection's
 * write mutex); no single file ever "holds" the socket.
 *
 * This file is pure Kotlin/JVM (no Android imports) so it can be unit-tested off-device.
 */
object SessionProtocol {

    /** Bytes that identify a BeamShare stream right after the socket opens. */
    const val MAGIC = 0x4253_3031 // "BS01"
    const val PROTOCOL_VERSION = 1

    /** Max bytes in a single FILE_CHUNK payload. */
    const val CHUNK_SIZE = 64 * 1024

    object Frame {
        const val HELLO: Int = 1        // client -> host : int ver, str name, str code
        const val HELLO_OK: Int = 2     // host -> client : str hostName
        const val HELLO_FAIL: Int = 3   // host -> client : int triesLeft
        const val FILE_OFFER: Int = 10  // long id, long size, str name, str mime
        const val FILE_CHUNK: Int = 11  // long id, int len, bytes[len]
        const val FILE_DONE: Int = 12   // long id
        const val FILE_CANCEL: Int = 13 // long id
        const val PING: Int = 20        // (no body)
        const val PONG: Int = 21        // (no body)
        const val LEAVE: Int = 30       // (no body)
    }

    // ── String helpers ──────────────────────────────────────────────────────
    fun writeString(out: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    fun readString(input: DataInputStream, maxLen: Int = 64 * 1024): String {
        val len = input.readInt()
        require(len in 0..maxLen) { "String length out of range: $len" }
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }
}

/** Result of the opening handshake. */
sealed class HandshakeResult {
    data class Ok(val peerName: String) : HandshakeResult()
    data class Failed(val triesLeft: Int) : HandshakeResult()
    data class Error(val message: String) : HandshakeResult()
}