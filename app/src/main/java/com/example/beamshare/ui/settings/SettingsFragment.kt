package com.example.beamshare.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentSettingsBinding
import com.example.beamshare.permissions.PermissionsActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    // SAF folder picker — must be registered before onStart
    private val folderPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) return@registerForActivityResult
            // Persist permission across reboots
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val displayPath = uri.lastPathSegment
                ?.replace("primary:", "Internal/")
                ?: uri.toString()
            prefs().edit { putString(KEY_SAVE_LOCATION, uri.toString()) }
            binding.tvSaveLocationValue.text = displayPath
        }

    companion object {
        private const val PREFS_NAME          = "beamshare_prefs"
        private const val KEY_DEVICE_NAME     = "device_name"
        private const val KEY_SAVE_LOCATION   = "save_location"
        private const val KEY_CONN_METHOD     = "connection_method"
        private const val KEY_NOTIF_PROGRESS  = "notif_progress"
        private const val KEY_NOTIF_COMPLETE  = "notif_complete"
        private const val KEY_NOTIF_FAILED    = "notif_failed"
        private const val DEFAULT_SAVE_LOC    = "Downloads/BeamShare"

        // Runtime permissions shown in PermissionsActivity
        // (FOREGROUND_SERVICE, INTERNET etc. are install-time — not counted here)
        private val RUNTIME_PERMISSIONS = buildList {
            // Wi-Fi / Location
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            // Bluetooth (Android 12+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            // Storage
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            // Camera
            add(Manifest.permission.CAMERA)
            // Contacts
            add(Manifest.permission.READ_CONTACTS)
            // Notifications (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                add(Manifest.permission.POST_NOTIFICATIONS)
            // Install packages is a special permission — not a runtime permission
            // checked via PackageManager.canRequestPackageInstalls(), counted separately
        }
    }

    // ── Inflate ────────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadPreferences()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionSummary()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Load saved prefs into UI ───────────────────────────────────────────
    private fun loadPreferences() {
        val prefs = prefs()

        binding.tvDeviceNameValue.text =
            prefs.getString(KEY_DEVICE_NAME, null) ?: Build.MODEL

        val savedUri = prefs.getString(KEY_SAVE_LOCATION, null)
        binding.tvSaveLocationValue.text = if (savedUri != null) {
            Uri.parse(savedUri).lastPathSegment
                ?.replace("primary:", "Internal/") ?: savedUri
        } else {
            DEFAULT_SAVE_LOC
        }

        binding.tvConnectionMethodValue.text =
            prefs.getString(KEY_CONN_METHOD, getString(R.string.sheet_method_wifi_direct))

        binding.switchNotifProgress.isChecked = prefs.getBoolean(KEY_NOTIF_PROGRESS, true)
        binding.switchNotifComplete.isChecked = prefs.getBoolean(KEY_NOTIF_COMPLETE, true)
        binding.switchNotifFailed.isChecked   = prefs.getBoolean(KEY_NOTIF_FAILED,   true)

        val versionName = runCatching {
            requireContext().packageManager
                .getPackageInfo(requireContext().packageName, 0).versionName
        }.getOrDefault("1.0.0")
        binding.tvVersionValue.text = versionName
    }

    // ── Click listeners ────────────────────────────────────────────────────
    private fun setupClickListeners() {

        binding.cardDeviceName.setOnClickListener   { showEditDeviceNameSheet() }
        binding.rowSaveLocation.setOnClickListener  { showSaveLocationInfo() }
        binding.rowConnectionMethod.setOnClickListener { showConnectionMethodSheet() }

        binding.switchNotifProgress.setOnCheckedChangeListener { _, checked ->
            prefs().edit { putBoolean(KEY_NOTIF_PROGRESS, checked) }
        }
        binding.switchNotifComplete.setOnCheckedChangeListener { _, checked ->
            prefs().edit { putBoolean(KEY_NOTIF_COMPLETE, checked) }
        }
        binding.switchNotifFailed.setOnCheckedChangeListener { _, checked ->
            prefs().edit { putBoolean(KEY_NOTIF_FAILED, checked) }
        }

        // Single card → opens full PermissionsActivity
        binding.cardPermissions.setOnClickListener {
            startActivity(Intent(requireContext(), PermissionsActivity::class.java))
        }
    }

    // ── Permission summary (replaces old badge rows) ───────────────────────
    private fun refreshPermissionSummary() {
        val ctx = requireContext()
        val runtimePerms = RUNTIME_PERMISSIONS

        val granted = runtimePerms.count { perm ->
            ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        }
        val total = runtimePerms.size

        // REQUEST_INSTALL_PACKAGES is a "special" permission — not a runtime perm
        // We count it in the total but check separately
        val canInstall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            ctx.packageManager.canRequestPackageInstalls() else true
        val totalWithInstall = total + 1
        val grantedWithInstall = granted + (if (canInstall) 1 else 0)

        val allGranted = grantedWithInstall >= totalWithInstall
        val summary = "$totalWithInstall permissions · $grantedWithInstall granted"

        binding.tvPermissionSummary.text = summary
        binding.tvPermissionSummary.setTextColor(
            if (allGranted)
                ContextCompat.getColor(ctx, R.color.fg_secondary)
            else
                ContextCompat.getColor(ctx, R.color.perm_denied)
        )
    }

    // ── Bottom sheet: edit device name ─────────────────────────────────────
    private fun showEditDeviceNameSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val v = LayoutInflater.from(requireContext())
            .inflate(R.layout.sheet_edit_device_name, sheet.window?.decorView as? ViewGroup, false)
        sheet.setContentView(v)

        val til     = v.findViewById<TextInputLayout>(R.id.tilDeviceName)
        val et      = v.findViewById<TextInputEditText>(R.id.etDeviceName)
        val btnSave = v.findViewById<View>(R.id.btnSaveDeviceName)

        et.setText(binding.tvDeviceNameValue.text)

        btnSave.setOnClickListener {
            val name = et.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) {
                til.error = getString(R.string.sheet_edit_name_hint)
                return@setOnClickListener
            }
            til.error = null
            prefs().edit { putString(KEY_DEVICE_NAME, name) }
            binding.tvDeviceNameValue.text = name
            Toast.makeText(requireContext(),
                getString(R.string.toast_device_name_saved), Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }
        sheet.show()
    }

    // ── Bottom sheet: connection method ────────────────────────────────────
    private fun showConnectionMethodSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val v = LayoutInflater.from(requireContext())
            .inflate(R.layout.sheet_connection_method, sheet.window?.decorView as? ViewGroup, false)
        sheet.setContentView(v)

        fun pick(label: String) {
            binding.tvConnectionMethodValue.text = label
            prefs().edit { putString(KEY_CONN_METHOD, label) }
            sheet.dismiss()
        }

        v.findViewById<View>(R.id.optionWifiDirect).setOnClickListener {
            pick(getString(R.string.sheet_method_wifi_direct))
        }
        v.findViewById<View>(R.id.optionHotspot).setOnClickListener {
            pick(getString(R.string.sheet_method_hotspot))
        }
        v.findViewById<View>(R.id.optionBluetooth).setOnClickListener {
            pick(getString(R.string.sheet_method_bluetooth))
        }

        sheet.show()
    }

    // ── Save location ──────────────────────────────────────────────────────
    private fun showSaveLocationInfo() {
        folderPickerLauncher.launch(null)
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private fun prefs() =
        requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}