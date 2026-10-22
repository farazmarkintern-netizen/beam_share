// com/example/beamshare/ui/transfers/TransfersFragment.kt
package com.example.beamshare.ui.transfers

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
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
import com.example.beamshare.ui.dialog.ReceivedFileOptionsDialog
import java.io.File

class TransfersFragment : Fragment() {

    private lateinit var rvHistory: RecyclerView
    private lateinit var emptyState: View
    private lateinit var filterAll: TextView
    private lateinit var filterSent: TextView
    private lateinit var filterReceived: TextView
    private lateinit var filterFailed: TextView
    private lateinit var etSearch: EditText
    private lateinit var searchContainer: View

    // Selection-mode toolbar views
    private lateinit var selectionBar: View
    private lateinit var tvSelectionCount: TextView
    private lateinit var btnSelectAll: View
    private lateinit var btnDeleteSelected: View
    private lateinit var btnCancelSelection: View

    private lateinit var adapter: TransferHistoryAdapter
    private lateinit var repo: TransferHistoryRepository   // ← fix: field-level repo

    private var allItems = listOf<TransferHistoryItem>()

    private var activeDirectionFilter: TransferDirection? = null
    private var activeStatusFilter: HistoryStatus? = null
    private var activeSearchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_transfers, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = TransferHistoryRepository.getInstance(requireContext())   // ← init here

        rvHistory          = view.findViewById(R.id.rvTransferHistory)
        emptyState         = view.findViewById(R.id.emptyState)
        filterAll          = view.findViewById(R.id.filterAll)
        filterSent         = view.findViewById(R.id.filterSent)
        filterReceived     = view.findViewById(R.id.filterReceived)
        filterFailed       = view.findViewById(R.id.filterFailed)
        etSearch           = view.findViewById(R.id.etSearch)
        searchContainer    = view.findViewById(R.id.searchContainer)
        selectionBar       = view.findViewById(R.id.selectionBar)
        tvSelectionCount   = view.findViewById(R.id.tvSelectionCount)
        btnSelectAll       = view.findViewById(R.id.btnSelectAll)
        btnDeleteSelected  = view.findViewById(R.id.btnDeleteSelected)
        btnCancelSelection = view.findViewById(R.id.btnCancelSelection)

        adapter = TransferHistoryAdapter(
            onItemClick        = { item -> showFileOptions(item) },
            onItemLongClick    = { item -> adapter.enterSelectionMode(item) },
            onSelectionChanged = { count -> onSelectionChanged(count) }
        )

        rvHistory.layoutManager = LinearLayoutManager(requireContext())
        rvHistory.adapter = adapter

        setupFilters()
        setupSearch()

        // Search toggle
        view.findViewById<View>(R.id.btnSearch).setOnClickListener {
            if (searchContainer.isVisible) {
                searchContainer.isGone = true
                etSearch.setText("")
                activeSearchQuery = ""
                applyCurrentFilter()
            } else {
                searchContainer.isVisible = true
                etSearch.requestFocus()
            }
        }

        // Empty state button
        view.findViewById<View>(R.id.btnSendFiles).setOnClickListener {
            startActivity(Intent(requireContext(),
                com.example.beamshare.ui.send.SendFilesActivity::class.java))
        }

        // Selection bar actions
        btnSelectAll.setOnClickListener { adapter.selectAll() }
        btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }
        btnCancelSelection.setOnClickListener {
            adapter.exitSelectionMode()
            showNormalBar()
        }
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
        applyCurrentFilter()
    }

    // ── Selection mode ────────────────────────────────────────────────────────

    private fun onSelectionChanged(count: Int) {
        if (count == 0 && !adapter.isSelectionMode) {
            showNormalBar()
            return
        }
        if (adapter.isSelectionMode) {
            showSelectionBar(count)
        }
    }

    private fun showSelectionBar(count: Int) {
        selectionBar.isVisible = true
        tvSelectionCount.text = "$count selected"
        btnDeleteSelected.isEnabled = count > 0
    }

    private fun showNormalBar() {
        selectionBar.isGone = true
    }

    private fun confirmDeleteSelected() {
        val selected = adapter.getSelectedItems()
        if (selected.isEmpty()) return

        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${selected.size} item${if (selected.size > 1) "s" else ""}?")
            .setMessage("This removes them from history. Local files will not be deleted.")
            .setPositiveButton("Delete") { _, _ -> deleteSelected(selected) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSelected(items: List<TransferHistoryItem>) {
        val ids = items.map { it.id }.toSet()
        repo.deleteByIds(ids)

        adapter.exitSelectionMode()
        showNormalBar()
        loadHistory()
        applyCurrentFilter()

        Toast.makeText(requireContext(),
            "${ids.size} item${if (ids.size > 1) "s" else ""} removed",
            Toast.LENGTH_SHORT).show()
    }

    fun onBackPressed(): Boolean {
        return if (adapter.isSelectionMode) {
            adapter.exitSelectionMode()
            showNormalBar()
            true
        } else false
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                activeSearchQuery = s?.toString()?.trim() ?: ""
                applyCurrentFilter()
            }
        })
    }

    // ── Filters ───────────────────────────────────────────────────────────────

    private fun setupFilters() {
        filterAll.setOnClickListener {
            activeDirectionFilter = null; activeStatusFilter = null
            applyFilterStyle(filterAll); applyCurrentFilter()
        }
        filterSent.setOnClickListener {
            activeDirectionFilter = TransferDirection.SENT; activeStatusFilter = null
            applyFilterStyle(filterSent); applyCurrentFilter()
        }
        filterReceived.setOnClickListener {
            activeDirectionFilter = TransferDirection.RECEIVED; activeStatusFilter = null
            applyFilterStyle(filterReceived); applyCurrentFilter()
        }
        filterFailed.setOnClickListener {
            activeDirectionFilter = null; activeStatusFilter = HistoryStatus.FAILED
            applyFilterStyle(filterFailed); applyCurrentFilter()
        }
    }

    private fun applyFilterStyle(selected: TextView) {
        listOf(filterAll, filterSent, filterReceived, filterFailed).forEach { t ->
            if (t == selected) {
                t.setBackgroundResource(R.drawable.bg_filter_pill_selected)
                t.setTextColor(android.graphics.Color.WHITE)
            } else {
                t.setBackgroundResource(R.drawable.bg_filter_pill_unselected)
                t.setTextColor(android.graphics.Color.parseColor("#64748B"))
            }
        }
    }

    private fun applyCurrentFilter() {
        var filtered = allItems.filter { item ->
            when {
                activeStatusFilter    != null -> item.status    == activeStatusFilter
                activeDirectionFilter != null -> item.direction == activeDirectionFilter
                else -> true
            }
        }
        if (activeSearchQuery.isNotEmpty()) {
            val q = activeSearchQuery.lowercase()
            filtered = filtered.filter { item ->
                item.title.lowercase().contains(q) ||
                        item.deviceName.lowercase().contains(q) ||
                        item.fileType.lowercase().contains(q)
            }
        }
        if (filtered.isEmpty()) {
            rvHistory.isGone     = true
            emptyState.isVisible = true
        } else {
            rvHistory.isVisible = true
            emptyState.isGone   = true
            val grouped = filtered
                .groupBy { it.dateTime }
                .toSortedMap(compareByDescending { groupSortKey(it) })
            adapter.submitGroupedList(grouped)
        }
    }

    private fun groupSortKey(label: String): String {
        return when (label) {
            "Today"     -> "9999-12-31"
            "Yesterday" -> "9999-12-30"
            else -> try {
                val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                val out = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                out.format(sdf.parse(label) ?: return label)
            } catch (_: Exception) { label }
        }
    }

    // ── File actions ──────────────────────────────────────────────────────────

    private fun showFileOptions(item: TransferHistoryItem) {
        val canOpenFile = item.direction == TransferDirection.RECEIVED
                && item.status == HistoryStatus.DONE
                && !item.filePath.isNullOrBlank()

        if (!canOpenFile) {
            // Sent / Cancelled / no path → sirf history delete
            confirmDeleteSingle(item)
            return
        }

        ReceivedFileOptionsDialog(
            context             = requireContext(),
            item                = item,
            onOpenFile          = { openFile(item) },
            onShowInFolder      = { showInFolder(item) },
            onDeleteFromHistory = { confirmDeleteSingle(item) }
        ).show()
    }

    private fun confirmDeleteSingle(item: TransferHistoryItem) {
        val isReceived = item.direction == TransferDirection.RECEIVED
        val hasFile    = item.filePath?.let { File(it).exists() } == true

        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Delete")
            .setNegativeButton("Cancel", null)

        if (isReceived && hasFile) {
            builder.setMessage("Remove from history only, or also delete the file from your device?")
            builder.setPositiveButton("History only") { _, _ ->
                repo.deleteByIds(setOf(item.id))
                loadHistory()
                applyCurrentFilter()
            }
            builder.setNeutralButton("File + History") { _, _ ->
                deleteFileFromDevice(item)
                repo.deleteByIds(setOf(item.id))
                loadHistory()
                applyCurrentFilter()
                Toast.makeText(requireContext(), "File deleted", Toast.LENGTH_SHORT).show()
            }
        } else {
            builder.setMessage("Remove this transfer from history?")
            builder.setPositiveButton("Delete") { _, _ ->
                repo.deleteByIds(setOf(item.id))
                loadHistory()
                applyCurrentFilter()
            }
        }

        builder.show()
    }

    private fun deleteFileFromDevice(item: TransferHistoryItem): Boolean {
        val path = item.filePath ?: return false
        val file = File(path)
        if (!file.exists()) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = MediaStore.Files.getContentUri("external")
            val deleted = requireContext().contentResolver.delete(
                uri,
                "${MediaStore.MediaColumns.DATA} = ?",
                arrayOf(path)
            )
            if (deleted == 0) file.delete() else true
        } else {
            file.delete()
        }
    }

    private fun openFile(item: TransferHistoryItem) {
        val path = item.filePath
        if (path.isNullOrBlank()) {
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
                requireContext(), "${requireContext().packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, getMimeType(file.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Cannot open file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showInFolder(item: TransferHistoryItem) {
        val path = item.filePath
        if (path.isNullOrBlank()) {
          // "No local file for sent items" ki jagah generic message
            Toast.makeText(requireContext(), "File path not available", Toast.LENGTH_SHORT).show()
            return
        }
        val file = File(path)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val folderUri: Uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider",
                file.parentFile ?: file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(folderUri, "resource/folder")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent); return
            }
        } catch (_: Exception) {}
        // Fallback: open the file itself and show folder name
        openFile(item)
        Toast.makeText(requireContext(),
            "Saved in: ${file.parentFile?.name ?: "BeamShare"}", Toast.LENGTH_LONG).show()
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png"  -> "image/png"
            "gif"  -> "image/gif"
            "webp" -> "image/webp"
            "heic" -> "image/heic"
            "bmp"  -> "image/bmp"
            "mp4"  -> "video/mp4"
            "mkv"  -> "video/x-matroska"
            "avi"  -> "video/x-msvideo"
            "mov"  -> "video/quicktime"
            "3gp"  -> "video/3gpp"
            "webm" -> "video/webm"
            "mp3"  -> "audio/mpeg"
            "aac"  -> "audio/aac"
            "flac" -> "audio/flac"
            "wav"  -> "audio/wav"
            "ogg"  -> "audio/ogg"
            "m4a"  -> "audio/mp4"
            "pdf"  -> "application/pdf"
            "doc"  -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls"  -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt"  -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt"  -> "text/plain"
            "csv"  -> "text/csv"
            "apk"  -> "application/vnd.android.package-archive"
            "vcf"  -> "text/vcard"
            else   -> "*/*"
        }
    }

    private fun loadHistory() {
        allItems = repo.getAllItems()
    }
}