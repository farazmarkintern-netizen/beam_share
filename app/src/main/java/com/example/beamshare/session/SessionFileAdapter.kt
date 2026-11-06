package com.example.beamshare.session

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import java.io.File

class SessionFileAdapter(
    private val onCancel: (LiveTransfer) -> Unit,
    private val onDelete: (LiveTransfer) -> Unit,
    private val onPause: (LiveTransfer) -> Unit = {},
    private val onResume: (LiveTransfer) -> Unit = {}
) : RecyclerView.Adapter<SessionFileAdapter.ViewHolder>() {

    private val files = mutableListOf<LiveTransfer>()

    fun submitList(newFiles: List<LiveTransfer>) {
        files.clear()
        val sorted = newFiles.sortedWith(
            compareByDescending<LiveTransfer> { it.status == TransferStatus.ACTIVE }
                .thenByDescending { it.id }
        )
        files.addAll(sorted)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_session_file, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(files[position])
    }

    override fun getItemCount() = files.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val ivFileIcon: ImageView        = view.findViewById(R.id.ivFileIcon)
        private val tvFileName: TextView         = view.findViewById(R.id.tvFileName)
        private val tvStatus: TextView           = view.findViewById(R.id.tvStatus)
        private val ivStatus: ImageView          = view.findViewById(R.id.ivStatus)
        private val tvFileSize: TextView         = view.findViewById(R.id.tvFileSize)
        private val btnCancel: ImageView         = view.findViewById(R.id.btnCancel)
        private val btnMenu: ImageView           = view.findViewById(R.id.btnMenu)
        private val layoutProgress: LinearLayout = view.findViewById(R.id.layoutProgress)
        private val progressBar: ProgressBar     = view.findViewById(R.id.progressBar)
        private val tvProgress: TextView         = view.findViewById(R.id.tvProgress)
        private val layoutMediaControls: LinearLayout = view.findViewById(R.id.layoutMediaControls)
        private val btnPlay: ImageView           = view.findViewById(R.id.btnPlay)
        private val btnPause: ImageView          = view.findViewById(R.id.btnPause)
        private val btnResume: ImageView         = view.findViewById(R.id.btnResume)
        private val btnDelete: ImageView         = view.findViewById(R.id.btnDelete)

        fun bind(transfer: LiveTransfer) {
            tvFileName.text = transfer.name
            tvFileSize.text = formatSize(transfer.size)
            ivFileIcon.setImageResource(fileTypeIcon(transfer.name))

            val isSent = transfer.direction == TransferDirection.OUT

            // Reset all
            ImageViewCompat.setImageTintList(ivStatus, null)
            btnCancel.visibility           = View.GONE
            ivStatus.visibility            = View.GONE
            btnMenu.visibility             = View.GONE
            layoutProgress.visibility      = View.GONE
            layoutMediaControls.visibility = View.GONE
            btnPlay.visibility             = View.GONE
            btnPause.visibility            = View.GONE
            btnResume.visibility           = View.GONE
            btnDelete.visibility           = View.GONE
            tvStatus.visibility            = View.GONE

            when (transfer.status) {

                TransferStatus.ACTIVE -> {
                    layoutProgress.visibility = View.VISIBLE
                    progressBar.progress      = transfer.progress
                    tvProgress.text           = "${transfer.progress}%"

                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = "Processing"
                    tvStatus.setTextColor(0xFF2563EB.toInt())

                    btnCancel.visibility = View.VISIBLE
                    btnCancel.setOnClickListener { onCancel(transfer) }

                    itemView.setOnClickListener {
                        if (transfer.isPaused) onResume(transfer) else onPause(transfer)
                    }
                }

                TransferStatus.COMPLETED -> {
                    ivStatus.visibility = View.VISIBLE
                    ivStatus.setImageResource(R.drawable.ic_check_circle_filled)

                    tvStatus.visibility = View.VISIBLE
                    if (isSent) {
                        tvStatus.text = "Sent"
                        tvStatus.setTextColor(0xFF2563EB.toInt())
                    } else {
                        tvStatus.text = "Received"
                        tvStatus.setTextColor(0xFF16A34A.toInt())
                    }

                    btnMenu.visibility = View.VISIBLE
                    btnMenu.setOnClickListener { showCustomMenu(it, transfer, isSent) }

                    itemView.setOnClickListener {
                        if (!isSent) {
                            showCustomMenu(it, transfer, isSent)
                        } else {
                            transfer.savedPath?.let { path -> openFile(itemView, path) }
                        }
                    }
                }

                TransferStatus.FAILED -> {
                    ivStatus.visibility = View.VISIBLE
                    ivStatus.setImageResource(R.drawable.ic_error_circle)
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = "Failed"
                    tvStatus.setTextColor(0xFFDC2626.toInt())
                    btnMenu.visibility = View.VISIBLE
                    btnMenu.setOnClickListener { showCustomMenu(it, transfer, true) }
                    itemView.setOnClickListener(null)
                }

                TransferStatus.CANCELLED -> {
                    ivStatus.visibility = View.VISIBLE
                    ivStatus.setImageResource(R.drawable.ic_close)
                    ImageViewCompat.setImageTintList(ivStatus, ColorStateList.valueOf(0xFFD97706.toInt()))
                    tvStatus.visibility = View.VISIBLE
                    tvStatus.text = "Cancelled"
                    tvStatus.setTextColor(0xFFD97706.toInt())
                    btnMenu.visibility = View.VISIBLE
                    btnMenu.setOnClickListener { showCustomMenu(it, transfer, true) }
                    itemView.setOnClickListener(null)
                }
            }
        }

        // ── Custom popup menu ──────────────────────────────────────────────────

        private fun showCustomMenu(anchor: View, transfer: LiveTransfer, isSent: Boolean) {
            val context = anchor.context
            val layoutRes = if (isSent) R.layout.popup_session_sender_menu
            else        R.layout.popup_session_file_menu

            val popupView = LayoutInflater.from(context).inflate(layoutRes, null)

            // Measure first to get real dimensions
            popupView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val popupWidth  = popupView.measuredWidth
            val popupHeight = popupView.measuredHeight

            val popup = PopupWindow(
                popupView,
                popupWidth,   // explicit width — prevents full-screen stretch
                popupHeight,  // explicit height
                true
            ).apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                elevation = 16f
            }

            // Receiver menu items
            if (!isSent) {
                popupView.findViewById<LinearLayout>(R.id.menuPreview)?.setOnClickListener {
                    popup.dismiss()
                    transfer.savedPath?.let { openFile(anchor, it) }
                }
                popupView.findViewById<LinearLayout>(R.id.menuRename)?.setOnClickListener {
                    popup.dismiss()
                    showRenameDialog(anchor, transfer)
                }
                popupView.findViewById<LinearLayout>(R.id.menuShare)?.setOnClickListener {
                    popup.dismiss()
                    transfer.savedPath?.let { shareFile(anchor, it) }
                }
            }

            // Delete — both sender and receiver
            popupView.findViewById<LinearLayout>(R.id.menuDelete)?.setOnClickListener {
                popup.dismiss()
                showDeleteConfirmDialog(anchor, transfer)
            }

            // Position: popup top-right corner = 3-dot bottom-right corner
            val xOffset = -popupWidth   // right edge of popup = right edge of anchor
            val yOffset = 0             // top of popup = bottom of anchor

            popup.showAsDropDown(anchor, xOffset, yOffset, Gravity.START)
        }

        // ── Rename dialog ──────────────────────────────────────────────────────

        private fun showRenameDialog(anchor: View, transfer: LiveTransfer) {
            val context = anchor.context
            val currentFile = File(transfer.savedPath ?: return)
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
                        SessionManager.updateSavedPath(transfer.id, newFile.absolutePath)
                        Toast.makeText(context, "Renamed to $newFileName", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Delete confirm dialog ──────────────────────────────────────────────

        private fun showDeleteConfirmDialog(anchor: View, transfer: LiveTransfer) {
            val context = anchor.context
            AlertDialog.Builder(context)
                .setTitle("Delete File")
                .setMessage("\"${transfer.name}\" delete ho jayega. Sure ho?")
                .setPositiveButton("Delete") { _, _ ->
                    onDelete(transfer)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── File actions ───────────────────────────────────────────────────────

        private fun openFile(anchor: View, path: String) {
            runCatching {
                val uri = Uri.parse(path)
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, anchor.context.contentResolver.getType(uri) ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                anchor.context.startActivity(intent)
            }
        }

        private fun shareFile(anchor: View, path: String) {
            runCatching {
                val uri = Uri.parse(path)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = anchor.context.contentResolver.getType(uri) ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                anchor.context.startActivity(Intent.createChooser(intent, "Share"))
            }
        }

        // ── Helpers ────────────────────────────────────────────────────────────

        private fun formatSize(bytes: Long): String {
            return when {
                bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
                bytes >= 1_048_576     -> "%.1f MB".format(bytes / 1_048_576.0)
                bytes >= 1_024         -> "%.0f KB".format(bytes / 1_024.0)
                else                   -> "$bytes B"
            }
        }

        private fun fileTypeIcon(name: String): Int {
            val ext = name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "mp4", "mkv", "avi", "mov", "webm", "3gp"          -> R.drawable.ic_file_video
                "mp3", "aac", "wav", "flac", "ogg", "m4a"          -> R.drawable.ic_file_audio
                "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic" -> R.drawable.ic_file_image
                "pdf"                                                -> R.drawable.ic_file_pdf
                "doc", "docx"                                        -> R.drawable.ic_file_doc
                "xls", "xlsx"                                        -> R.drawable.ic_file_doc
                "ppt", "pptx"                                        -> R.drawable.ic_file_doc
                "zip", "rar", "7z", "tar", "gz"                     -> R.drawable.ic_file_zip
                "apk"                                                -> R.drawable.ic_file_apk
                else                                                 -> R.drawable.ic_file_doc
            }
        }
    }
}