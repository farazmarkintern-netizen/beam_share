package com.example.beamshare.session

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.ui.send.SendFilesActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

class SessionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SessionActivity"
        const val EXTRA_REMOTE_DEVICE_NAME = "extra_remote_device_name"
        const val EXTRA_LOCAL_DEVICE_NAME  = "extra_local_device_name"
        const val EXTRA_PENDING_URIS       = "extra_pending_uris"
    }

    private lateinit var tvLocalDeviceName: TextView
    private lateinit var tvRemoteDeviceName: TextView
    private lateinit var tvConnectedDuration: TextView
    private lateinit var connectionBadge: LinearLayout
    private lateinit var statusDot: View
    private lateinit var tvStatSent: TextView
    private lateinit var tvStatReceived: TextView
    private lateinit var tvStatTotal: TextView
    private lateinit var tvRemoteLiveBadge: TextView
    private lateinit var rvSessionFiles: RecyclerView
    private lateinit var incomingBanner: LinearLayout

    private lateinit var sessionFileAdapter: SessionFileAdapter
    private var localDeviceName = ""

    private var pendingUris: ArrayList<Uri>? = null
    private var pendingUrisSent = false

    private var groupService: GroupSessionService? = null
    private var serviceBound = false

    private val serviceConn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            groupService = (binder as GroupSessionService.LocalBinder).getService()
            serviceBound = true
            if (SessionManager.state.value is SessionState.Connected) sendPendingFiles()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            groupService = null
            serviceBound = false
        }
    }

    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
            groupService?.sendUris(uris)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var sessionStartMs = 0L
    private val durationRunnable = object : Runnable {
        override fun run() {
            updateDuration()
            handler.postDelayed(this, 1000)
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_session)

        @Suppress("DEPRECATION")
        pendingUris = intent.getParcelableArrayListExtra(EXTRA_PENDING_URIS)

        setupEdgeToEdge()
        initViews()
        setupRecyclerView()
        bindGroupService()
        observeSession()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        @Suppress("DEPRECATION")
        val newUris = intent.getParcelableArrayListExtra<Uri>(EXTRA_PENDING_URIS)
        if (!newUris.isNullOrEmpty()) {
            pendingUris = newUris
            pendingUrisSent = false
            if (SessionManager.state.value is SessionState.Connected && serviceBound) {
                sendPendingFiles()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(durationRunnable)
        if (serviceBound) {
            unbindService(serviceConn)
            serviceBound = false
        }
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private fun setupEdgeToEdge() {
        val rootLayout = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, insets.top, 0, insets.bottom)
            windowInsets
        }
    }

    private fun initViews() {
        localDeviceName = intent.getStringExtra(EXTRA_LOCAL_DEVICE_NAME)
            ?: "${Build.MANUFACTURER} ${Build.MODEL}"

        tvLocalDeviceName    = findViewById(R.id.tvLocalDeviceName)
        tvRemoteDeviceName   = findViewById(R.id.tvRemoteDeviceName)
        tvConnectedDuration  = findViewById(R.id.tvConnectedDuration)
        connectionBadge      = findViewById(R.id.connectionBadge)
        statusDot            = findViewById(R.id.statusDot)
        tvRemoteLiveBadge    = findViewById(R.id.tvRemoteLiveBadge)
        tvStatSent           = findViewById(R.id.tvStatSent)
        tvStatReceived       = findViewById(R.id.tvStatReceived)
        tvStatTotal          = findViewById(R.id.tvStatTotal)
        rvSessionFiles       = findViewById(R.id.rvSessionFiles)
        incomingBanner       = findViewById(R.id.incomingBanner)

        tvLocalDeviceName.text = localDeviceName
        intent.getStringExtra(EXTRA_REMOTE_DEVICE_NAME)?.let { tvRemoteDeviceName.text = it }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnMenu).setOnClickListener { showSessionMenuDialog() }
        findViewById<LinearLayout>(R.id.btnSendFiles).setOnClickListener {
            startActivity(Intent(this, SendFilesActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.btnReadyToReceive).setOnClickListener { }
    }

    private fun setupRecyclerView() {
        sessionFileAdapter = SessionFileAdapter(
            onCancel = { file ->
                // Cancel by Long id — parse back from the String id
                groupService?.cancelTransfer(file.id.toLongOrNull() ?: return@SessionFileAdapter)
            },
            onDelete = { file ->
                SessionManager.removeTransfer(file.id.toLongOrNull() ?: return@SessionFileAdapter)
            }
        )
        rvSessionFiles.layoutManager = LinearLayoutManager(this)
        rvSessionFiles.adapter = sessionFileAdapter
        rvSessionFiles.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: android.graphics.Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                outRect.bottom = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._8sdp)
            }
        })
    }

    private fun bindGroupService() {
        bindService(Intent(this, GroupSessionService::class.java), serviceConn, Context.BIND_AUTO_CREATE)
    }

    // ── Observe ────────────────────────────────────────────────────────────────

    private fun observeSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    SessionManager.state.collect { state ->
                        when (state) {
                            is SessionState.Connected -> {
                                tvRemoteDeviceName.text = state.peerName
                                sessionStartMs = state.sinceMs
                                startDurationTimer()
                                setConnectionBadge(
                                    R.drawable.bg_badge_connected,
                                    R.drawable.bg_dot_green,
                                    getString(R.string.connected),
                                    0xFF16A34A.toInt()
                                )
                                if (serviceBound) sendPendingFiles()
                            }
                            is SessionState.Reconnecting -> {
                                handler.removeCallbacks(durationRunnable)
                                setConnectionBadge(
                                    R.drawable.bg_badge_reconnecting,
                                    R.drawable.bg_dot_amber,
                                    getString(R.string.session_reconnecting),
                                    0xFFD97706.toInt()
                                )
                            }
                            is SessionState.Hosting, is SessionState.Joining -> {
                                setConnectionBadge(
                                    R.drawable.bg_badge_reconnecting,
                                    R.drawable.bg_dot_amber,
                                    getString(R.string.session_connecting),
                                    0xFFD97706.toInt()
                                )
                            }
                            is SessionState.Ended -> {
                                handler.removeCallbacks(durationRunnable)
                                setConnectionBadge(
                                    R.drawable.bg_badge_disconnected,
                                    R.drawable.bg_dot_red,
                                    getString(R.string.session_disconnected),
                                    0xFFDC2626.toInt()
                                )
                                handler.postDelayed({ finish() }, 2000)
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    SessionManager.transfers.collect { transfers ->
                        updateTransferUI(transfers)
                    }
                }
            }
        }
    }

    // ── Transfer UI ────────────────────────────────────────────────────────────

    private fun updateTransferUI(transfers: List<LiveTransfer>) {
        val files = transfers.map { t ->
            SessionFile(
                id        = t.id.toString(),   // LiveTransfer.id is Long → String
                fileName  = t.name,
                fileSize  = formatSize(t.size),
                direction = if (t.direction == TransferDirection.OUT) FileDirection.SENT
                else FileDirection.RECEIVED,
                status    = when (t.status) {
                    TransferStatus.ACTIVE    -> FileStatus.ACTIVE
                    TransferStatus.COMPLETED -> FileStatus.COMPLETED
                    TransferStatus.FAILED    -> FileStatus.FAILED
                    TransferStatus.CANCELLED -> FileStatus.CANCELLED
                },
                progress  = t.progress,
                // savedPath is a file-system path → wrap in Uri for the adapter
                savedUri  = t.savedPath?.let { Uri.parse("file://$it") }
            )
        }
        sessionFileAdapter.submitList(files)

        val sentCount     = transfers.count { it.direction == TransferDirection.OUT && it.status == TransferStatus.COMPLETED }
        val receivedCount = transfers.count { it.direction == TransferDirection.IN  && it.status == TransferStatus.COMPLETED }
        val totalBytes    = transfers.filter { it.status == TransferStatus.COMPLETED }.sumOf { it.size }

        tvStatSent.text     = sentCount.toString()
        tvStatReceived.text = receivedCount.toString()
        tvStatTotal.text    = formatSize(totalBytes)

        val activeIncoming = transfers.firstOrNull {
            it.direction == TransferDirection.IN && it.status == TransferStatus.ACTIVE
        }
        if (activeIncoming != null) {
            showIncomingBanner(
                activeIncoming.name,
                "${formatSize(activeIncoming.transferred)} / ${formatSize(activeIncoming.size)}",
                activeIncoming.progress
            )
        } else {
            hideIncomingBanner()
        }
    }

    // ── Connection badge ───────────────────────────────────────────────────────

    private fun setConnectionBadge(badgeBg: Int, dotBg: Int, text: String, textColor: Int) {
        connectionBadge.setBackgroundResource(badgeBg)
        statusDot.setBackgroundResource(dotBg)
        tvConnectedDuration.text = text
        tvConnectedDuration.setTextColor(textColor)

        val isConnected = SessionManager.state.value is SessionState.Connected
        tvRemoteLiveBadge.text = if (isConnected) getString(R.string.session_live)
        else getString(R.string.session_disconnected)
        tvRemoteLiveBadge.setTextColor(if (isConnected) 0xFF16A34A.toInt() else 0xFFDC2626.toInt())
        tvRemoteLiveBadge.setBackgroundResource(
            if (isConnected) R.drawable.bg_badge_live else R.drawable.bg_badge_offline
        )
    }

    // ── Duration timer ─────────────────────────────────────────────────────────

    private fun startDurationTimer() {
        handler.removeCallbacks(durationRunnable)
        handler.post(durationRunnable)
    }

    private fun updateDuration() {
        if (sessionStartMs <= 0) return
        if (SessionManager.state.value !is SessionState.Connected) return
        val elapsed = (System.currentTimeMillis() - sessionStartMs) / 1000
        val minutes = elapsed / 60
        val seconds = elapsed % 60
        tvConnectedDuration.text = if (minutes > 0)
            getString(R.string.connected) + " · ${minutes}m ${seconds}s"
        else
            getString(R.string.connected) + " · ${seconds}s"
    }

    // ── Incoming banner ────────────────────────────────────────────────────────

    private fun showIncomingBanner(title: String, subtitle: String, progress: Int) {
        incomingBanner.visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvBannerTitle).text    = title
        findViewById<TextView>(R.id.tvBannerSubtitle).text = subtitle
        findViewById<TextView>(R.id.tvBannerProgress).text = "$progress%"
    }

    private fun hideIncomingBanner() {
        incomingBanner.visibility = View.GONE
    }

    // ── Size formatter ─────────────────────────────────────────────────────────

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576     -> String.format("%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024          -> String.format("%.0f KB", bytes / 1024.0)
        else                   -> "$bytes B"
    }

    // ── Pending files ──────────────────────────────────────────────────────────

    private fun sendPendingFiles() {
        if (pendingUrisSent) return
        val uris = pendingUris ?: return
        if (uris.isEmpty()) return
        val svc = groupService ?: return
        pendingUrisSent = true
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        svc.sendUris(uris)
    }

    // ── Menus ──────────────────────────────────────────────────────────────────

    private fun showSessionMenuDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_session_menu, null)

        view.findViewById<TextView>(R.id.tvCurrentDeviceName).text = localDeviceName
        view.findViewById<TextView>(R.id.tvFolderPath).text = "/BeamShare/"

        view.findViewById<LinearLayout>(R.id.menuRename).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<LinearLayout>(R.id.menuOpenFolder).setOnClickListener {
            dialog.dismiss()
        }
        view.findViewById<SwitchCompat>(R.id.switchAutoAccept).setOnCheckedChangeListener { _, _ -> }
        view.findViewById<LinearLayout>(R.id.menuLeave).setOnClickListener {
            dialog.dismiss()
            showLeaveSessionDialog()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    private fun showLeaveSessionDialog() {
        val peerName = SessionManager.peerName().ifEmpty { "the peer" }
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_leave_session, null)

        view.findViewById<TextView>(R.id.tvLeaveDesc).text =
            getString(R.string.session_leave_desc, peerName)

        view.findViewById<TextView>(R.id.btnStay).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnLeave).setOnClickListener {
            dialog.dismiss()
            groupService?.leave()
            finish()
        }

        dialog.setContentView(view)
        dialog.show()
    }
}