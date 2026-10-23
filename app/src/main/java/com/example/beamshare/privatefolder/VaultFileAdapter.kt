package com.example.beamshare.privatefolder

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import java.io.File
import java.text.DecimalFormat

class VaultFileAdapter(
    private val context: Context,
    private var files: MutableList<File>,
    private val onFileClick: (File) -> Unit,
    private val onDeleteFile: (File) -> Unit,
    private val onShareFile: (File) -> Unit,
    private val onMoveFile: (File) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_FOLDER = 0
        private const val TYPE_FILE   = 1
    }

    // Colorful card backgrounds cycling through blue/green/yellow/purple/orange
    private val cardColors = listOf(
        "#EEF2FF", "#F0FDF4", "#FEFCE8", "#F5F3FF", "#FFF7ED"
    )
    private val cardColorsDark = listOf(
        "#1E1B4B", "#064E3B", "#422006", "#2E1065", "#431407"
    )
    private val iconTints = listOf(
        "#6366F1", "#22C55E", "#EAB308", "#A855F7", "#F97316"
    )

    // ── ViewHolders ────────────────────────────────────────────────────────────

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView  = itemView.findViewById(R.id.ivFolderIcon)
        val name: TextView   = itemView.findViewById(R.id.tvFolderName)
        val count: TextView  = itemView.findViewById(R.id.tvFolderCount)
        val more: ImageView  = itemView.findViewById(R.id.ivFolderMore)
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card:   CardView  = itemView as CardView
        val iconBg: View      = itemView.findViewById(R.id.viewIconBg)
        val icon:   ImageView = itemView.findViewById(R.id.ivFileIcon)
        val name:   TextView  = itemView.findViewById(R.id.tvFileName)
        val size:   TextView  = itemView.findViewById(R.id.tvFileSize)
        val more:   ImageView = itemView.findViewById(R.id.ivMore)
    }

    // ── Adapter overrides ──────────────────────────────────────────────────────

    override fun getItemViewType(position: Int) =
        if (files[position].isDirectory) TYPE_FOLDER else TYPE_FILE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(context)
        return if (viewType == TYPE_FOLDER) {
            FolderViewHolder(inflater.inflate(R.layout.item_vault_folder, parent, false))
        } else {
            FileViewHolder(inflater.inflate(R.layout.item_vault_file, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val file = files[position]
        if (holder is FolderViewHolder) bindFolder(holder, file)
        else if (holder is FileViewHolder) bindFile(holder, file, position)
    }

    override fun getItemCount() = files.size

    // ── Bind folder ────────────────────────────────────────────────────────────

    private fun bindFolder(holder: FolderViewHolder, file: File) {
        holder.name.text  = file.name
        holder.count.text = "${file.listFiles()?.size ?: 0} items"

        holder.itemView.setOnClickListener { onFileClick(file) }

        holder.more.setOnClickListener { v ->
            val popup = PopupMenu(context, v)
            popup.menu.add(0, 1, 0, "Delete")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { onDeleteFile(file); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    // ── Bind file ──────────────────────────────────────────────────────────────

    private fun bindFile(holder: FileViewHolder, file: File, position: Int) {
        val colorIdx = position % cardColors.size
        val isDark = (context.resources.configuration.uiMode
                and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES

        try {
            holder.iconBg.setBackgroundColor(
                Color.parseColor(if (isDark) cardColorsDark[colorIdx] else cardColors[colorIdx])
            )
            holder.icon.setImageResource(getIconForFile(file))
            holder.icon.setColorFilter(Color.parseColor(iconTints[colorIdx]))
        } catch (_: Exception) {}

        holder.name.text = file.name
        holder.size.text = formatSize(file.length())

        holder.card.setOnClickListener { onFileClick(file) }

        holder.more.setOnClickListener { v ->
            val popup = PopupMenu(context, v)
            popup.menu.add(0, 1, 0, "Move")
            popup.menu.add(0, 2, 0, "Delete")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> { onMoveFile(file); true }
                    2 -> { onDeleteFile(file); true }
                    else -> false
                }
            }
            popup.show()
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    fun updateFiles(newFiles: List<File>) {
        files = newFiles.toMutableList()
        notifyDataSetChanged()
    }

    private fun getIconForFile(file: File): Int {
        if (file.isDirectory) return R.drawable.ic_folder
        return when (file.extension.lowercase()) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> R.drawable.ic_photo
            "mp4", "mov", "avi", "mkv", "3gp"                  -> R.drawable.ic_video
            "mp3", "wav", "flac", "aac", "m4a", "ogg"          -> R.drawable.ic_music
            "pdf", "doc", "docx", "txt", "odt"                  -> R.drawable.ic_document
            "zip", "rar", "tar", "gz"                           -> R.drawable.ic_document
            else                                                -> R.drawable.ic_document
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val capped = minOf(digitGroups, units.size - 1)
        return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, capped.toDouble())) + " " + units[capped]
    }
}