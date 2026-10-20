package com.example.beamshare.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * App-wide, process-singleton holder of session + live-transfer state.
 *
 * GroupSessionService writes to it from the SessionConnection callbacks; every
 * screen (HomeFragment, SessionActivity, SendFilesActivity, the Create-group screen)
 * just collects these flows with repeatOnLifecycle(STARTED). No polling, and the UI
 * is correct after rotation or returning from the background because the state lives
 * here, not in any one screen.
 *
 * Kept as a plain object (not a Hilt-injected type) so it survives Activity/Fragment
 * recreation and is trivially testable.
 */
object SessionManager {

    private val _state = MutableStateFlow<SessionState>(SessionState.Idle)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _transfers = MutableStateFlow<List<LiveTransfer>>(emptyList())
    val transfers: StateFlow<List<LiveTransfer>> = _transfers.asStateFlow()

    /** Only the transfers still in flight — this is what the LIVE NOW card binds to. */
    val liveTransfers: List<LiveTransfer>
        get() = _transfers.value.filter { it.status == TransferStatus.ACTIVE }

    // ── Session state ────────────────────────────────────────────────────────

    fun setIdle() {
        _state.value = SessionState.Idle
        _transfers.value = emptyList()
    }

    fun setHosting(code: String, selfName: String) {
        _state.value = SessionState.Hosting(
            code = code,
            members = listOf(GroupMember(name = selfName, isHost = true))
        )
    }

    fun setJoining() { _state.value = SessionState.Joining }

    fun addMember(name: String) {
        val s = _state.value
        if (s is SessionState.Hosting && s.members.none { it.name == name }) {
            _state.value = s.copy(members = s.members + GroupMember(name = name, isHost = false))
        }
    }

    fun setConnected(peerName: String) {
        _state.value = SessionState.Connected(peerName = peerName, sinceMs = System.currentTimeMillis())
    }

    fun setReconnecting() { _state.value = SessionState.Reconnecting }

    fun setEnded(reason: String) {
        _state.value = SessionState.Ended(reason)
        // Mark any still-active transfers as failed so the LIVE NOW card clears.
        _transfers.value = _transfers.value.map {
            if (it.status == TransferStatus.ACTIVE)
                it.copy(status = TransferStatus.FAILED, updatedAtMs = System.currentTimeMillis())
            else it
        }
    }

    fun peerName(): String = (_state.value as? SessionState.Connected)?.peerName ?: ""

    // ── Live transfers ───────────────────────────────────────────────────────

    fun onTransferStart(id: Long, name: String, size: Long, direction: TransferDirection) {
        upsert(
            LiveTransfer(
                id = id, name = name, size = size, direction = direction,
                peerName = peerName(), status = TransferStatus.ACTIVE
            )
        )
    }

    fun onTransferProgress(id: Long, name: String, size: Long, direction: TransferDirection, transferred: Long) {
        val pct = if (size > 0) ((transferred * 100) / size).toInt().coerceIn(0, 100) else 0
        val existing = _transfers.value.firstOrNull { it.id == id && it.direction == direction }
        if (existing == null) {
            upsert(
                LiveTransfer(
                    id = id, name = name, size = size, direction = direction,
                    peerName = peerName(), progress = pct, transferred = transferred
                )
            )
        } else {
            upsert(existing.copy(progress = pct, transferred = transferred, updatedAtMs = System.currentTimeMillis()))
        }
    }

    fun onTransferComplete(id: Long, name: String, size: Long, direction: TransferDirection, savedPath: String?) {
        val existing = _transfers.value.firstOrNull { it.id == id && it.direction == direction }
        upsert(
            (existing ?: LiveTransfer(id, name, size, direction, peerName())).copy(
                progress = 100, transferred = size, status = TransferStatus.COMPLETED,
                savedPath = savedPath, updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun onTransferFailed(id: Long, name: String, size: Long, direction: TransferDirection, reason: String) {
        val existing = _transfers.value.firstOrNull { it.id == id && it.direction == direction }
        upsert(
            (existing ?: LiveTransfer(id, name, size, direction, peerName())).copy(
                status = TransferStatus.FAILED, updatedAtMs = System.currentTimeMillis()
            )
        )
    }

    fun clearFinished() {
        _transfers.value = _transfers.value.filter { it.status == TransferStatus.ACTIVE }
    }

    private fun upsert(item: LiveTransfer) {
        val list = _transfers.value.toMutableList()
        val idx = list.indexOfFirst { it.id == item.id && it.direction == item.direction }
        if (idx >= 0) list[idx] = item else list.add(item)
        _transfers.value = list
    }
}
