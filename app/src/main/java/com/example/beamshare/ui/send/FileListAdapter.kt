// com/example/beamshare/ui/send/FileListAdapter.kt
package com.example.beamshare.ui.send

import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import java.util.Locale
import kotlin.math.abs

class FileListAdapter(
    private val category: SendFilesActivity.Category,
    private val onSelectionChanged: (selectedCount: Int, totalSize: Long) -> Unit
) : ListAdapter<MediaFile, FileListAdapter.ViewHolder>(FileDiffCallback()) {

    private val selectedIds = mutableSetOf<Long>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val viewIconBg: View = view.findViewById(R.id.viewIconBg)
        val ivFileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        val ivAppIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvInitials: TextView = view.findViewById(R.id.tvInitials)
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
        val ivListCheckbox: ImageView = view.findViewById(R.id.ivListCheckbox)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                val file = getItem(position)
                toggleSelection(file.id)
                updateCheckbox(file.id)
                notifySelectionChanged()
            }
        }

        fun bind(file: MediaFile) {
            tvFileName.text = file.name
            tvFileSize.text = formatFileSize(file.size)
            tvFileSize.visibility = View.VISIBLE

            when (category) {
                SendFilesActivity.Category.APPS -> bindAppItem(file)
                SendFilesActivity.Category.DOCUMENTS -> bindDocumentItem(file)
                SendFilesActivity.Category.MUSIC -> bindMusicItem(file)
                SendFilesActivity.Category.CONTACTS -> bindContactItem(file)
                else -> bindGenericItem()
            }

            updateCheckbox(file.id)
        }

        private fun bindAppItem(file: MediaFile) {
            viewIconBg.visibility = View.VISIBLE
            viewIconBg.background = null
            ivFileIcon.visibility = View.GONE
            tvInitials.visibility = View.GONE
            ivAppIcon.visibility = View.VISIBLE

            // Load app icon directly from package name
            val pkg = file.packageName
            if (pkg != null) {
                try {
                    val icon = itemView.context.packageManager.getApplicationIcon(pkg)
                    ivAppIcon.setImageDrawable(icon)
                } catch (_: Exception) {
                    ivAppIcon.setImageResource(R.drawable.ic_file_apk)
                }
            } else {
                ivAppIcon.setImageResource(R.drawable.ic_file_apk)
            }
        }

        private fun bindDocumentItem(file: MediaFile) {
            viewIconBg.visibility = View.VISIBLE
            ivFileIcon.visibility = View.VISIBLE
            ivAppIcon.visibility = View.GONE
            tvInitials.visibility = View.GONE

            val (iconRes, bgColor) = getDocIconAndColor(file.mimeType, file.name)
            ivFileIcon.setImageResource(iconRes)

            val bg = viewIconBg.background as? GradientDrawable
                ?: GradientDrawable().also { viewIconBg.background = it }
            bg.shape = GradientDrawable.OVAL
            bg.setColor(bgColor)
        }

        private fun bindMusicItem(@Suppress("UNUSED_PARAMETER") file: MediaFile) {
            viewIconBg.visibility = View.VISIBLE
            ivFileIcon.visibility = View.VISIBLE
            ivAppIcon.visibility = View.GONE
            tvInitials.visibility = View.GONE

            ivFileIcon.setImageResource(R.drawable.ic_file_audio)
            val bg = viewIconBg.background as? GradientDrawable
                ?: GradientDrawable().also { viewIconBg.background = it }
            bg.shape = GradientDrawable.OVAL
            bg.setColor(ContextCompat.getColor(itemView.context, R.color.bs_blue_light))
        }

        private fun bindContactItem(file: MediaFile) {
            viewIconBg.visibility = View.VISIBLE
            ivFileIcon.visibility = View.GONE
            ivAppIcon.visibility = View.GONE
            tvInitials.visibility = View.VISIBLE
            tvFileSize.visibility = View.GONE

            val initials = file.name.split(" ")
                .take(2)
                .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                .joinToString("")
            tvInitials.text = initials

            val avatarColorRes = arrayOf(
                R.color.bs_blue, R.color.color_red, R.color.bs_green, R.color.bs_teal,
                R.color.bs_gold, R.color.bs_dark2, R.color.bs_teal, R.color.bs_gold
            )
            val colorIndex = abs(file.name.hashCode()) % avatarColorRes.size
            val bg = viewIconBg.background as? GradientDrawable
                ?: GradientDrawable().also { viewIconBg.background = it }
            bg.shape = GradientDrawable.OVAL
            bg.setColor(ContextCompat.getColor(itemView.context, avatarColorRes[colorIndex]))
        }

        private fun bindGenericItem() {
            viewIconBg.visibility = View.VISIBLE
            ivFileIcon.visibility = View.VISIBLE
            ivAppIcon.visibility = View.GONE
            tvInitials.visibility = View.GONE
            ivFileIcon.setImageResource(R.drawable.ic_file_doc)
        }

        private fun updateCheckbox(id: Long) {
            val isSelected = selectedIds.contains(id)
            ivListCheckbox.setImageResource(
                if (isSelected) R.drawable.bg_list_checkbox_selected
                else R.drawable.bg_list_checkbox_unselected
            )
        }

        private fun getDocIconAndColor(mimeType: String, name: String): Pair<Int, Int> {
            val ctx = itemView.context
            val lowerName = name.lowercase(Locale.ROOT)
            return when {
                mimeType.contains("pdf") || lowerName.endsWith(".pdf") ->
                    R.drawable.ic_file_pdf to ContextCompat.getColor(ctx, R.color.bs_surface)

                mimeType.contains("word") || lowerName.endsWith(".docx") || lowerName.endsWith(".doc") ->
                    R.drawable.ic_file_doc to ContextCompat.getColor(ctx, R.color.bs_blue_light)

                mimeType.contains("excel") || mimeType.contains("spreadsheet")
                        || lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".csv") ->
                    R.drawable.ic_file_excel to ContextCompat.getColor(ctx, R.color.bs_surface)

                mimeType.contains("presentation") || lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt") ->
                    R.drawable.ic_file_ppt to ContextCompat.getColor(ctx, R.color.bs_surface)

                mimeType.contains("text") || lowerName.endsWith(".txt") ->
                    R.drawable.ic_file_txt to ContextCompat.getColor(ctx, R.color.bs_surface)

                else -> R.drawable.ic_file_doc to ContextCompat.getColor(ctx, R.color.bs_blue_light)
            }
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes <= 0 -> ""
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
                else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private fun toggleSelection(id: Long) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
    }

    private fun notifySelectionChanged() {
        val selectedFiles = currentList.filter { selectedIds.contains(it.id) }
        onSelectionChanged(selectedFiles.size, selectedFiles.sumOf { it.size })
    }

    fun getSelectedCount(): Int = selectedIds.size


    fun getSelectedFiles(): List<MediaFile> =
        currentList.filter { selectedIds.contains(it.id) }

    /** Deselect items whose URI is in [uris]. Called after a transfer completes. */
    fun deselectUris(uris: Set<Uri>) {
        val idsToRemove = currentList.filter { it.uri in uris }.map { it.id }
        if (idsToRemove.isEmpty()) return
        idsToRemove.forEach { selectedIds.remove(it) }
        notifyDataSetChanged()
        val selectedFiles = currentList.filter { selectedIds.contains(it.id) }
        onSelectionChanged(selectedFiles.size, selectedFiles.sumOf { it.size })
    }

    private class FileDiffCallback : DiffUtil.ItemCallback<MediaFile>() {
        override fun areItemsTheSame(a: MediaFile, b: MediaFile) = a.id == b.id
        override fun areContentsTheSame(a: MediaFile, b: MediaFile) = a == b
    }
}