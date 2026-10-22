package com.example.beamshare.session

/** A member of the group (used by the Create-group member list). */
data class GroupMember(
    val name: String,
    val isHost: Boolean,
    val joinedAtMs: Long = System.currentTimeMillis()
)

/** The single source of truth for "where is the session right now". */
sealed class SessionState {
    data object Idle : SessionState()
    data class Hosting(
        val code: String,
        val members: List<GroupMember> = emptyList()
    ) : SessionState()
    data object Joining : SessionState()
    data class Connected(
        val peerName: String,
        val sinceMs: Long
    ) : SessionState()
    data object Reconnecting : SessionState()
    data class Ended(val reason: String) : SessionState()
}

enum class TransferDirection { IN, OUT }

enum class TransferStatus { ACTIVE, COMPLETED, FAILED, CANCELLED }

data class LiveTransfer(
    val id: Long,
    val name: String,
    val size: Long,
    val direction: TransferDirection,
    val peerName: String,
    val progress: Int = 0,
    val transferred: Long = 0,
    val status: TransferStatus = TransferStatus.ACTIVE,
    val savedPath: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis()
)