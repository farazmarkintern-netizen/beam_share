package com.example.beamshare.documentscanner

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R

class DocumentScannerAdapter(
    private val onMenuClick: (View, ScannedDoc) -> Unit
) : ListAdapter<ScannedDoc, DocumentScannerAdapter.DocVH>(DIFF) {

    inner class DocVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView  = itemView.findViewById(R.id.tvDocName)
        val tvSize: TextView  = itemView.findViewById(R.id.tvDocSize)
        val btnMenu: ImageView = itemView.findViewById(R.id.btnDocMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DocVH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_document_scanner, parent, false)
        return DocVH(v)
    }

    override fun onBindViewHolder(holder: DocVH, position: Int) {
        val doc = getItem(position)
        holder.tvName.text = doc.name
        holder.tvSize.text = ScannedDocRepository.formatSize(doc.sizeBytes)
        holder.btnMenu.setOnClickListener { onMenuClick(it, doc) }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ScannedDoc>() {
            override fun areItemsTheSame(a: ScannedDoc, b: ScannedDoc) = a.id == b.id
            override fun areContentsTheSame(a: ScannedDoc, b: ScannedDoc) = a == b
        }
    }
}
