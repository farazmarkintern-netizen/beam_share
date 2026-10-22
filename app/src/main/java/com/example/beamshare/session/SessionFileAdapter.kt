package com.example.beamshare.session

import android.content.Intent
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R

class SessionFileAdapter(
    private val onCancel: (SessionFile) -> Unit,
    private val onDelete: (SessionFile) -> Unit
) : RecyclerView.Adapter<SessionFileAdapter.ViewHolder>() {

    private val files = mutableListOf<SessionFile>()

    fun submitList(newFiles: List<SessionFile>) {
        files.clear()
        files.addAll(newFiles)
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
        private val ivFileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        private val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        private val tvDirection: TextView = view.findViewById(R.id.tvDirection)
        private val tvFileSize: TextView = view.findViewById(R.id.tvFileSize)
        private val btnCancel: ImageView = view.findViewById(R.id.btnCancel)
        private val ivStatus: ImageView = view.findViewById(R.id.ivStatus)
        private val layoutProgress: LinearLayout = view.findViewById(R.id.layoutProgress)
        private val progressBar: ProgressBar = view.findViewById(R.id.progressBar)
        private val tvProgress: TextView = view.findViewById(R.id.tvProgress)

        fun bind(file: SessionFile) {
            tvFileName.text = file.fileName
            tvFileSize.text = file.fileSize

            // Direction badge
            if (file.direction == FileDirection.SENT) {
                tvDirection.text = "SENT"
                tvDirection.setBackgroundResource(R.drawable.bg_badge_live)
                tvDirection.setTextColor(0xFF2563EB.toInt())
            } else {
                tvDirection.text = "RECEIVED"
                tvDirection.setBackgroundResource(R.drawable.bg_badge_connected)
                tvDirection.setTextColor(0xFF16A34A.toInt())
            }

            // Status — clear tint before setting drawable so previous row's tint doesn't bleed
            ImageViewCompat.setImageTintList(ivStatus, null)

            when (file.status) {
                FileStatus.ACTIVE -> {
                    btnCancel.visibility = View.VISIBLE
                    ivStatus.visibility = View.GONE
                    layoutProgress.visibility = View.VISIBLE
                    progressBar.progress = file.progress
                    tvProgress.text = "${file.progress}%"
                    btnCancel.setOnClickListener { onCancel(file) }
                }
                FileStatus.COMPLETED -> {
                    btnCancel.visibility = View.GONE
                    ivStatus.visibility = View.VISIBLE
                    // ic_check_circle_filled is already green — no tint needed
                    ivStatus.setImageResource(R.drawable.ic_check_circle_filled)
                    layoutProgress.visibility = View.GONE
                }
                FileStatus.FAILED -> {
                    btnCancel.visibility = View.GONE
                    ivStatus.visibility = View.VISIBLE
                    // ic_error_circle is already red — no tint needed
                    ivStatus.setImageResource(R.drawable.ic_error_circle)
                    layoutProgress.visibility = View.GONE
                }
                FileStatus.CANCELLED -> {
                    btnCancel.visibility = View.GONE
                    ivStatus.visibility = View.VISIBLE
                    // ic_close is neutral — tint amber for cancelled
                    ivStatus.setImageResource(R.drawable.ic_close)
                    ImageViewCompat.setImageTintList(
                        ivStatus,
                        ColorStateList.valueOf(0xFFD97706.toInt())
                    )
                    layoutProgress.visibility = View.GONE
                }
            }

            // Tap completed file to open it
            itemView.setOnClickListener {
                if (file.status == FileStatus.COMPLETED && file.savedUri != null) {
                    runCatching {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(
                                file.savedUri,
                                itemView.context.contentResolver.getType(file.savedUri) ?: "*/*"
                            )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        itemView.context.startActivity(intent)
                    }
                }
            }

            // Long-press to delete from list
            itemView.setOnLongClickListener {
                onDelete(file)
                true
            }
        }
    }
}