package com.example.beamshare.videotoaudio.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.databinding.ItemVaLogEntryBinding
import com.example.beamshare.videotoaudio.model.LogEntry
import com.example.beamshare.videotoaudio.model.LogStatus

class LogEntryAdapter : ListAdapter<LogEntry, LogEntryAdapter.VH>(DIFF) {

    inner class VH(val binding: ItemVaLogEntryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: LogEntry) {
            binding.tvLogText.text = item.text
            binding.tvLogTime.text = item.timestamp

            val dotColor = when (item.status) {
                LogStatus.DONE        -> ContextCompat.getColor(binding.root.context, R.color.va_green)
                LogStatus.IN_PROGRESS -> ContextCompat.getColor(binding.root.context, R.color.va_accent)
                LogStatus.PENDING     -> ContextCompat.getColor(binding.root.context, R.color.va_border)
            }
            binding.statusDot.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(dotColor)
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemVaLogEntryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<LogEntry>() {
            override fun areItemsTheSame(a: LogEntry, b: LogEntry) = a.id == b.id
            override fun areContentsTheSame(a: LogEntry, b: LogEntry) = a == b
        }
    }
}
