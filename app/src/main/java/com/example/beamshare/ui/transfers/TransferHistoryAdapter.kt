// com/example/beamshare/ui/transfers/TransferHistoryAdapter.kt
package com.example.beamshare.ui.transfers

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.model.HistoryStatus
import com.example.beamshare.model.TransferDirection
import com.example.beamshare.model.TransferHistoryItem
import java.io.File

class TransferHistoryAdapter(
    private val onItemLongClick: (TransferHistoryItem) -> Unit = {},
    private val onSelectionChanged: (count: Int) -> Unit = {},
    private val onDeleteItem: (TransferHistoryItem) -> Unit = {},
    private val onRefresh: () -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM   = 1
    }

    // ── Selection state ───────────────────────────────────────────────────────
    var isSelectionMode = false
        private set
    private val selectedIds = mutableSetOf<String>()

    fun enterSelectionMode(item: TransferHistoryItem) {
        isSelectionMode = true
        selectedIds.clear()
        selectedIds.add(item.id)
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0)
    }

    fun selectAll() {
        items.filterIsInstance<TransferHistoryItem>().forEach { selectedIds.add(it.id) }
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun getSelectedIds(): Set<String> = selectedIds.toSet()

    fun getSelectedItems(): List<TransferHistoryItem> =
        items.filterIsInstance<TransferHistoryItem>().filter { it.id in selectedIds }

    // ── List data ─────────────────────────────────────────────────────────────
    private val items = mutableListOf<Any>() // String for header, TransferHistoryItem for item

    fun submitGroupedList(grouped: Map<String, List<TransferHistoryItem>>) {
        items.clear()
        for ((date, transfers) in grouped) {
            items.add(date)
            items.addAll(transfers)
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int) =
        if (items[position] is String) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transfer_history_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_session_file, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(items[position] as String)
            is ItemViewHolder -> {
                val item = items[position] as TransferHistoryItem
                holder.bind(item, item.id in selectedIds)

                holder.itemView.setOnClickListener {
                    if (isSelectionMode) {
                        toggleSelection(item, holder.bindingAdapterPosition)
                    } else {
                        val isSent = item.direction == TransferDirection.SENT
                        if (!isSent && item.status == HistoryStatus.DONE) {
                            holder.showCustomMenu(it, item, false)
                        }
                    }
                }
                holder.itemView.setOnLongClickListener {
                    if (!isSelectionMode) {
                        enterSelectionMode(item)
                    }
                    true
                }
            }
        }
    }

    private fun toggleSelection(item: TransferHistoryItem, position: Int) {
        if (item.id in selectedIds) selectedIds.remove(item.id)
        else selectedIds.add(item.id)
        notifyItemChanged(position)
        onSelectionChanged(selectedIds.size)
    }

    override fun getItemCount() = items.size

    // ── ViewHolders ───────────────────────────────────────────────────────────

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate: TextView = view.findViewById(R.id.tvDateHeader)
        fun bind(date: String) { tvDate.text = date.uppercase() }
    }

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivFileIcon: ImageView        = view.findViewById(R.id.ivFileIcon)
        private val tvFileName: TextView         = view.findViewById(R.id.tvFileName)
        private val tvFileSize: TextView         = view.findViewById(R.id.tvFileSize)
        private val tvStatus: TextView           = view.findViewById(R.id.tvStatus)
        private val ivStatus: ImageView          = view.findViewById(R.id.ivStatus)
        private val btnCancel: ImageView         = view.findViewById(R.id.btnCancel)
        private val btnMenu: ImageView           = view.findViewById(R.id.btnMenu)
        private val layoutProgress: LinearLayout = view.findViewById(R.id.layoutProgress)
        private val progressBar: ProgressBar     = view.findViewById(R.id.progressBar)
        private val tvProgress: TextView         = view.findViewById(R.id.tvProgress)
        private val layoutMediaControls: LinearLayout = view.findViewById(R.id.layoutMediaControls)

        fun bind(item: TransferHistoryItem, isSelected: Boolean) {
            tvFileName.text = if (item.fileCount > 1) "${item.fileCount} files · ${item.title}" else item.title
            tvFileSize.text = item.totalSize
            ivFileIcon.setImageResource(fileTypeIcon(item.fileType, item.title))

            val isSent = item.direction == TransferDirection.SENT

            // Reset visibility
            ImageViewCompat.setImageTintList(ivStatus, null)
            btnCancel.visibility           = View.GONE
            ivStatus.visibility            = View.GONE
            btnMenu.visibility             = View.VISIBLE
            layoutProgress.visibility      = View.GONE
            layoutMediaControls.visibility = View.GONE
            tvStatus.visibility            = View.VISIBLE

            // Highlight selected row in selection mode
            itemView.isActivated = isSelected

            when (item.status) {
                HistoryStatus.DONE -> {
                    tvStatus.text = "Completed"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.bs_gray))
                    btnMenu.visibility = View.VISIBLE
                    btnMenu.setOnClickListener { showCustomMenu(it, item, isSent) }
                }
                HistoryStatus.FAILED -> {
                    tvStatus.text = "Failed"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.color_red))
                    btnMenu.visibility = View.VISIBLE
                    btnMenu.setOnClickListener { showCustomMenu(it, item, true) }
                }
                HistoryStatus.CANCELLED -> {
                    ivStatus.visibility = View.VISIBLE
                    ivStatus.setImageResource(R.drawable.ic_close)
                    ImageViewCompat.setImageTintList(ivStatus, ColorStateList.valueOf(ContextCompat.getColor(itemView.context, R.color.bs_gold)))
                    tvStatus.text = "Cancelled"
                    tvStatus.setTextColor(ContextCompat.getColor(itemView.context, R.color.bs_gold))
                    btnMenu.visibility = View.VISIBLE
                    btnMenu.setOnClickListener { showCustomMenu(it, item, true) }
                }
            }
        }

        fun showCustomMenu(anchor: View, item: TransferHistoryItem, isSent: Boolean) {
            val context = anchor.context
            val layoutRes = if (isSent) R.layout.popup_session_sender_menu
            else        R.layout.popup_session_file_menu

            val popupView = LayoutInflater.from(context).inflate(layoutRes, null)

            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupWidth  = popupView.measuredWidth
            val popupHeight = popupView.measuredHeight

            val popup = PopupWindow(
                popupView,
                popupWidth,
                popupHeight,
                true
            ).apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                elevation = 16f
            }

            if (!isSent) {
                popupView.findViewById<LinearLayout>(R.id.menuPreview)?.setOnClickListener {
                    popup.dismiss()
                    openFile(anchor, item)
                }
                popupView.findViewById<LinearLayout>(R.id.menuRename)?.setOnClickListener {
                    popup.dismiss()
                    showRenameDialog(anchor, item)
                }
                popupView.findViewById<LinearLayout>(R.id.menuShare)?.setOnClickListener {
                    popup.dismiss()
                    shareFile(anchor, item)
                }
            }

            popupView.findViewById<LinearLayout>(R.id.menuDelete)?.setOnClickListener {
                popup.dismiss()
                onDeleteItem(item)
            }

            val xOffset = -popupWidth
            val yOffset = 0
            popup.showAsDropDown(anchor, xOffset, yOffset, Gravity.START)
        }

        private fun showRenameDialog(anchor: View, item: TransferHistoryItem) {
            val context = anchor.context
            val path = item.filePath
            if (path.isNullOrBlank()) {
                Toast.makeText(context, "File path not available", Toast.LENGTH_SHORT).show()
                return
            }
            val currentFile = File(path)
            if (!currentFile.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                return
            }

            val nameWithoutExt = currentFile.nameWithoutExtension
            val extension = currentFile.extension

            val editText = EditText(context).apply {
                setText(nameWithoutExt)
                selectAll()
                setPadding(48, 32, 48, 16)
            }

            AlertDialog.Builder(context)
                .setTitle("Rename File")
                .setView(editText)
                .setPositiveButton("Save") { _, _ ->
                    val newName = editText.text.toString().trim()
                    if (newName.isEmpty()) return@setPositiveButton
                    val newFileName = if (extension.isNotEmpty()) "$newName.$extension" else newName
                    val newFile = File(currentFile.parent, newFileName)
                    if (currentFile.renameTo(newFile)) {
                        item.filePath = newFile.absolutePath
                        item.title = newFileName
                        Toast.makeText(context, "Renamed to $newFileName", Toast.LENGTH_SHORT).show()
                        onRefresh()
                    } else {
                        Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun openFile(anchor: View, item: TransferHistoryItem) {
            val context = anchor.context
            val path = item.filePath
            if (path.isNullOrBlank()) {
                Toast.makeText(context, item.title, Toast.LENGTH_SHORT).show()
                return
            }
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(context, "File not found: ${item.title}", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val uri: Uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.beamshare.fileprovider", file)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, getMimeType(file.name))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Open with"))
            } catch (_: Exception) {
                Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
            }
        }

        private fun shareFile(anchor: View, item: TransferHistoryItem) {
            val context = anchor.context
            val path = item.filePath
            if (path.isNullOrBlank()) {
                Toast.makeText(context, "File not available to share", Toast.LENGTH_SHORT).show()
                return
            }
            val file = File(path)
            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val uri: Uri = FileProvider.getUriForFile(
                    context, "${context.packageName}.beamshare.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = getMimeType(file.name)
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Share"))
            } catch (_: Exception) {
                Toast.makeText(context, "Cannot share file", Toast.LENGTH_SHORT).show()
            }
        }

        private fun fileTypeInfo(type: String, name: String): Int {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp4", "mkv", "avi", "mov", "webm", "3gp"          -> R.drawable.ic_file_video
                "mp3", "aac", "wav", "flac", "ogg", "m4a"          -> R.drawable.ic_file_audio
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic" -> R.drawable.ic_file_image
                "pdf"                                                -> R.drawable.ic_file_pdf
                "doc", "docx", "xls", "xlsx", "ppt", "pptx"        -> R.drawable.ic_file_doc
                "zip", "rar", "7z", "tar", "gz"                     -> R.drawable.ic_file_zip
                "apk"                                                -> R.drawable.ic_file_apk
                else -> when (type) {
                    "photo"    -> R.drawable.ic_file_image
                    "video"    -> R.drawable.ic_file_video
                    "music"    -> R.drawable.ic_file_audio
                    "document" -> R.drawable.ic_file_doc
                    "app"      -> R.drawable.ic_file_apk
                    "contact"  -> R.drawable.ic_file_doc
                    else       -> R.drawable.ic_file_doc
                }
            }
        }

        private fun fileTypeIcon(type: String, name: String): Int = fileTypeInfo(type, name)

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
                "xls"  -> "application/msword"
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
    }
}
