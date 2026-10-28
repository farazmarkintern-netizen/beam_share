package com.example.beamshare.language

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.databinding.ItemLanguageBinding

class LanguageAdapter(
    private val items: MutableList<LanguageItem>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemLanguageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLanguageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvLanguage.text = item.name
        holder.binding.ivFlag.setImageResource(item.flagResId)
        holder.binding.cbSelected.isChecked = item.isSelected
        holder.binding.root.isActivated = item.isSelected

        holder.binding.root.setOnClickListener {
            onItemClick(position)
        }
    }

    override fun getItemCount() = items.size
}