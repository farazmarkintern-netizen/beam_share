package com.example.beamshare.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.session.LiveTransfer
import com.example.beamshare.session.TransferDirection
import java.util.Locale

/**
 * Binds the active transfers in the home "LIVE NOW" card.
 * Incoming = green down/receive icon + green bar; outgoing = primary send icon + primary bar.
 */
class LiveTransferAdapter :
    ListAdapter<LiveTransfer, LiveTransferAdapter.VH>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_live_transfer, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconBg: View = itemView.findViewById(R.id.iconBg)
        private val ivDirection: ImageView = itemView.findViewById(R.id.ivDirection)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvSub: TextView = itemView.findViewById(R.id.tvSub)
        private val tvPercent: TextView = itemView.findViewById(R.id.tvPercent)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progressBar)

        fun bind(item: LiveTransfer) {
            val ctx = itemView.context
            val incoming = item.direction == TransferDirection.IN

            val accent = ContextCompat.getColor(
                ctx, if (incoming) R.color.color_green else R.color.color_primary
            )
            iconBg.setBackgroundResource(
                if (incoming) R.drawable.bg_icon_circle_green else R.drawable.bg_icon_circle_blue
            )
            ivDirection.setImageResource(if (incoming) R.drawable.ic_receive else R.drawable.ic_send)
            ivDirection.setColorFilter(accent)
            tvPercent.setTextColor(accent)
            progressBar.progressTintList = android.content.res.ColorStateList.valueOf(accent)

            tvName.text = item.name
            val dir = if (incoming)
                ctx.getString(R.string.live_from, item.peerName.ifBlank { ctx.getString(R.string.home_unknown_device) })
            else
                ctx.getString(R.string.live_to, item.peerName.ifBlank { ctx.getString(R.string.home_unknown_device) })
            tvSub.text = "$dir · ${formatSize(item.size)}"
            tvPercent.text = "${item.progress}%"
            progressBar.progress = item.progress
        }

        private fun formatSize(bytes: Long): String = when {
            bytes >= 1_073_741_824 -> String.format(Locale.US, "%.1f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LiveTransfer>() {
            override fun areItemsTheSame(a: LiveTransfer, b: LiveTransfer) =
                a.id == b.id && a.direction == b.direction
            override fun areContentsTheSame(a: LiveTransfer, b: LiveTransfer) = a == b
        }
    }
}
