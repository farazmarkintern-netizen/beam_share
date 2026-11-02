package com.example.beamshare.documentscanner

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R

class DocPageAdapter(
    val pages: MutableList<Bitmap>,
    private val onPageOptions: (position: Int) -> Unit
) : RecyclerView.Adapter<DocPageAdapter.PageVH>() {

    inner class PageVH(view: View) : RecyclerView.ViewHolder(view) {
        val ivPage: ImageView        = view.findViewById(R.id.ivPageItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageVH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_doc_page, parent, false)
        return PageVH(view)
    }
    override fun onBindViewHolder(holder: PageVH, position: Int) {
        holder.ivPage.setImageBitmap(pages[position])
        holder.ivPage.setOnClickListener {
            onPageOptions(holder.adapterPosition)
        }
    }

    override fun getItemCount() = pages.size

    fun deletePage(position: Int) {
        if (position < 0 || position >= pages.size) return
        pages.removeAt(position)
        notifyItemRemoved(position)
        // Refresh neighbors so options button visibility updates
        notifyItemRangeChanged(0, pages.size)
    }

    fun movePage(from: Int, to: Int) {
        val item = pages.removeAt(from)
        pages.add(to, item)
        notifyItemMoved(from, to)
    }
}