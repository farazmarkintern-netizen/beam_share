package com.example.beamshare.session

/** A member of the group (used by the Create-group member list). */
data class GroupMember(
    val name: String,
    val isHost: Boolean,
    val joinedAtMs: Long = System.currentTimeMillis()
)

/** The single source of truth for "where is the session right now". */
sealed class SessionState {
    /** Not in a group. */
    data object Idle : SessionState()

    /** This device created a group and is showing its code, waiting for joiners. */
    data class Hosting(
        val code: String,
        val members: List<GroupMember> = emptyList()
    ) : SessionState()

    /** This device is joining a group (connecting / verifying code). */
    data object Joining : SessionState()

    /** Connected and live. Either side can send; incoming is auto-accepted. */
    data class Connected(
        val peerName: String,
        val sinceMs: Long
    ) : SessionState()

    /** Link looked dead; auto-retrying to the same peer. */
    data object Reconnecting : SessionState()

    /** Session is over. */
    data class Ended(val reason: String) : SessionState()
}

enum class TransferDirection { IN, OUT }

enum class TransferStatus { ACTIVE, COMPLETED, FAILED }

/**
 * A single transfer, live or finished.
 *  - The home "LIVE NOW" card shows items whose status == ACTIVE.
 *  - Completed / failed items are what the Transfers tab reads as history.
 */
data class LiveTransfer(
    val id: Long,
    val name: String,
    val size: Long,
    val direction: TransferDirection,
    val peerName: String,
    val progress: Int = 0,            // 0..100
    val transferred: Long = 0,
    val status: TransferStatus = TransferStatus.ACTIVE,
    val savedPath: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)
