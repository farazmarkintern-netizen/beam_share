package com.example.beamshare.ui.transfers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.connection.FileTransferService
import com.example.beamshare.connection.TransferState
import com.example.beamshare.data.TransferHistoryRepository
import com.example.beamshare.data.TransferHistoryRepository.Companion.dateStamp
import com.example.beamshare.data.TransferHistoryRepository.Companion.friendlyDate
import com.example.beamshare.model.HistoryStatus
import com.example.beamshare.model.TransferDirection
import com.example.beamshare.model.TransferFile
import com.example.beamshare.model.TransferHistoryItem
import com.example.beamshare.model.TransferStatus
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.UUID

class TransferProgressActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvSavingTo: TextView
    private lateinit var tvPercentage: TextView
    private lateinit var tvFileCount: TextView
    private lateinit var tvCurrentFileName: TextView
    private lateinit var tvCurrentFileSize: TextView
    private lateinit var tvStatSpeed: TextView
    private lateinit var tvStatSpeedUnit: TextView
    private lateinit var tvStatTimeLeft: TextView
    private lateinit var tvStatTotal: TextView
    private lateinit var tvStatTotalLabel: TextView
    private lateinit var tvQueueLabel: TextView
    private lateinit var progressRing: ProgressBar
    private lateinit var rvTransferQueue: RecyclerView
    private lateinit var btnAction: TextView
    private lateinit var queueAdapter: TransferQueueAdapter

    private var transferService: FileTransferService? = null
    private var serviceBound = false
    private var isSender = true
    private var remoteDeviceName = "Unknown device"

    // Sender file list (passed from DeviceDiscoveryActivity)
    private var senderFileUris: ArrayList<Uri> = arrayListOf()
    private var senderFileNames: ArrayList<String> = arrayListOf()
    private var senderFileSizes: LongArray = longArrayOf()

    // Track stats for history
    private var transferStartTimeMs = 0L
    private var lastFileName = ""
    private var lastTotalBytes = 0L
    private var lastSpeedBytesPerSec = 0L
    private var lastFileCount = 0

    // Track cumulative bytes sent/received
    private var cumulativeBytes = 0L

    // Receiver: accumulate completed files for the queue
    private val receivedFiles = mutableListOf<TransferFile>()
    private var lastReceiverFileIndex = 0

    companion object {
        const val EXTRA_IS_SENDER = "is_sender"
        const val EXTRA_REMOTE_DEVICE_NAME = "remote_device_name"
        const val EXTRA_FILE_URIS = "file_uris"
        const val EXTRA_FILE_NAMES = "file_names"
        const val EXTRA_FILE_SIZES = "file_sizes"
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as FileTransferService.TransferBinder).getService()
            transferService = service
            serviceBound = true
            observeTransferState(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            transferService = null
            serviceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_transfer_progress)

        isSender = intent.getBooleanExtra(EXTRA_IS_SENDER, true)
        remoteDeviceName = intent.getStringExtra(EXTRA_REMOTE_DEVICE_NAME) ?: "Unknown device"
        senderFileUris = intent.getParcelableArrayListExtra(EXTRA_FILE_URIS) ?: arrayListOf()
        senderFileNames = intent.getStringArrayListExtra(EXTRA_FILE_NAMES) ?: arrayListOf()
        senderFileSizes = intent.getLongArrayExtra(EXTRA_FILE_SIZES) ?: longArrayOf()

        initViews()
        setupEdgeToEdge()
        setupQueue()
        applyMode()
        bindTransferService()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvSavingTo = findViewById(R.id.tvSavingTo)
        tvPercentage = findViewById(R.id.tvPercentage)
        tvFileCount = findViewById(R.id.tvFileCount)
        tvCurrentFileName = findViewById(R.id.tvCurrentFileName)
        tvCurrentFileSize = findViewById(R.id.tvCurrentFileSize)
        tvStatSpeed = findViewById(R.id.tvStatSpeed)
        tvStatSpeedUnit = findViewById(R.id.tvStatSpeedUnit)
        tvStatTimeLeft = findViewById(R.id.tvStatTimeLeft)
        tvStatTotal = findViewById(R.id.tvStatTotal)
        tvStatTotalLabel = findViewById(R.id.tvStatTotalLabel)
        tvQueueLabel = findViewById(R.id.tvQueueLabel)
        progressRing = findViewById(R.id.progressRing)
        rvTransferQueue = findViewById(R.id.rvTransferQueue)
        btnAction = findViewById(R.id.btnAction)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { showCancelDialog() }
        findViewById<ImageView>(R.id.btnCancel).setOnClickListener { showCancelDialog() }
        btnAction.setOnClickListener { showCancelDialog() }
    }

    private fun setupEdgeToEdge() {
        val rootLayout = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, insets.top, 0, insets.bottom)
            windowInsets
        }
    }

    private fun setupQueue() {
        queueAdapter = TransferQueueAdapter(isSender)
        rvTransferQueue.layoutManager = LinearLayoutManager(this)
        rvTransferQueue.adapter = queueAdapter
    }

    private fun applyMode() {
        if (isSender) {
            tvTitle.text = getString(R.string.transfer_sending)
            tvSubtitle.text = getString(R.string.transfer_to_device, remoteDeviceName)
            tvSavingTo.visibility = View.GONE
            progressRing.progressDrawable = getDrawable(R.drawable.progress_ring_blue)
            tvStatTotalLabel.text = getString(R.string.transfer_total_sent)
            tvQueueLabel.text = getString(R.string.transfer_queue)
            btnAction.text = getString(R.string.transfer_cancel_action)
            btnAction.setBackgroundResource(R.drawable.bg_button_outlined)
            btnAction.setTextColor(getColor(R.color.color_red))
        } else {
            tvTitle.text = getString(R.string.transfer_receiving)
            tvSubtitle.text = getString(R.string.transfer_from_device, remoteDeviceName)
            tvSavingTo.visibility = View.VISIBLE
            progressRing.progressDrawable = getDrawable(R.drawable.progress_ring_green)
            tvStatTotalLabel.text = getString(R.string.transfer_total_received)
            tvQueueLabel.text = getString(R.string.transfer_incoming)
            btnAction.text = getString(R.string.stop_receiving)
            btnAction.setBackgroundResource(R.drawable.bg_button_danger)
            btnAction.setTextColor(getColor(R.color.color_text_on_primary))
        }
    }

    private fun bindTransferService() {
        val intent = Intent(this, FileTransferService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun observeTransferState(service: FileTransferService) {
        lifecycleScope.launch {
            service.transferState.collect { state ->
                when (state) {
                    is TransferState.WaitingForReceiver -> {
                        tvCurrentFileName.text = if (isSender) "Connecting…" else "Waiting for sender…"
                        tvPercentage.text = "0%"
                        tvFileCount.text = ""
                        tvStatSpeed.text = "0.0"
                        tvStatTimeLeft.text = "—"
                        tvStatTotal.text = "0 B"
                        progressRing.progress = 0

                        // Show full queue as PENDING on sender side
                        if (isSender && senderFileNames.isNotEmpty()) {
                            val pendingList = senderFileNames.mapIndexed { index, name ->
                                val size = if (index < senderFileSizes.size) senderFileSizes[index] else 0L
                                TransferFile(
                                    id = (index + 1).toString(),
                                    name = name,
                                    size = size,
                                    mimeType = "",
                                    status = TransferStatus.PENDING,
                                    progress = 0
                                )
                            }
                            queueAdapter.submitList(pendingList)
                        }
                    }

                    is TransferState.Transferring -> {
                        if (transferStartTimeMs == 0L) transferStartTimeMs = System.currentTimeMillis()
                        lastFileName = state.fileName
                        lastTotalBytes = state.totalBytes
                        lastSpeedBytesPerSec = state.speedBytesPerSec
                        lastFileCount = state.totalFiles
                        updateTransferUI(state)
                    }

                    is TransferState.Completed -> {
                        val historyStatus = when {
                            state.successCount > 0 -> HistoryStatus.DONE
                            else -> HistoryStatus.FAILED
                        }
                        saveHistoryPerFile(state, historyStatus)

                        val resultType = when {
                            state.failedCount > 0 && state.successCount > 0 -> TransferResultActivity.RESULT_PARTIAL
                            state.successCount > 0 -> TransferResultActivity.RESULT_SUCCESS
                            else -> TransferResultActivity.RESULT_ERROR
                        }
                        startActivity(Intent(this@TransferProgressActivity, TransferResultActivity::class.java).apply {
                            putExtra(TransferResultActivity.EXTRA_RESULT_TYPE, resultType)
                            putExtra(TransferResultActivity.EXTRA_FILE_COUNT, state.successCount)
                            putExtra(TransferResultActivity.EXTRA_SUCCESS_COUNT, state.successCount)
                            putExtra(TransferResultActivity.EXTRA_FAILED_COUNT, state.failedCount)
                            putExtra(TransferResultActivity.EXTRA_TOTAL_SIZE, formatSize(state.totalSize))
                            putExtra("is_sender", isSender)
                        })
                        finish()
                    }

                    is TransferState.Failed -> {
                        saveHistory(
                            successCount = 0,
                            failedCount = lastFileCount.coerceAtLeast(1),
                            totalSize = lastTotalBytes,
                            status = HistoryStatus.FAILED
                        )
                        startActivity(Intent(this@TransferProgressActivity, TransferResultActivity::class.java).apply {
                            putExtra(TransferResultActivity.EXTRA_RESULT_TYPE, TransferResultActivity.RESULT_ERROR)
                            putExtra(TransferResultActivity.EXTRA_ERROR_MESSAGE, state.message)
                            putExtra("is_sender", isSender)
                        })
                        finish()
                    }

                    else -> {}
                }
            }
        }
    }

    private fun saveHistoryPerFile(state: TransferState.Completed, status: HistoryStatus) {
        val repo = TransferHistoryRepository.getInstance(applicationContext)
        val dateStamp = dateStamp()
        val durationMs = if (transferStartTimeMs > 0) System.currentTimeMillis() - transferStartTimeMs else 0L
        val durationSec = durationMs / 1000
        val durationLabel = when {
            durationSec <= 0 -> null
            durationSec < 60 -> "0:${durationSec.toString().padStart(2, '0')}"
            else -> "${durationSec / 60}:${(durationSec % 60).toString().padStart(2, '0')}"
        }
        val speedLabel = if (lastSpeedBytesPerSec > 0) formatSpeed(lastSpeedBytesPerSec) else null

        if (isSender) {
            senderFileNames.forEachIndexed { index, name ->
                val size = if (index < senderFileSizes.size) senderFileSizes[index] else 0L
                val item = TransferHistoryItem(
                    id = UUID.randomUUID().toString(),
                    title = name.ifBlank { "File" },
                    direction = TransferDirection.SENT,
                    deviceName = remoteDeviceName,
                    totalSize = formatSize(size),
                    fileCount = 1,
                    status = if (index < state.successCount) HistoryStatus.DONE else HistoryStatus.FAILED,
                    dateTime = friendlyDate(dateStamp),
                    speed = speedLabel,
                    duration = durationLabel,
                    method = "Wi-Fi Direct",
                    fileType = guessFileType(name),
                    filePath = null
                )
                repo.addItem(item)
            }

            if (senderFileNames.isEmpty()) {
                saveHistory(state.successCount, state.failedCount, state.totalSize, status)
            }
        } else {
            if (state.savedFiles.isNotEmpty()) {
                state.savedFiles.forEach { (path, size) ->
                    val name = File(path).name
                    val item = TransferHistoryItem(
                        id = UUID.randomUUID().toString(),
                        title = name.ifBlank { "File" },
                        direction = TransferDirection.RECEIVED,
                        deviceName = remoteDeviceName,
                        totalSize = formatSize(size),
                        fileCount = 1,
                        status = HistoryStatus.DONE,
                        dateTime = friendlyDate(dateStamp),
                        speed = speedLabel,
                        duration = durationLabel,
                        method = "Wi-Fi Direct",
                        fileType = guessFileType(name),
                        filePath = path
                    )
                    repo.addItem(item)
                }

                if (state.failedCount > 0) {
                    saveHistory(0, state.failedCount, 0L, HistoryStatus.FAILED)
                }
            } else {
                saveHistory(state.successCount, state.failedCount, state.totalSize, status)
            }
        }
    }

    private fun saveHistory(
        successCount: Int,
        failedCount: Int,
        totalSize: Long,
        status: HistoryStatus
    ) {
        val durationMs = if (transferStartTimeMs > 0) System.currentTimeMillis() - transferStartTimeMs else 0L
        val durationSec = durationMs / 1000
        val durationLabel = when {
            durationSec <= 0 -> null
            durationSec < 60 -> "0:${durationSec.toString().padStart(2, '0')}"
            else -> "${durationSec / 60}:${(durationSec % 60).toString().padStart(2, '0')}"
        }

        val fileCount = (successCount + failedCount).coerceAtLeast(1)
        val title = if (fileCount > 1) "$fileCount files" else lastFileName.ifBlank { "Transfer" }
        val dateStamp = dateStamp()

        val item = TransferHistoryItem(
            id = UUID.randomUUID().toString(),
            title = title,
            direction = if (isSender) TransferDirection.SENT else TransferDirection.RECEIVED,
            deviceName = remoteDeviceName,
            totalSize = formatSize(totalSize),
            fileCount = fileCount,
            status = status,
            dateTime = friendlyDate(dateStamp),
            speed = if (lastSpeedBytesPerSec > 0) formatSpeed(lastSpeedBytesPerSec) else null,
            duration = durationLabel,
            method = "Wi-Fi Direct",
            fileType = guessFileType(lastFileName)
        )

        TransferHistoryRepository.getInstance(applicationContext).addItem(item)
    }

    private fun guessFileType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> "photo"
            "mp4", "mkv", "avi", "mov", "3gp", "webm" -> "video"
            "mp3", "aac", "flac", "wav", "ogg", "m4a" -> "music"
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv" -> "document"
            "apk", "xapk" -> "app"
            "vcf" -> "contact"
            else -> "mixed"
        }
    }

    private fun updateTransferUI(state: TransferState.Transferring) {
        // File name and size
        tvCurrentFileName.text = state.fileName
        tvCurrentFileSize.text = String.format(
            Locale.US, "%s of %s",
            formatSize(state.currentBytes), formatSize(state.totalBytes)
        )

        // Percentage and ring progress
        val percent = if (state.totalBytes > 0)
            ((state.currentBytes * 100) / state.totalBytes).toInt() else 0
        tvPercentage.text = String.format(Locale.US, "%d%%", percent)
        progressRing.progress = percent

        // File count
        tvFileCount.text = getString(R.string.transfer_file_count, state.fileIndex, state.totalFiles)

        // Speed — adaptive unit (MB/s or KB/s)
        val speedBytes = state.speedBytesPerSec.toDouble()
        if (speedBytes >= 1_048_576) {
            // MB/s range
            tvStatSpeed.text = String.format(Locale.US, "%.1f", speedBytes / 1_048_576.0)
            tvStatSpeedUnit.text = "MB/s"
        } else if (speedBytes >= 1024) {
            // KB/s range
            tvStatSpeed.text = String.format(Locale.US, "%.0f", speedBytes / 1024.0)
            tvStatSpeedUnit.text = "KB/s"
        } else {
            tvStatSpeed.text = "0.0"
            tvStatSpeedUnit.text = "MB/s"
        }

        // Time left
        if (state.speedBytesPerSec > 0) {
            val remainingBytes = state.totalBytes - state.currentBytes
            val remainingSec = remainingBytes.toDouble() / state.speedBytesPerSec.toDouble()
            tvStatTimeLeft.text = when {
                remainingSec < 1 -> "<1 s"
                remainingSec < 60 -> String.format(Locale.US, "~%.0f s", remainingSec)
                remainingSec < 3600 -> String.format(Locale.US, "~%.0f min", remainingSec / 60.0)
                else -> String.format(Locale.US, "~%.1f hr", remainingSec / 3600.0)
            }
        } else {
            tvStatTimeLeft.text = "—"
        }

        // Total sent/received
        cumulativeBytes = state.currentBytes
        tvStatTotal.text = formatSize(cumulativeBytes)

        // Queue list — build full file list for both sender and receiver
        if (isSender && senderFileNames.isNotEmpty()) {
            // Sender: we know the full list upfront
            val queueList = senderFileNames.mapIndexed { index, name ->
                val size = if (index < senderFileSizes.size) senderFileSizes[index] else 0L
                val fileIndex = index + 1 // 1-based
                val status = when {
                    fileIndex < state.fileIndex -> TransferStatus.DONE
                    fileIndex == state.fileIndex -> TransferStatus.TRANSFERRING
                    else -> TransferStatus.PENDING
                }
                val prog = if (status == TransferStatus.TRANSFERRING) percent else 0
                TransferFile(
                    id = fileIndex.toString(),
                    name = name,
                    size = size,
                    mimeType = "",
                    status = status,
                    progress = prog
                )
            }
            queueAdapter.submitList(queueList)
        } else {
            // Receiver: accumulate files as they arrive
            // Mark previous files as DONE when fileIndex advances
            while (lastReceiverFileIndex < state.fileIndex - 1 && lastReceiverFileIndex < receivedFiles.size) {
                receivedFiles[lastReceiverFileIndex] = receivedFiles[lastReceiverFileIndex].copy(
                    status = TransferStatus.DONE,
                    progress = 100
                )
                lastReceiverFileIndex++
            }

            // Add or update current file
            val currentId = state.fileIndex.toString()
            val currentFile = TransferFile(
                id = currentId,
                name = state.fileName,
                size = state.totalBytes,
                mimeType = "",
                status = TransferStatus.TRANSFERRING,
                progress = percent
            )

            val existingIndex = receivedFiles.indexOfFirst { it.id == currentId }
            if (existingIndex >= 0) {
                receivedFiles[existingIndex] = currentFile
            } else {
                receivedFiles.add(currentFile)
            }

            queueAdapter.submitList(receivedFiles.toList())
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String = when {
        bytesPerSec >= 1_048_576 -> String.format(Locale.US, "%.1f MB/s", bytesPerSec / 1_048_576.0)
        bytesPerSec >= 1024 -> String.format(Locale.US, "%.0f KB/s", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B/s"
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun showCancelDialog() {
        transferService?.cancelTransfer()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}