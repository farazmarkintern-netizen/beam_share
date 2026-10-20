package com.example.beamshare.ui.session

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R

data class SessionFile(
    val fileName: String,
    val fileSize: String,
    val direction: String, // "Sent" or "Received"
    val time: String
)

class SessionFileAdapter(
    private val onItemClick: (SessionFile) -> Unit
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
        val file = files[position]
        holder.bind(file)
        holder.itemView.setOnClickListener { onItemClick(file) }
    }

    override fun getItemCount() = files.size

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        private val tvFileInfo: TextView = view.findViewById(R.id.tvFileInfo)

        fun bind(file: SessionFile) {
            tvFileName.text = file.fileName
            tvFileInfo.text = "${file.fileSize} · ${file.direction} · ${file.time}"
        }
    }
}