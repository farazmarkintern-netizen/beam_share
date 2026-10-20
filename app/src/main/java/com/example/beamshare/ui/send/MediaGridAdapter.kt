// com/example/beamshare/ui/send/MediaGridAdapter.kt
package com.example.beamshare.ui.send

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.example.beamshare.R

class MediaGridAdapter(
    private val onSelectionChanged: (selectedCount: Int, totalSize: Long) -> Unit
) : ListAdapter<MediaFile, MediaGridAdapter.ViewHolder>(MediaDiffCallback()) {

    private val selectedIds = mutableSetOf<Long>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumbnail: ImageView = view.findViewById(R.id.ivThumbnail)
        val ivCheckbox: ImageView = view.findViewById(R.id.ivCheckbox)
        val viewSelectionOverlay: View = view.findViewById(R.id.viewSelectionOverlay)

        init {
            view.setOnClickListener {
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@setOnClickListener

                val item = getItem(position)
                toggleSelection(item.id)
                updateSelectionUI(item.id)
                notifySelectionChanged()
            }
        }

        fun bind(item: MediaFile) {
            Glide.with(itemView.context)
                .load(item.uri)
                .centerCrop()
                .placeholder(R.color.color_border)
                .transition(DrawableTransitionOptions.withCrossFade(150))
                .into(ivThumbnail)

            updateSelectionUI(item.id)
        }

        private fun updateSelectionUI(id: Long) {
            val isSelected = selectedIds.contains(id)
            if (isSelected) {
                ivCheckbox.setImageResource(R.drawable.bg_checkbox_selected)
                viewSelectionOverlay.visibility = View.VISIBLE
                viewSelectionOverlay.setBackgroundResource(R.drawable.bg_media_selected_border)
            } else {
                ivCheckbox.setImageResource(R.drawable.bg_checkbox_unselected)
                viewSelectionOverlay.visibility = View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_grid, parent, false)
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

    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        onSelectionChanged(0, 0)
    }

    /** Deselect items whose URI is in [uris]. Called after a transfer completes. */
    fun deselectUris(uris: Set<Uri>) {
        val idsToRemove = currentList.filter { it.uri in uris }.map { it.id }
        if (idsToRemove.isEmpty()) return
        idsToRemove.forEach { selectedIds.remove(it) }
        notifyDataSetChanged()
        notifySelectionChanged()
    }

    private class MediaDiffCallback : DiffUtil.ItemCallback<MediaFile>() {
        override fun areItemsTheSame(oldItem: MediaFile, newItem: MediaFile) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: MediaFile, newItem: MediaFile) = oldItem == newItem
    }
}