package com.example.beamshare.ui.transfers

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.model.TransferFile
import com.example.beamshare.model.TransferStatus
import java.util.Locale

class TransferQueueAdapter(
    private val isSender: Boolean = true
) : ListAdapter<TransferFile, TransferQueueAdapter.ViewHolder>(TransferFileDiffCallback()) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val root: LinearLayout = view.findViewById(R.id.queueItemRoot)
        val viewIconBg: View = view.findViewById(R.id.viewIconBg)
        val ivFileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        val tvFileName: TextView = view.findViewById(R.id.tvQueueFileName)
        val tvFileInfo: TextView = view.findViewById(R.id.tvQueueFileInfo)
        val ivStatusIcon: ImageView = view.findViewById(R.id.ivStatusIcon)

        fun bind(file: TransferFile) {
            tvFileName.text = file.name

            when (file.status) {
                TransferStatus.DONE -> {
                    root.setBackgroundResource(0)
                    tvFileInfo.text = "${formatFileSize(file.size)} · ${
                        if (isSender) root.context.getString(R.string.transfer_item_done)
                        else root.context.getString(R.string.transfer_item_saved)
                    }"
                    tvFileInfo.setTextColor(root.context.getColor(R.color.color_text_secondary))
                    ivStatusIcon.visibility = View.VISIBLE
                    ivStatusIcon.setImageResource(R.drawable.ic_check_circle_filled)
                    viewIconBg.setBackgroundResource(R.drawable.bg_icon_circle_blue)
                    tvFileName.alpha = 0.7f
                    tvFileInfo.alpha = 0.7f
                }
                TransferStatus.TRANSFERRING -> {
                    if (isSender) {
                        root.setBackgroundResource(R.drawable.bg_queue_item_active_blue)
                    } else {
                        root.setBackgroundResource(R.drawable.bg_queue_item_active_green)
                    }
                    val action = if (isSender)
                        root.context.getString(R.string.transfer_sending)
                    else
                        root.context.getString(R.string.transfer_receiving)
                    tvFileInfo.text = "$action · ${file.progress}%"
                    if (isSender) {
                        tvFileInfo.setTextColor(root.context.getColor(R.color.color_primary))
                    } else {
                        tvFileInfo.setTextColor(root.context.getColor(R.color.color_green))
                    }
                    ivStatusIcon.visibility = View.GONE
                    viewIconBg.setBackgroundResource(
                        if (isSender) R.drawable.bg_icon_circle_blue
                        else R.drawable.bg_icon_circle_green
                    )
                    tvFileName.alpha = 1f
                    tvFileInfo.alpha = 1f
                }
                TransferStatus.FAILED -> {
                    root.setBackgroundResource(0)
                    tvFileInfo.text = "${formatFileSize(file.size)} · Failed"
                    tvFileInfo.setTextColor(root.context.getColor(R.color.color_red))
                    ivStatusIcon.visibility = View.VISIBLE
                    ivStatusIcon.setImageResource(R.drawable.ic_error_circle)
                    viewIconBg.setBackgroundResource(R.drawable.bg_icon_circle_red)
                    tvFileName.alpha = 1f
                    tvFileInfo.alpha = 1f
                }
                TransferStatus.PENDING -> {
                    root.setBackgroundResource(0)
                    tvFileInfo.text = formatFileSize(file.size)
                    tvFileInfo.setTextColor(root.context.getColor(R.color.color_text_tertiary))
                    ivStatusIcon.visibility = View.GONE
                    viewIconBg.setBackgroundResource(R.drawable.bg_icon_circle_blue)
                    tvFileName.alpha = 0.5f
                    tvFileInfo.alpha = 0.5f
                }
            }

            // Set file type icon
            val iconRes = getFileTypeIcon(file.name)
            ivFileIcon.setImageResource(iconRes)
        }

        private fun getFileTypeIcon(fileName: String): Int {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> R.drawable.ic_photo
                "mp4", "mkv", "avi", "mov", "3gp", "webm" -> R.drawable.ic_file_generic
                "mp3", "aac", "flac", "wav", "ogg", "m4a" -> R.drawable.ic_file_generic
                "zip", "rar", "7z", "tar", "gz" -> R.drawable.ic_file_generic
                else -> R.drawable.ic_file_generic
            }
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes <= 0 -> ""
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> String.format(
                    Locale.US, "%.1f MB", bytes / (1024.0 * 1024)
                )
                else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transfer_queue, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class TransferFileDiffCallback : DiffUtil.ItemCallback<TransferFile>() {
        override fun areItemsTheSame(a: TransferFile, b: TransferFile) = a.id == b.id
        override fun areContentsTheSame(a: TransferFile, b: TransferFile) = a == b
    }
}