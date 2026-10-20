// com/example/beamshare/ui/transfers/TransferHistoryAdapter.kt
package com.example.beamshare.ui.transfers

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.model.HistoryStatus
import com.example.beamshare.model.TransferDirection
import com.example.beamshare.model.TransferHistoryItem

class TransferHistoryAdapter(
    private val onItemClick: (TransferHistoryItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    private val items = mutableListOf<Any>() // String for header, TransferHistoryItem for item

    fun submitGroupedList(grouped: Map<String, List<TransferHistoryItem>>) {
        items.clear()
        for ((date, transfers) in grouped) {
            items.add(date)
            items.addAll(transfers)
        }
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position] is String) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transfer_history_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_transfer_history, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> holder.bind(items[position] as String)
            is ItemViewHolder -> {
                val item = items[position] as TransferHistoryItem
                holder.bind(item)
                holder.itemView.setOnClickListener { onItemClick(item) }
            }
        }
    }

    override fun getItemCount() = items.size

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvDate: TextView = view.findViewById(R.id.tvDateHeader)
        fun bind(date: String) {
            tvDate.text = date.uppercase()
        }
    }

    inner class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val viewIconBg: View = view.findViewById(R.id.viewIconBg)
        private val ivIcon: ImageView = view.findViewById(R.id.ivFileTypeIcon)
        private val tvTitle: TextView = view.findViewById(R.id.tvHistoryTitle)
        private val tvDetails: TextView = view.findViewById(R.id.tvHistoryDetails)
        private val viewStatusBg: View = view.findViewById(R.id.viewStatusBg)
        private val ivStatusIcon: ImageView = view.findViewById(R.id.ivStatusIcon)

        fun bind(item: TransferHistoryItem) {
            tvTitle.text = if (item.fileCount > 1) {
                "${item.fileCount} files"
            } else {
                item.title
            }

            val directionLabel = if (item.direction == TransferDirection.SENT) "To" else "From"
            tvDetails.text = "$directionLabel ${item.deviceName} · ${item.totalSize}"

            // Icon and color based on file type
            val (iconRes, bgColor) = getIconForType(item.fileType)
            ivIcon.setImageResource(iconRes)
            val bg = viewIconBg.background as? GradientDrawable
                ?: GradientDrawable().also { viewIconBg.background = it }
            bg.shape = GradientDrawable.OVAL
            bg.setColor(bgColor)

            // Status badge (colored bg + arrow/x)
            when {
                item.status == HistoryStatus.FAILED || item.status == HistoryStatus.CANCELLED -> {
                    viewStatusBg.setBackgroundResource(R.drawable.bg_status_error)
                    ivStatusIcon.setImageResource(R.drawable.ic_close)
                    ivStatusIcon.setColorFilter("#EF4444".toColorInt())
                }
                item.direction == TransferDirection.SENT -> {
                    viewStatusBg.setBackgroundResource(R.drawable.bg_status_sent)
                    ivStatusIcon.setImageResource(R.drawable.ic_arrow_up)
                    ivStatusIcon.clearColorFilter()
                }
                else -> {
                    viewStatusBg.setBackgroundResource(R.drawable.bg_status_received)
                    ivStatusIcon.setImageResource(R.drawable.ic_arrow_down)
                    ivStatusIcon.clearColorFilter()
                }
            }
        }

        private fun getIconForType(type: String): Pair<Int, Int> {
            return when (type) {
                "photo" -> R.drawable.ic_photo to "#DBEAFE".toColorInt()
                "video" -> R.drawable.ic_video to "#FEE2E2".toColorInt()
                "music" -> R.drawable.ic_music to "#F3E8FF".toColorInt()
                "document" -> R.drawable.ic_document to "#FEF3C7".toColorInt()
                "app" -> R.drawable.ic_apps to "#DCFCE7".toColorInt()
                "contact" -> R.drawable.ic_contact to "#FCE7F3".toColorInt()
                else -> R.drawable.ic_file_outline to "#EEF2FF".toColorInt()
            }
        }
    }
}