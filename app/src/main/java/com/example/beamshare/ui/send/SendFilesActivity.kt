// com/example/beamshare/ui/send/SendFilesActivity.kt
package com.example.beamshare.ui.send

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.session.SessionActivity
import com.example.beamshare.session.SessionManager
import com.example.beamshare.session.SessionState
import com.example.beamshare.session.TransferStatus
import com.example.beamshare.session.TransferDirection
import com.example.beamshare.ui.discovery.DeviceDiscoveryActivity
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import android.util.Log

class SendFilesActivity : AppCompatActivity() {

    private lateinit var rvMediaGrid: RecyclerView
    private lateinit var tabLayout: TabLayout
    private lateinit var tvSelectedBadge: TextView
    private lateinit var bottomBar: LinearLayout
    private lateinit var tvSelectedItems: TextView
    private lateinit var tvTotalSize: TextView
    private lateinit var btnNext: TextView
    private lateinit var emptyState: LinearLayout
    private lateinit var permissionState: LinearLayout
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvPermissionTitle: TextView
    private lateinit var tvPermissionDesc: TextView
    private lateinit var btnGrantPermission: TextView
    private lateinit var shimmerGrid: ShimmerFrameLayout
    private lateinit var shimmerList: ShimmerFrameLayout

    private var gridAdapter: MediaGridAdapter? = null
    private var listAdapter: FileListAdapter? = null

    private var currentCategory = Category.PHOTOS

    /** Maps filename → Uri so we can deselect by Uri when a transfer completes. */
    private val pendingSendUris = mutableMapOf<String, Uri>()

    enum class Category { PHOTOS, VIDEOS, MUSIC, DOCUMENTS, APPS, CONTACTS }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                showShimmerLoading()
                loadMedia(currentCategory)
            } else {
                showPermissionState()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_send_files)

        val initialCategoryName = intent.getStringExtra(EXTRA_CATEGORY)
        if (initialCategoryName != null) {
            try {
                currentCategory = Category.valueOf(initialCategoryName)
            } catch (_: Exception) {}
        }

        initViews()
        setupEdgeToEdge()
        setupTabs()
        setupClickListeners()
        observeTransfers()   // ← auto-deselect on successful send

        if (hasPermissionForCategory(currentCategory)) {
            showShimmerLoading()
            loadMedia(currentCategory)
        } else {
            showPermissionState()
        }
    }

    // ─── Auto-deselect sent files ────────────────────────────

    private fun observeTransfers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.transfers.collect { transfers ->
                    // Find URIs whose outgoing transfer completed successfully
                    val completedUris = transfers
                        .filter {
                            it.direction == TransferDirection.OUT &&
                                    it.status == TransferStatus.COMPLETED
                        }
                        .mapNotNull { pendingSendUris[it.name] }
                        .toSet()

                    if (completedUris.isNotEmpty()) {
                        gridAdapter?.deselectUris(completedUris)
                        listAdapter?.deselectUris(completedUris)
                        // Remove completed entries from the map
                        transfers.filter {
                            it.direction == TransferDirection.OUT &&
                                    it.status == TransferStatus.COMPLETED
                        }.forEach { pendingSendUris.remove(it.name) }
                    }
                }
            }
        }
    }

    private fun setupEdgeToEdge() {
        val root = findViewById<View>(R.id.main)
        val defaultBottomBarPaddingVertical = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._12sdp)
        val defaultRecyclerPadding = resources.getDimensionPixelSize(com.intuit.sdp.R.dimen._2sdp)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            root.setPadding(root.paddingLeft, systemBars.top, root.paddingRight, 0)
            bottomBar.setPadding(
                bottomBar.paddingLeft,
                defaultBottomBarPaddingVertical,
                bottomBar.paddingRight,
                defaultBottomBarPaddingVertical + systemBars.bottom
            )
            val extraBottomPadding = systemBars.bottom + defaultRecyclerPadding
            rvMediaGrid.setPadding(
                defaultRecyclerPadding, defaultRecyclerPadding,
                defaultRecyclerPadding, extraBottomPadding
            )
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentCategory == Category.DOCUMENTS && permissionState.visibility == View.VISIBLE) {
            if (hasPermissionForCategory(Category.DOCUMENTS)) {
                showShimmerLoading()
                loadMedia(Category.DOCUMENTS)
            }
        }
    }

    private fun initViews() {
        rvMediaGrid = findViewById(R.id.rvMediaGrid)
        tabLayout = findViewById(R.id.tabLayout)
        tvSelectedBadge = findViewById(R.id.tvSelectedBadge)
        bottomBar = findViewById(R.id.bottomBar)
        tvSelectedItems = findViewById(R.id.tvSelectedItems)
        tvTotalSize = findViewById(R.id.tvTotalSize)
        btnNext = findViewById(R.id.btnNext)
        emptyState = findViewById(R.id.emptyState)
        permissionState = findViewById(R.id.permissionState)
        tvEmptyTitle = findViewById(R.id.tvEmptyTitle)
        tvPermissionTitle = findViewById(R.id.tvPermissionTitle)
        tvPermissionDesc = findViewById(R.id.tvPermissionDesc)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        shimmerGrid = findViewById(R.id.shimmerGrid)
        shimmerList = findViewById(R.id.shimmerList)
    }

    private fun setupTabs() {
        val tabs = listOf("Photos", "Videos", "Music", "Documents", "Apps", "Contacts")
        tabs.forEach { title -> tabLayout.addTab(tabLayout.newTab().setText(title)) }

        val initialTab = tabLayout.getTabAt(currentCategory.ordinal)
        if (initialTab != null && !initialTab.isSelected) initialTab.select()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val category = when (tab?.position) {
                    0 -> Category.PHOTOS
                    1 -> Category.VIDEOS
                    2 -> Category.MUSIC
                    3 -> Category.DOCUMENTS
                    4 -> Category.APPS
                    5 -> Category.CONTACTS
                    else -> Category.PHOTOS
                }
                currentCategory = category
                updateBottomBar(0, 0)
                if (hasPermissionForCategory(category)) {
                    showShimmerLoading()
                    loadMedia(category)
                } else {
                    showPermissionState()
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        btnGrantPermission.setOnClickListener {
            requestPermissionForCategory(currentCategory)
        }

        btnNext.setOnClickListener {
            val selectedFiles = gridAdapter?.getSelectedFiles()
                ?: listAdapter?.getSelectedFiles()
                ?: emptyList()

            Log.d("BeamShare", "Selected files count: ${selectedFiles.size}")

            if (selectedFiles.isEmpty()) {
                Toast.makeText(this, getString(R.string.select_files_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val uris = ArrayList(selectedFiles.map { it.uri })
            val names = ArrayList(selectedFiles.map { it.name })
            val sizes = selectedFiles.map { it.size }.toLongArray()

            // Register name→Uri so observeTransfers() can deselect on completion
            selectedFiles.forEach { pendingSendUris[it.name] = it.uri }

            val state = SessionManager.state.value
            if (state is SessionState.Connected || state is SessionState.Reconnecting ||
                state is SessionState.Hosting || state is SessionState.Joining) {
                Log.d("BeamShare", "Session active, sending ${uris.size} files via SessionActivity")
                val intent = Intent(this, SessionActivity::class.java).apply {
                    putParcelableArrayListExtra(SessionActivity.EXTRA_PENDING_URIS, uris)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(intent)
            } else {
                Log.d("BeamShare", "Launching DeviceDiscoveryActivity with ${uris.size} files")
                val intent = Intent(this, DeviceDiscoveryActivity::class.java).apply {
                    putParcelableArrayListExtra(DeviceDiscoveryActivity.EXTRA_FILE_URIS, uris)
                    putStringArrayListExtra(DeviceDiscoveryActivity.EXTRA_FILE_NAMES, names)
                    putExtra(DeviceDiscoveryActivity.EXTRA_FILE_SIZES, sizes)
                }
                startActivity(intent)
            }
        }
    }

    // ─── Permissions ────────────────────────────────────────

    private fun hasPermissionForCategory(category: Category): Boolean {
        return when (category) {
            Category.CONTACTS -> checkPerm(Manifest.permission.READ_CONTACTS)
            Category.APPS -> true
            Category.DOCUMENTS -> hasFileAccessPermission()
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when (category) {
                        Category.PHOTOS -> checkPerm(Manifest.permission.READ_MEDIA_IMAGES)
                        Category.VIDEOS -> checkPerm(Manifest.permission.READ_MEDIA_VIDEO)
                        Category.MUSIC -> checkPerm(Manifest.permission.READ_MEDIA_AUDIO)
                        else -> false
                    }
                } else {
                    checkPerm(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }
    }

    private fun hasFileAccessPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkPerm(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun checkPerm(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionForCategory(category: Category) {
        when (category) {
            Category.DOCUMENTS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } else {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                }
            }
            Category.CONTACTS -> {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
            }
            Category.APPS -> {
                showShimmerLoading()
                loadMedia(category)
            }
            else -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    when (category) {
                        Category.PHOTOS -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_IMAGES))
                        Category.VIDEOS -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_VIDEO))
                        Category.MUSIC -> permissionLauncher.launch(arrayOf(Manifest.permission.READ_MEDIA_AUDIO))
                        else -> {}
                    }
                } else {
                    permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
                }
            }
        }
    }

    // ─── UI States ──────────────────────────────────────────

    private fun showMediaContent() {
        rvMediaGrid.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        permissionState.visibility = View.GONE
        hideShimmer()
    }

    private fun showEmptyState() {
        rvMediaGrid.visibility = View.GONE
        emptyState.visibility = View.VISIBLE
        permissionState.visibility = View.GONE
        hideShimmer()
        tvEmptyTitle.text = when (currentCategory) {
            Category.PHOTOS -> getString(R.string.no_photos_found)
            Category.VIDEOS -> getString(R.string.no_videos_found)
            Category.MUSIC -> getString(R.string.no_music_found)
            Category.DOCUMENTS -> getString(R.string.no_documents_found)
            Category.APPS -> getString(R.string.no_apps_found)
            Category.CONTACTS -> getString(R.string.no_contacts_found)
        }
    }

    private fun showPermissionState() {
        rvMediaGrid.visibility = View.GONE
        emptyState.visibility = View.GONE
        permissionState.visibility = View.VISIBLE
        hideShimmer()
        when (currentCategory) {
            Category.CONTACTS -> {
                tvPermissionTitle.text = getString(R.string.contacts_permission_needed)
                tvPermissionDesc.text = getString(R.string.contacts_permission_desc)
            }
            Category.DOCUMENTS -> {
                tvPermissionTitle.text = getString(R.string.files_permission_needed)
                tvPermissionDesc.text = getString(R.string.files_permission_desc)
            }
            else -> {
                tvPermissionTitle.text = getString(R.string.storage_permission_needed)
                tvPermissionDesc.text = getString(R.string.storage_permission_desc)
            }
        }
    }

    private fun showShimmerLoading() {
        rvMediaGrid.visibility = View.GONE
        emptyState.visibility = View.GONE
        permissionState.visibility = View.GONE
        val isGrid = currentCategory == Category.PHOTOS || currentCategory == Category.VIDEOS
        if (isGrid) {
            shimmerGrid.visibility = View.VISIBLE
            shimmerList.visibility = View.GONE
            shimmerGrid.startShimmer()
        } else {
            shimmerGrid.visibility = View.GONE
            shimmerList.visibility = View.VISIBLE
            shimmerList.startShimmer()
        }
    }

    private fun hideShimmer() {
        shimmerGrid.stopShimmer()
        shimmerList.stopShimmer()
        shimmerGrid.visibility = View.GONE
        shimmerList.visibility = View.GONE
    }

    // ─── RecyclerView Setup ─────────────────────────────────

    private fun setupAsGrid() {
        gridAdapter = MediaGridAdapter { count, totalSize -> updateBottomBar(count, totalSize) }
        rvMediaGrid.layoutManager = GridLayoutManager(this, 4)
        rvMediaGrid.adapter = gridAdapter
        listAdapter = null
    }

    private fun setupAsList(category: Category) {
        listAdapter = FileListAdapter(category) { count, totalSize -> updateBottomBar(count, totalSize) }
        rvMediaGrid.layoutManager = LinearLayoutManager(this)
        rvMediaGrid.adapter = listAdapter
        gridAdapter = null
    }

    // ─── Media Loading ──────────────────────────────────────

    private fun loadMedia(category: Category) {
        lifecycleScope.launch {
            when (category) {
                Category.PHOTOS, Category.VIDEOS -> {
                    setupAsGrid()
                    val uri = if (category == Category.PHOTOS)
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    val files = withContext(Dispatchers.IO) { queryMedia(uri) }
                    if (files.isEmpty()) showEmptyState() else { showMediaContent(); gridAdapter?.submitList(files) }
                }
                Category.MUSIC -> {
                    setupAsList(category)
                    val files = withContext(Dispatchers.IO) { queryMedia(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) }
                    if (files.isEmpty()) showEmptyState() else { showMediaContent(); listAdapter?.submitList(files) }
                }
                Category.DOCUMENTS -> {
                    setupAsList(category)
                    val files = withContext(Dispatchers.IO) { queryDocuments() }
                    if (files.isEmpty()) showEmptyState() else { showMediaContent(); listAdapter?.submitList(files) }
                }
                Category.APPS -> {
                    setupAsList(category)
                    val files = withContext(Dispatchers.IO) { queryInstalledApps() }
                    if (files.isEmpty()) showEmptyState() else { showMediaContent(); listAdapter?.submitList(files) }
                }
                Category.CONTACTS -> {
                    setupAsList(category)
                    val contacts = withContext(Dispatchers.IO) { queryContacts() }
                    if (contacts.isEmpty()) showEmptyState() else { showMediaContent(); listAdapter?.submitList(contacts) }
                }
            }
        }
    }

    private fun queryMedia(contentUri: Uri): List<MediaFile> {
        val files = mutableListOf<MediaFile>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        contentResolver.query(contentUri, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                files.add(MediaFile(
                    id = id,
                    uri = ContentUris.withAppendedId(contentUri, id),
                    name = cursor.getString(nameCol) ?: "Unknown",
                    size = cursor.getLong(sizeCol),
                    mimeType = cursor.getString(mimeCol) ?: "",
                    dateModified = cursor.getLong(dateCol)
                ))
            }
        }
        return files
    }

    private fun queryDocuments(): List<MediaFile> {
        val files = mutableListOf<MediaFile>()
        val contentUri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )
        val docMimeTypes = arrayOf(
            "application/pdf", "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain", "text/csv", "text/html", "text/xml",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
            "application/rtf", "application/json", "application/xml",
            "application/zip", "application/x-rar-compressed",
            "application/epub+zip", "application/x-7z-compressed"
        )
        val placeholders = docMimeTypes.joinToString(",") { "?" }
        contentResolver.query(
            contentUri, projection,
            "${MediaStore.Files.FileColumns.MIME_TYPE} IN ($placeholders)",
            docMimeTypes,
            "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                if (name == "Unknown" || name.startsWith(".")) continue
                files.add(MediaFile(
                    id = id,
                    uri = ContentUris.withAppendedId(contentUri, id),
                    name = name,
                    size = cursor.getLong(sizeCol),
                    mimeType = cursor.getString(mimeCol) ?: "",
                    dateModified = cursor.getLong(dateCol)
                ))
            }
        }
        return files
    }

    @Suppress("DEPRECATION")
    private fun queryInstalledApps(): List<MediaFile> {
        val files = mutableListOf<MediaFile>()
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
        } else {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        }
        for (appInfo in packages) {
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isSystemApp && !isUpdatedSystem) continue
            val sourceDir = appInfo.sourceDir ?: continue
            val file = java.io.File(sourceDir)
            if (!file.exists()) continue
            files.add(MediaFile(
                id = appInfo.packageName.hashCode().toLong(),
                uri = Uri.fromFile(file),
                name = packageManager.getApplicationLabel(appInfo).toString(),
                size = file.length(),
                mimeType = "application/vnd.android.package-archive",
                dateModified = file.lastModified() / 1000,
                packageName = appInfo.packageName
            ))
        }
        return files.sortedByDescending { it.dateModified }
    }

    private fun queryContacts(): List<MediaFile> {
        val contacts = mutableListOf<MediaFile>()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, projection, null, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val nameCol = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                contacts.add(MediaFile(
                    id = id,
                    uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id),
                    name = name,
                    size = 0,
                    mimeType = "text/vcard",
                    dateModified = 0
                ))
            }
        }
        return contacts
    }

    // ─── Bottom Bar ─────────────────────────────────────────

    private fun updateBottomBar(count: Int, totalSize: Long) {
        if (count > 0) {
            bottomBar.visibility = View.VISIBLE
            tvSelectedBadge.visibility = View.VISIBLE
            tvSelectedBadge.text = getString(R.string.n_selected, count)
            if (currentCategory == Category.CONTACTS) {
                tvSelectedItems.text = getString(R.string.contacts_selected, count)
                tvTotalSize.visibility = View.GONE
            } else {
                tvSelectedItems.text = getString(R.string.items_selected, count)
                tvTotalSize.text = getString(R.string.total_size, formatFileSize(totalSize))
                tvTotalSize.visibility = View.VISIBLE
            }
        } else {
            bottomBar.visibility = View.GONE
            tvSelectedBadge.visibility = View.GONE
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }

    companion object {
        const val EXTRA_CATEGORY = "extra_category"
    }
}