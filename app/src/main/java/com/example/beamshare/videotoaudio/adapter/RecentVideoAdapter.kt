package com.example.beamshare.videotoaudio.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.beamshare.databinding.ItemVaRecentVideoBinding
import com.example.beamshare.videotoaudio.model.VideoFile

class RecentVideoAdapter(
    private val onItemClick: (VideoFile) -> Unit
) : ListAdapter<VideoFile, RecentVideoAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemVaRecentVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: VideoFile) {
            binding.tvFileName.text = item.displayName
            binding.tvFileMeta.text = "${item.formattedDuration}  •  ${item.formattedSize}"

            // Thumbnail via Glide (load from content URI)
            Glide.with(binding.ivThumbnail)
                .load(Uri.parse(item.uri))
                .centerCrop()
                .placeholder(android.R.color.darker_gray)
                .into(binding.ivThumbnail)

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemVaRecentVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<VideoFile>() {
            override fun areItemsTheSame(a: VideoFile, b: VideoFile) = a.id == b.id
            override fun areContentsTheSame(a: VideoFile, b: VideoFile) = a == b
        }
    }
}