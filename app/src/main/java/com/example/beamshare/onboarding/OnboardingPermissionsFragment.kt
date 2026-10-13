package com.example.beamshare.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentOnboardingPermissionsBinding

class OnboardingPermissionsFragment : Fragment() {

    private var _binding: FragmentOnboardingPermissionsBinding? = null
    private val binding get() = _binding!!

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Move to next page regardless of results
        (activity as? OnboardingActivity)?.goToNextPage()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPermissionItems()
        setupClickListeners()
    }

    private fun setupPermissionItems() {
        // 1. Nearby Wi-Fi Devices
        setupPermissionRow(
            binding.permWifi.root,
            R.drawable.ic_wifi,
            getString(R.string.perm_wifi_name),
            getString(R.string.perm_wifi_desc),
            isRequired = true
        )

        // 2. Bluetooth
        setupPermissionRow(
            binding.permBluetooth.root,
            R.drawable.ic_bluetooth,
            getString(R.string.perm_bluetooth_name),
            getString(R.string.perm_bluetooth_desc),
            isRequired = true
        )

        // 3. Files & Storage
        setupPermissionRow(
            binding.permStorage.root,
            R.drawable.ic_folder,
            getString(R.string.perm_storage_name),
            getString(R.string.perm_storage_desc),
            isRequired = true
        )

        // 4. Camera
        setupPermissionRow(
            binding.permCamera.root,
            R.drawable.ic_camera_perm,
            getString(R.string.perm_camera_name),
            getString(R.string.perm_camera_desc),
            isRequired = true
        )

        // 5. Contacts
        setupPermissionRow(
            binding.permContacts.root,
            R.drawable.ic_contacts,
            getString(R.string.perm_contacts_name),
            getString(R.string.perm_contacts_desc),
            isRequired = false
        )

        // 6. Notifications
        setupPermissionRow(
            binding.permNotifications.root,
            R.drawable.ic_notifications,
            getString(R.string.perm_notifications_name),
            getString(R.string.perm_notifications_desc),
            isRequired = false
        )
    }

    private fun setupPermissionRow(
        root: View,
        iconRes: Int,
        name: String,
        desc: String,
        isRequired: Boolean
    ) {
        root.findViewById<ImageView>(R.id.ivPermissionIcon).setImageResource(iconRes)
        root.findViewById<TextView>(R.id.tvPermissionName).text = name
        root.findViewById<TextView>(R.id.tvPermissionDesc).text = desc

        val badge = root.findViewById<TextView>(R.id.tvBadge)
        if (isRequired) {
            badge.text = getString(R.string.perm_badge_required)
            badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_red))
            badge.setBackgroundResource(R.drawable.bg_badge_required)
        } else {
            badge.text = getString(R.string.perm_badge_optional)
            badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.color_text_tertiary))
            badge.setBackgroundResource(R.drawable.bg_badge_optional)
        }
    }

    private fun setupClickListeners() {
        binding.btnAllow.setOnClickListener {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        // Bluetooth permissions (API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }

        // Storage permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}