package com.example.beamshare.permissions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.beamshare.R
import com.example.beamshare.databinding.ActivityPermissionsAccordionBinding
import com.example.beamshare.databinding.ItemPermissionCardAccordionBinding
import com.example.beamshare.ui.MainActivity
import com.google.android.material.card.MaterialCardView

// ---------------------------------------------------------------------------
// Data model
// ---------------------------------------------------------------------------

data class PermissionData(
    val iconRes: Int,
    val iconTint: Int,        // color res id
    val iconBgTint: Int,      // color res id
    val titleRes: Int,
    val descriptionRes: Int,
    val permName: String,     // displayed in chip
    val required: Boolean,
    val runtimePermissions: List<String>,
    /** true = this is a "special" permission (Settings page) not a runtime perm */
    val isSpecial: Boolean = false
)

// ---------------------------------------------------------------------------
// Activity
// ---------------------------------------------------------------------------

class PermissionsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionsAccordionBinding

    // card binding → expanded state
    private val expandedState = mutableMapOf<ItemPermissionCardAccordionBinding, Boolean>()

    // lazily built after binding is set
    private val permissionItems by lazy { buildPermissionList() }

    private val allRuntimePerms: Array<String> by lazy {
        permissionItems
            .filter { (_, data) -> !data.isSpecial }
            .flatMap { (_, data) -> data.runtimePermissions }
            .distinct()
            .toTypedArray()
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            onPermissionsResult(results)
        }

    // ---------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityPermissionsAccordionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        bindAllCards()
        setupButtons()
    }

    // ---------------------------------------------------------------------------
    // Insets
    // ---------------------------------------------------------------------------

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.root.getChildAt(0).setPadding(
                binding.root.getChildAt(0).paddingLeft,
                systemBars.top,
                binding.root.getChildAt(0).paddingRight,
                binding.root.getChildAt(0).paddingBottom
            )

            binding.nestedScrollView.setPadding(
                binding.nestedScrollView.paddingLeft,
                binding.nestedScrollView.paddingTop,
                binding.nestedScrollView.paddingRight,
                systemBars.bottom
            )

            WindowInsetsCompat.CONSUMED
        }
    }

    // ---------------------------------------------------------------------------
    // Build list
    // ---------------------------------------------------------------------------

    private fun buildPermissionList(): List<Pair<ItemPermissionCardAccordionBinding, PermissionData>> {
        return listOf(

            // ── Wi-Fi & Network ──────────────────────────────────────────
            cardBinding(binding.cardWifiDirect.root) to PermissionData(
                iconRes        = R.drawable.ic_wifi,
                iconTint       = R.color.perm_wifi,
                iconBgTint     = R.color.perm_wifi_bg,
                titleRes       = R.string.perm_wifi_title,
                descriptionRes = R.string.perm_wifi_desc,
                permName       = "NEARBY_WIFI_DEVICES",
                required       = true,
                runtimePermissions = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    else
                        add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            ),

            cardBinding(binding.cardLocation.root) to PermissionData(
                iconRes        = R.drawable.ic_device_search,
                iconTint       = R.color.perm_location,
                iconBgTint     = R.color.perm_location_bg,
                titleRes       = R.string.perm_location_title,
                descriptionRes = R.string.perm_location_desc,
                permName       = "ACCESS_FINE_LOCATION",
                required       = true,
                runtimePermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            ),

            // ── Bluetooth ────────────────────────────────────────────────
            cardBinding(binding.cardBluetooth.root) to PermissionData(
                iconRes        = R.drawable.ic_bluetooth,
                iconTint       = R.color.perm_bluetooth,
                iconBgTint     = R.color.perm_bluetooth_bg,
                titleRes       = R.string.perm_bt_title,
                descriptionRes = R.string.perm_bt_desc,
                permName       = "BLUETOOTH_SCAN / CONNECT",
                required       = true,
                runtimePermissions = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
            ),

            // ── Files & Storage ──────────────────────────────────────────
            cardBinding(binding.cardReadFiles.root) to PermissionData(
                iconRes        = R.drawable.ic_folder,
                iconTint       = R.color.perm_storage,
                iconBgTint     = R.color.perm_storage_bg,
                titleRes       = R.string.perm_read_title,
                descriptionRes = R.string.perm_read_desc,
                permName       = "READ_MEDIA_IMAGES / VIDEO",
                required       = true,
                runtimePermissions = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.READ_MEDIA_IMAGES)
                        add(Manifest.permission.READ_MEDIA_VIDEO)
                        add(Manifest.permission.READ_MEDIA_AUDIO)
                    } else {
                        add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            ),

            cardBinding(binding.cardSaveFiles.root) to PermissionData(
                iconRes        = R.drawable.ic_download,
                iconTint       = R.color.perm_save,
                iconBgTint     = R.color.perm_save_bg,
                titleRes       = R.string.perm_save_title,
                descriptionRes = R.string.perm_save_desc,
                permName       = "WRITE_EXTERNAL_STORAGE",
                required       = false,
                runtimePermissions = buildList {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q)
                        add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            ),

            // ── Camera ───────────────────────────────────────────────────
            cardBinding(binding.cardCamera.root) to PermissionData(
                iconRes        = R.drawable.ic_camera_perm,
                iconTint       = R.color.perm_camera,
                iconBgTint     = R.color.perm_camera_bg,
                titleRes       = R.string.perm_camera_title,
                descriptionRes = R.string.perm_camera_desc,
                permName       = "CAMERA",
                required       = true,
                runtimePermissions = listOf(Manifest.permission.CAMERA)
            ),

            // ── Contacts ─────────────────────────────────────────────────
            cardBinding(binding.cardContacts.root) to PermissionData(
                iconRes        = R.drawable.ic_contacts,
                iconTint       = R.color.perm_contacts,
                iconBgTint     = R.color.perm_contacts_bg,
                titleRes       = R.string.perm_contacts_title,
                descriptionRes = R.string.perm_contacts_desc,
                permName       = "READ_CONTACTS",
                required       = false,
                runtimePermissions = listOf(Manifest.permission.READ_CONTACTS)
            ),

            // ── Notifications ─────────────────────────────────────────────
            cardBinding(binding.cardNotifications.root) to PermissionData(
                iconRes        = R.drawable.ic_notifications,
                iconTint       = R.color.perm_notif,
                iconBgTint     = R.color.perm_notif_bg,
                titleRes       = R.string.perm_notif_title,
                descriptionRes = R.string.perm_notif_desc,
                permName       = "POST_NOTIFICATIONS",
                required       = false,
                runtimePermissions = buildList {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        add(Manifest.permission.POST_NOTIFICATIONS)
                }
            ),

            // ── Install Apps (special permission — opens Settings) ────────
            cardBinding(binding.cardInstallPackages.root) to PermissionData(
                iconRes        = R.drawable.ic_install_package,
                iconTint       = R.color.perm_install,
                iconBgTint     = R.color.perm_install_bg,
                titleRes       = R.string.perm_install_title,
                descriptionRes = R.string.perm_install_desc,
                permName       = "REQUEST_INSTALL_PACKAGES",
                required       = false,
                runtimePermissions = emptyList(), // not a runtime perm
                isSpecial      = true
            )
        )
    }

    // ---------------------------------------------------------------------------
    // Bind cards
    // ---------------------------------------------------------------------------

    private fun cardBinding(view: View) =
        ItemPermissionCardAccordionBinding.bind(view as MaterialCardView)

    private fun bindAllCards() {
        for ((cardBinding, data) in permissionItems) {
            bind(cardBinding, data)
        }
    }

    private fun bind(card: ItemPermissionCardAccordionBinding, data: PermissionData) {
        card.ivIcon.setImageResource(data.iconRes)
        card.ivIcon.backgroundTintList = ColorStateList.valueOf(getColor(data.iconBgTint))
        card.ivIcon.imageTintList      = ColorStateList.valueOf(getColor(data.iconTint))

        card.tvTitle.setText(data.titleRes)

        if (data.required) {
            card.chipRequired.visibility = View.VISIBLE
            card.chipOptional.visibility = View.GONE
        } else {
            card.chipRequired.visibility = View.GONE
            card.chipOptional.visibility = View.VISIBLE
        }

        card.tvDescription.setText(data.descriptionRes)
        card.chipPermName.text = data.permName

        card.llExpanded.visibility = View.GONE
        expandedState[card] = false

        card.llHeader.setOnClickListener {
            if (data.isSpecial) {
                // "Install packages" opens system settings
                openInstallPackagesSettings()
            } else {
                toggleCard(card)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Accordion toggle
    // ---------------------------------------------------------------------------

    private fun toggleCard(card: ItemPermissionCardAccordionBinding) {
        val isExpanded = expandedState[card] ?: false
        val parent = card.root.parent as? ViewGroup
        if (parent != null) {
            TransitionManager.beginDelayedTransition(parent, AutoTransition().apply { duration = 200 })
        }

        if (isExpanded) {
            card.llExpanded.visibility = View.GONE
            card.ivChevron.animate().rotation(0f).setDuration(200).start()
            card.ivChevron.imageTintList = ColorStateList.valueOf(getColor(R.color.fg_tertiary))
            card.root.strokeColor = getColor(R.color.card_border)
        } else {
            card.llExpanded.visibility = View.VISIBLE
            card.ivChevron.animate().rotation(180f).setDuration(200).start()
            card.ivChevron.imageTintList = ColorStateList.valueOf(getColor(R.color.accent))
            card.root.strokeColor = getColor(R.color.accent_soft_border)
        }
        expandedState[card] = !isExpanded
    }

    // ---------------------------------------------------------------------------
    // Buttons
    // ---------------------------------------------------------------------------

    private fun setupButtons() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnGrantPermissions.setOnClickListener {
            val needed = allRuntimePerms.filter { perm ->
                ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
            }
            if (needed.isEmpty()) proceedToApp()
            else permissionLauncher.launch(needed.toTypedArray())
        }

        binding.btnSkip.setOnClickListener { proceedToApp() }
    }

    // ---------------------------------------------------------------------------
    // Permission result
    // ---------------------------------------------------------------------------

    private fun onPermissionsResult(results: Map<String, Boolean>) {
        val requiredPerms = permissionItems
            .filter { (_, data) -> data.required && !data.isSpecial }
            .flatMap { (_, data) -> data.runtimePermissions }
            .distinct()

        val allGranted = requiredPerms.all { perm ->
            results[perm] == true ||
                    ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) proceedToApp()
        else highlightDeniedCards(results)
    }

    private fun highlightDeniedCards(results: Map<String, Boolean>) {
        for ((cardBinding, data) in permissionItems) {
            if (!data.required || data.isSpecial) continue
            val anyDenied = data.runtimePermissions.any { perm ->
                results[perm] == false &&
                        ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
            }
            if (anyDenied) {
                if (expandedState[cardBinding] != true) toggleCard(cardBinding)
                cardBinding.root.strokeColor = getColor(R.color.perm_denied)
            }
        }
    }

    // ---------------------------------------------------------------------------
    // Special permission helpers
    // ---------------------------------------------------------------------------

    /** Opens "Install unknown apps" system settings for REQUEST_INSTALL_PACKAGES */
    private fun openInstallPackagesSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
    }

    // ---------------------------------------------------------------------------
    // Navigation
    // ---------------------------------------------------------------------------

    private fun proceedToApp() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}