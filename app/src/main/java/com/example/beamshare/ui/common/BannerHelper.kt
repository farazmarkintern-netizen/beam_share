// com/example/beamshare/ui/common/BannerHelper.kt
package com.example.beamshare.ui.common

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.example.beamshare.R

object BannerHelper {

    enum class BannerType {
        WIFI_DIRECT_UNAVAILABLE,
        BLUETOOTH_OFF,
        WIFI_OFF,
        STORAGE_FULL,
        WIFI_PERMISSION
    }

    fun showBanner(container: View, type: BannerType, onAction: (() -> Unit)? = null) {
        val ivIcon = container.findViewById<ImageView>(R.id.ivBannerIcon)
        val tvText = container.findViewById<TextView>(R.id.tvBannerText)
        val tvAction = container.findViewById<TextView>(R.id.tvBannerAction)

        when (type) {
            BannerType.WIFI_DIRECT_UNAVAILABLE -> {
                container.setBackgroundResource(R.drawable.bg_banner_warning)
                ivIcon.setImageResource(R.drawable.ic_wifi_off)
                tvText.text = "Wi-Fi Direct is not available on this device"
                tvAction.visibility = View.GONE
            }
            BannerType.BLUETOOTH_OFF -> {
                container.setBackgroundResource(R.drawable.bg_banner_info)
                ivIcon.setImageResource(R.drawable.ic_bluetooth_off)
                tvText.text = "Bluetooth is turned off"
                tvAction.text = "Turn On"
                tvAction.visibility = View.VISIBLE
            }
            BannerType.WIFI_OFF -> {
                container.setBackgroundResource(R.drawable.bg_banner_warning)
                ivIcon.setImageResource(R.drawable.ic_wifi_off)
                tvText.text = "Wi-Fi is turned off"
                tvAction.text = "Turn On"
                tvAction.visibility = View.VISIBLE
            }
            BannerType.STORAGE_FULL -> {
                container.setBackgroundResource(R.drawable.bg_banner_error)
                ivIcon.setImageResource(R.drawable.ic_storage_full)
                tvText.text = "Storage is full. Free up space to receive files."
                tvAction.text = "Manage"
                tvAction.visibility = View.VISIBLE
            }
            BannerType.WIFI_PERMISSION -> {
                container.setBackgroundResource(R.drawable.bg_banner_warning)
                ivIcon.setImageResource(R.drawable.ic_wifi_off)
                tvText.text = "Wi-Fi permission is required for nearby sharing"
                tvAction.text = "Grant"
                tvAction.visibility = View.VISIBLE
            }
        }

        tvAction.setOnClickListener { onAction?.invoke() }
        container.visibility = View.VISIBLE
    }

    fun hideBanner(container: View) {
        container.visibility = View.GONE
    }
}