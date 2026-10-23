package com.example.beamshare.permissions

import android.Manifest
import android.Manifest.permission.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.beamshare.R
import com.example.beamshare.databinding.ActivityPermissionsAccordionBinding
import com.example.beamshare.databinding.ItemPermissionCardToggleBinding

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsAccordionBinding

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshAllCards()
        }

    data class PermissionData(
        val permName: String,
        val icon: Int,
        val iconTint: Int,
        val iconBg: Int,
        val title: Int,
        val subtitle: Int,
        val dialogDesc: Int,          // full description shown in dialog
        val runtimePermissions: List<String>,
        val isSpecial: Boolean = false
    )

    private data class CardEntry(
        val binding: ItemPermissionCardToggleBinding,
        val data: PermissionData
    )

    private val cards = mutableListOf<CardEntry>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityPermissionsAccordionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Push content below status bar and above nav bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }

        binding.btnBack.setOnClickListener { finish() }
        bindAllCards()
        setupGrantButton()
    }

    override fun onResume() {
        super.onResume()
        refreshAllCards()
    }

    private fun buildPermissionList(): List<PermissionData> = buildList {

        add(PermissionData(
            permName           = "NEARBY_WIFI_DEVICES",
            icon               = R.drawable.ic_wifi,
            iconTint           = R.color.perm_wifi,
            iconBg             = R.color.perm_wifi_bg,
            title              = R.string.perm_wifi_title,
            subtitle           = R.string.perm_wifi_sub,
            dialogDesc         = R.string.perm_wifi_desc,
            runtimePermissions = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                    add(NEARBY_WIFI_DEVICES)
                add(Manifest.permission.CHANGE_NETWORK_STATE)
                add(Manifest.permission.ACCESS_WIFI_STATE)
            }
        ))

        add(PermissionData(
            permName           = "ACCESS_FINE_LOCATION",
            icon               = R.drawable.ic_location,
            iconTint           = R.color.perm_location,
            iconBg             = R.color.perm_location_bg,
            title              = R.string.perm_location_title,
            subtitle           = R.string.perm_location_sub,
            dialogDesc         = R.string.perm_location_desc,
            runtimePermissions = listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
        ))

        add(PermissionData(
            permName           = "BLUETOOTH_SCAN",
            icon               = R.drawable.ic_bluetooth,
            iconTint           = R.color.perm_bluetooth,
            iconBg             = R.color.perm_bluetooth_bg,
            title              = R.string.perm_bluetooth_title,
            subtitle           = R.string.perm_bluetooth_sub,
            dialogDesc         = R.string.perm_bluetooth_desc,
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                listOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
            else emptyList()
        ))

        add(PermissionData(
            permName           = "READ_MEDIA_*",
            icon               = R.drawable.ic_folder,
            iconTint           = R.color.perm_storage,
            iconBg             = R.color.perm_storage_bg,
            title              = R.string.perm_read_files_title,
            subtitle           = R.string.perm_read_files_sub,
            dialogDesc         = R.string.perm_read_files_desc,
            runtimePermissions = buildList {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(READ_MEDIA_IMAGES); add(READ_MEDIA_VIDEO); add(READ_MEDIA_AUDIO)
                } else add(READ_EXTERNAL_STORAGE)
            }
        ))

        add(PermissionData(
            permName           = "WRITE_EXTERNAL_STORAGE",
            icon               = R.drawable.ic_save,
            iconTint           = R.color.perm_save,
            iconBg             = R.color.perm_save_bg,
            title              = R.string.perm_save_files_title,
            subtitle           = R.string.perm_save_files_sub,
            dialogDesc         = R.string.perm_save_files_desc,
            runtimePermissions = if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
                listOf(WRITE_EXTERNAL_STORAGE)
            else emptyList()
        ))

        add(PermissionData(
            permName           = "CAMERA",
            icon               = R.drawable.ic_camera,
            iconTint           = R.color.perm_camera,
            iconBg             = R.color.perm_camera_bg,
            title              = R.string.perm_camera_title,
            subtitle           = R.string.perm_camera_sub,
            dialogDesc         = R.string.perm_camera_desc,
            runtimePermissions = listOf(CAMERA)
        ))

        add(PermissionData(
            permName           = "READ_CONTACTS",
            icon               = R.drawable.ic_contacts,
            iconTint           = R.color.perm_contacts,
            iconBg             = R.color.perm_contacts_bg,
            title              = R.string.perm_contacts_title,
            subtitle           = R.string.perm_contacts_sub,
            dialogDesc         = R.string.perm_contacts_desc,
            runtimePermissions = listOf(READ_CONTACTS)
        ))

        add(PermissionData(
            permName           = "POST_NOTIFICATIONS",
            icon               = R.drawable.ic_notifications,
            iconTint           = R.color.perm_notif,
            iconBg             = R.color.perm_notif_bg,
            title              = R.string.perm_notif_title,
            subtitle           = R.string.perm_notif_sub,
            dialogDesc         = R.string.perm_notif_desc,
            runtimePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                listOf(POST_NOTIFICATIONS)
            else emptyList()
        ))

        add(PermissionData(
            permName           = "REQUEST_INSTALL_PACKAGES",
            icon               = R.drawable.ic_install_package,
            iconTint           = R.color.perm_install,
            iconBg             = R.color.perm_install_bg,
            title              = R.string.perm_install_title,
            subtitle           = R.string.perm_install_sub,
            dialogDesc         = R.string.perm_install_desc,
            runtimePermissions = emptyList(),
            isSpecial          = true
        ))
    }

    private fun bindAllCards() {
        val permList = buildPermissionList()

        val cardBindings = listOf(
            binding.cardWifiDirect,
            binding.cardLocation,
            binding.cardBluetooth,
            binding.cardReadFiles,
            binding.cardSaveFiles,
            binding.cardCamera,
            binding.cardContacts,
            binding.cardNotifications,
            binding.cardInstallPackages
        )

        permList.forEachIndexed { index, permData ->
            val cardBinding = cardBindings.getOrNull(index) ?: return@forEachIndexed

            cardBinding.ivIcon.setImageResource(permData.icon)
            cardBinding.ivIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, permData.iconTint)
            )
            cardBinding.ivIcon.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, permData.iconBg)
            )
            cardBinding.tvTitle.setText(permData.title)
            cardBinding.tvSubtitle.setText(permData.subtitle)

            cardBinding.root.setOnClickListener { handlePermissionRequest(permData) }

            cards += CardEntry(cardBinding, permData)
        }

        refreshAllCards()
    }

    private fun handlePermissionRequest(data: PermissionData) {
        when {
            data.isSpecial                    -> openInstallPackagesSettings()
            data.runtimePermissions.isEmpty() -> Unit
            else -> requestPermissionsLauncher.launch(data.runtimePermissions.toTypedArray())
        }
    }

    // ── Refresh all cards (status chip) ──────────────────────────────────────
    private fun refreshAllCards() {
        for ((cardBinding, permData) in cards) {
            val granted = isGranted(permData)
            cardBinding.tvStatus.text = if (granted)
                getString(R.string.perm_status_granted)
            else
                getString(R.string.perm_status_required)

            val chipColor = ContextCompat.getColor(
                this, if (granted) R.color.color_green else R.color.accent
            )
            val chipDrawable = DrawableCompat.wrap(
                cardBinding.tvStatus.background.mutate()
            )
            DrawableCompat.setTint(chipDrawable, chipColor)
            cardBinding.tvStatus.background = chipDrawable
        }
        updateGrantButton()
    }

    private fun isGranted(data: PermissionData): Boolean {
        if (data.isSpecial) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                packageManager.canRequestPackageInstalls()
            else true
        }
        if (data.runtimePermissions.isEmpty()) return true
        return data.runtimePermissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun setupGrantButton() {
        binding.btnGrantPermissions.setOnClickListener { requestAllPending() }
        binding.btnSkip.setOnClickListener { navigateNext() }
    }

    private fun requestAllPending() {
        val allRuntime = cards
            .filter { !it.data.isSpecial && !isGranted(it.data) }
            .flatMap { it.data.runtimePermissions }
            .distinct()

        val hasSpecialPending = cards.any { it.data.isSpecial && !isGranted(it.data) }

        if (allRuntime.isNotEmpty())  requestPermissionsLauncher.launch(allRuntime.toTypedArray())
        if (hasSpecialPending)        openInstallPackagesSettings()
        if (allRuntime.isEmpty() && !hasSpecialPending) navigateNext()
    }

    private fun updateGrantButton() {
        val allGranted = cards.all { isGranted(it.data) }
        binding.btnGrantPermissions.text = getString(
            if (allGranted) R.string.perm_btn_continue else R.string.perm_btn_grant
        )
        binding.btnGrantPermissions.setOnClickListener {
            if (allGranted) navigateNext() else requestAllPending()
        }
    }

    private fun navigateNext() { finish() }

    private fun openInstallPackagesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }
}