// com/example/beamshare/ui/transfers/TransfersFragment.kt
package com.example.beamshare.ui.transfers

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.data.TransferHistoryRepository
import com.example.beamshare.model.HistoryStatus
import com.example.beamshare.model.TransferDirection
import com.example.beamshare.model.TransferHistoryItem
import java.io.File

class TransfersFragment : Fragment() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var emptyState: View
    private lateinit var filterAll: TextView
    private lateinit var filterSent: TextView
    private lateinit var filterReceived: TextView
    private lateinit var filterFailed: TextView

    private lateinit var adapter: TransferHistoryAdapter
    private var allItems = listOf<TransferHistoryItem>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_transfers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvHistory = view.findViewById(R.id.rvTransferHistory)
        emptyState = view.findViewById(R.id.emptyState)
        filterAll = view.findViewById(R.id.filterAll)
        filterSent = view.findViewById(R.id.filterSent)
        filterReceived = view.findViewById(R.id.filterReceived)
        filterFailed = view.findViewById(R.id.filterFailed)

        adapter = TransferHistoryAdapter { item ->
            openFile(item)
        }

        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter

        setupFilters()

        // Empty state "Send files" button
        view.findViewById<View>(R.id.btnSendFiles).setOnClickListener {
            startActivity(Intent(requireContext(),
                com.example.beamshare.ui.send.SendFilesActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
        applyFilter(filterAll, null)
    }

    private fun openFile(item: TransferHistoryItem) {
        val path = item.filePath
        if (path.isNullOrBlank()) {
            // Sent file — no local path stored
            Toast.makeText(requireContext(), item.title, Toast.LENGTH_SHORT).show()
            return
        }

        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File not found: ${item.title}", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val mime = getMimeType(file.name)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "bmp" -> "image/bmp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "webm" -> "video/webm"
            "mp3" -> "audio/mpeg"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "apk" -> "application/vnd.android.package-archive"
            "vcf" -> "text/vcard"
            else -> "*/*"
        }
    }

    private fun setupFilters() {
        filterAll.setOnClickListener { applyFilter(filterAll, null) }
        filterSent.setOnClickListener { applyFilter(filterSent, TransferDirection.SENT) }
        filterReceived.setOnClickListener { applyFilter(filterReceived, TransferDirection.RECEIVED) }
        filterFailed.setOnClickListener {
            applyFilter(filterFailed, null, statusFilter = HistoryStatus.FAILED)
        }
    }

    private fun applyFilter(
        tab: TextView,
        directionFilter: TransferDirection?,
        statusFilter: HistoryStatus? = null
    ) {
        // Update tab styles
        val tabs = listOf(filterAll, filterSent, filterReceived, filterFailed)
        tabs.forEach { t ->
            if (t == tab) {
                t.setBackgroundResource(R.drawable.bg_filter_pill_selected)
                t.setTextColor("#FFFFFF".toColorInt())
            } else {
                t.setBackgroundResource(R.drawable.bg_filter_pill_unselected)
                t.setTextColor("#64748B".toColorInt())
            }
        }

        // Filter items
        val filtered = allItems.filter { item ->
            when {
                statusFilter != null -> item.status == statusFilter
                directionFilter != null -> item.direction == directionFilter
                else -> true
            }
        }

        if (filtered.isEmpty()) {
            rvHistory.isGone = true
            emptyState.isVisible = true
        } else {
            rvHistory.isVisible = true
            emptyState.isGone = true
            // Group by dateTime label (Today / Yesterday / Oct 10, 2026 …) preserving order
            val grouped = filtered
                .groupBy { it.dateTime }
                .toSortedMap(compareByDescending { groupSortKey(it) })
            adapter.submitGroupedList(grouped)
        }
    }

    /**
     * Converts a dateTime label into a sort key so "Today" > "Yesterday" > past dates.
     * Past dates are expected in "MMM d, yyyy" format (e.g. "Oct 10, 2026").
     */
    private fun groupSortKey(label: String): String {
        return when (label) {
            "Today" -> "9999-12-31"
            "Yesterday" -> "9999-12-30"
            else -> {
                // Try to parse "MMM d, yyyy" → "yyyy-MM-dd" for correct lexicographic sort
                try {
                    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                    val out = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                    out.format(sdf.parse(label) ?: return label)
                } catch (_: Exception) { label }
            }
        }
    }

    private fun loadHistory() {
        allItems = TransferHistoryRepository.getInstance(requireContext()).getAllItems()
    }
}