// com/example/beamshare/ui/discovery/NearbyDeviceAdapter.kt
package com.example.beamshare.ui.discovery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.model.NearbyDevice

class NearbyDeviceAdapter(
    private val onConnectClick: (NearbyDevice) -> Unit
) : ListAdapter<NearbyDevice, NearbyDeviceAdapter.ViewHolder>(DeviceDiffCallback()) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDeviceName: TextView = view.findViewById(R.id.tvDeviceName)
        val tvDeviceStatus: TextView = view.findViewById(R.id.tvDeviceStatus)
        val ivChevron: ImageView = view.findViewById(R.id.ivChevron)
        val pbConnecting: ProgressBar = view.findViewById(R.id.pbConnecting)

        fun bind(device: NearbyDevice) {
            tvDeviceName.text = device.name
            tvDeviceStatus.text = if (device.isConnecting) {
                itemView.context.getString(R.string.connecting)
            } else {
                device.signalStrength
            }

            if (device.isConnecting) {
                // Connecting: show spinner, hide chevron, blue status text
                pbConnecting.isVisible = true
                ivChevron.isGone = true
                tvDeviceStatus.setTextColor(
                    itemView.context.getColor(R.color.color_primary)
                )
                itemView.alpha = 1f
                itemView.isEnabled = false
            } else if (device.signalStrength == "Available") {
                // Available: show chevron, hide spinner
                pbConnecting.isGone = true
                ivChevron.isVisible = true
                tvDeviceStatus.setTextColor(
                    itemView.context.getColor(R.color.color_text_secondary)
                )
                itemView.alpha = 1f
                itemView.isEnabled = true
            } else {
                // Busy / Unavailable: hide both
                pbConnecting.isGone = true
                ivChevron.isGone = true
                tvDeviceStatus.setTextColor(
                    itemView.context.getColor(R.color.color_text_tertiary)
                )
                itemView.alpha = 0.6f
                itemView.isEnabled = false
            }

            itemView.setOnClickListener {
                if (!device.isConnecting && device.signalStrength == "Available") {
                    onConnectClick(device)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_nearby_device, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private class DeviceDiffCallback : DiffUtil.ItemCallback<NearbyDevice>() {
        override fun areItemsTheSame(a: NearbyDevice, b: NearbyDevice) = a.id == b.id
        override fun areContentsTheSame(a: NearbyDevice, b: NearbyDevice) = a == b
    }
}