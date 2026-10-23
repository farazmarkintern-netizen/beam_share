package com.example.beamshare.privatefolder

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import com.example.beamshare.R
import com.example.beamshare.databinding.ActivityPrivateFolderVaultBinding
import java.io.File
import java.io.FileOutputStream

class PrivateFolderVaultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPrivateFolderVaultBinding
    private lateinit var adapter: VaultFileAdapter

    private var allFiles = mutableListOf<File>()
    private lateinit var currentDirectory: File

    // ── Lock-on-background ────────────────────────────────────────────────────
    private var backgroundedAt = 0L
    private val LOCK_TIMEOUT_MS = 15_000L // 15 seconds

    // ── Import launchers ──────────────────────────────────────────────────────
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) showImportConfirmDialog(uris)
    }

    private val importGalleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) showImportConfirmDialog(uris)
    }

    // Holds URIs pending delete after RecoverableSecurityException grant
    private var pendingDeleteUris = listOf<Uri>()

    // Android 11+ delete permission request
    private val deletePermissionLauncher: ActivityResultLauncher<IntentSenderRequest> =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK && pendingDeleteUris.isNotEmpty()) {
                // Permission granted — now actually delete
                pendingDeleteUris.forEach { uri ->
                    try { contentResolver.delete(uri, null, null) } catch (_: Exception) {}
                }
                pendingDeleteUris = emptyList()
                Toast.makeText(this, "Originals deleted from device", Toast.LENGTH_SHORT).show()
            }
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityPrivateFolderVaultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply insets: top bar gets status bar padding, RecyclerView gets nav bar padding
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Pad the top bar for status bar height
            binding.root.setPadding(0, systemBars.top, 0, 0)
            // Pad RecyclerView bottom for nav bar (already has 80sdp internal padding)
            val extraBottomPadding = (80 * resources.displayMetrics.density).toInt()
            binding.rvFiles.setPadding(
                binding.rvFiles.paddingLeft,
                binding.rvFiles.paddingTop,
                binding.rvFiles.paddingRight,
                systemBars.bottom + extraBottomPadding
            )
            insets
        }

        currentDirectory = PrivateFolderManager.getVaultDir(this)

        setupRecyclerView()
        setupClickListeners()
        setupSearch()
        refreshFiles()
    }

    override fun onResume() {
        super.onResume()
        // If app was backgrounded for 10+ seconds, lock and go back to PIN
        if (backgroundedAt > 0 && System.currentTimeMillis() - backgroundedAt >= LOCK_TIMEOUT_MS) {
            backgroundedAt = 0L
            finish()
            return
        }
        backgroundedAt = 0L
        refreshFiles()
    }

    override fun onPause() {
        super.onPause()
        backgroundedAt = System.currentTimeMillis()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        onBackNavigation()
    }

    private fun onBackNavigation() {
        val vaultRoot = PrivateFolderManager.getVaultDir(this)
        if (currentDirectory != vaultRoot && currentDirectory.parentFile != null) {
            currentDirectory = currentDirectory.parentFile!!
            refreshFiles()
        } else {
            super.onBackPressed()
        }
    }

    // ── Setup ─────────────────────────────────────────────────────────────────
    private fun setupRecyclerView() {
        adapter = VaultFileAdapter(
            context = this,
            files = allFiles,
            onFileClick = ::onFileClick,
            onDeleteFile = ::confirmDeleteFile,
            onShareFile = ::shareFile,
            onMoveFile = ::showMoveDialog
        )
        binding.rvFiles.layoutManager = GridLayoutManager(this, 2)
        binding.rvFiles.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { onBackNavigation() }

        // Settings gear
        binding.btnSettings.setOnClickListener { showSettingsMenu() }

        // Empty state buttons
        binding.btnImportFromFiles.setOnClickListener { launchFilePicker() }
        binding.btnImportFromGallery.setOnClickListener { launchGalleryPicker() }
        binding.btnImportFab.setOnClickListener { showImportSheet() }

        // Grid state FAB
        binding.btnImportFabGrid.setOnClickListener { showImportSheet() }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterFiles(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ── File state ────────────────────────────────────────────────────────────
    private fun refreshFiles() {
        allFiles = (currentDirectory.listFiles()?.toMutableList() ?: mutableListOf()).apply {
            sortWith(compareBy({ !it.isDirectory }, { it.name }))
        }
        filterFiles(binding.etSearch.text?.toString() ?: "")
    }

    private fun filterFiles(query: String) {
        val filtered = if (query.isBlank()) allFiles
        else allFiles.filter { it.name.contains(query, ignoreCase = true) }

        adapter.updateFiles(filtered)
        updateEmptyState(allFiles.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvFiles.visibility = View.GONE
            binding.layoutFab.visibility = View.GONE
            binding.layoutSearch.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvFiles.visibility = View.VISIBLE
            binding.layoutFab.visibility = View.VISIBLE
            binding.layoutSearch.visibility = View.VISIBLE
        }
    }

    // ── Import ────────────────────────────────────────────────────────────────
    private fun showImportSheet() {
        val items = arrayOf("Import from Files", "Import from Gallery", "Create Folder")
        AlertDialog.Builder(this)
            .setTitle("Add to Vault")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> launchFilePicker()
                    1 -> launchGalleryPicker()
                    2 -> showCreateFolderDialog()
                }
            }
            .show()
    }

    private fun launchFilePicker() {
        importFileLauncher.launch("*/*")
    }

    private fun launchGalleryPicker() {
        importGalleryLauncher.launch("image/*")
    }

    private fun showImportConfirmDialog(uris: List<Uri>) {
        val fileCount = uris.size
        val label = if (fileCount == 1) "1 file" else "$fileCount files"

        AlertDialog.Builder(this)
            .setTitle("Import $label")
            .setMessage("Import $label into ${if (currentDirectory == PrivateFolderManager.getVaultDir(this)) "Private Folder" else currentDirectory.name}?")
            .setPositiveButton("Import") { _, _ ->
                importFiles(uris)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importFiles(uris: List<Uri>) {
        var successCount = 0
        val copiedUris = mutableListOf<Uri>() // track which URIs were successfully copied

        uris.forEach { uri ->
            val name = getFileNameFromUri(uri) ?: "file_${System.currentTimeMillis()}"
            val dest = File(currentDirectory, name)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                }
                successCount++
                copiedUris.add(uri)
            } catch (_: Exception) {
                // Skip individual failures
            }
        }

        refreshFiles()
        val msg = if (successCount == uris.size) "Imported $successCount file(s)"
        else "Imported $successCount of ${uris.size} file(s)"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

        if (copiedUris.isNotEmpty()) {
            showDeleteOriginalsDialog(copiedUris)
        }
    }

    private fun showDeleteOriginalsDialog(uris: List<Uri>) {
        val count = uris.size
        val label = if (count == 1) "this original file" else "these $count original files"
        AlertDialog.Builder(this)
            .setTitle("Delete Originals?")
            .setMessage("Do you want to delete $label from your device now that they are safely in your Private Folder?")
            .setPositiveButton("Delete") { _, _ ->
                deleteOriginalsFromDevice(uris)
            }
            .setNegativeButton("Keep", null)
            .show()
    }

    private fun deleteOriginalsFromDevice(uris: List<Uri>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                pendingDeleteUris = uris
                deletePermissionLauncher.launch(
                    IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                )
                return
            } catch (_: Exception) {
                // Fall back to per-URI deletion / SAF / RecoverableSecurityException
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val needsGrant = mutableListOf<Uri>()
            var firstRse: RecoverableSecurityException? = null

            uris.forEach { uri ->
                try {
                    if (DocumentsContract.isDocumentUri(this, uri)) {
                        DocumentsContract.deleteDocument(contentResolver, uri)
                    } else {
                        contentResolver.delete(uri, null, null)
                    }
                } catch (e: RecoverableSecurityException) {
                    needsGrant.add(uri)
                    if (firstRse == null) firstRse = e
                } catch (_: Exception) {
                    try {
                        contentResolver.delete(uri, null, null)
                    } catch (rse: RecoverableSecurityException) {
                        needsGrant.add(uri)
                        if (firstRse == null) firstRse = rse
                    } catch (_: Exception) {}
                }
            }

            val intentSender = firstRse?.userAction?.actionIntent?.intentSender
            if (intentSender != null && needsGrant.isNotEmpty()) {
                pendingDeleteUris = needsGrant
                try {
                    deletePermissionLauncher.launch(
                        IntentSenderRequest.Builder(intentSender).build()
                    )
                } catch (_: Exception) {
                    Toast.makeText(this, "Could not request delete permission", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Originals deleted from device", Toast.LENGTH_SHORT).show()
            }
        } else {
            // Android 9 and below: direct delete
            var deleted = 0
            uris.forEach { uri ->
                try {
                    if (DocumentsContract.isDocumentUri(this, uri)) {
                        if (DocumentsContract.deleteDocument(contentResolver, uri)) deleted++
                    } else {
                        if (contentResolver.delete(uri, null, null) > 0) deleted++
                    }
                } catch (_: Exception) {}
            }
            if (deleted > 0) Toast.makeText(this, "$deleted original(s) deleted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileNameFromUri(uri: Uri): String? {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx)
            }
        }
        return uri.lastPathSegment
    }

    // ── Create Folder ─────────────────────────────────────────────────────────
    private fun showCreateFolderDialog() {
        val input = EditText(this).apply {
            hint = "Folder name"
            inputType = InputType.TYPE_CLASS_TEXT
            setPadding(48, 24, 48, 8)
        }
        AlertDialog.Builder(this)
            .setTitle("New Folder")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) createFolder(name)
                else Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createFolder(name: String) {
        val folder = File(currentDirectory, name)
        if (folder.exists()) {
            Toast.makeText(this, "A folder with this name already exists", Toast.LENGTH_SHORT).show()
            return
        }
        folder.mkdirs()
        currentDirectory = folder // Open the new folder immediately!
        refreshFiles()
        Toast.makeText(this, "Folder \"$name\" created and opened", Toast.LENGTH_SHORT).show()
    }

    // ── File actions ──────────────────────────────────────────────────────────
    private fun onFileClick(file: File) {
        if (file.isDirectory) {
            currentDirectory = file
            refreshFiles()
            return
        }
        // Open file via FileProvider
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val mime = contentResolver.getType(uri) ?: "*/*"
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open this file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showMoveDialog(file: File) {
        val vaultDir = PrivateFolderManager.getVaultDir(this)
        val folders = mutableListOf<File>()
        folders.add(vaultDir) // Root
        vaultDir.walkTopDown().maxDepth(3).forEach { f ->
            if (f.isDirectory && f != vaultDir && !folders.contains(f)) {
                folders.add(f)
            }
        }

        val folderNames = folders.map { if (it == vaultDir) "Root Vault" else it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Move \"${file.name}\" to...")
            .setItems(folderNames) { _, which ->
                val destFolder = folders[which]
                moveFileToFolder(file, destFolder)
            }
            .show()
    }

    private fun moveFileToFolder(file: File, destFolder: File) {
        if (file.parentFile == destFolder) {
            Toast.makeText(this, "File is already in this folder", Toast.LENGTH_SHORT).show()
            return
        }
        val destFile = File(destFolder, file.name)
        if (destFile.exists()) {
            Toast.makeText(this, "A file with this name already exists in destination", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val success = file.renameTo(destFile)
            if (success) {
                Toast.makeText(this, "Moved successfully", Toast.LENGTH_SHORT).show()
                refreshFiles()
            } else {
                file.copyTo(destFile, overwrite = false)
                file.delete()
                Toast.makeText(this, "Moved successfully", Toast.LENGTH_SHORT).show()
                refreshFiles()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Failed to move file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDeleteFile(file: File) {
        AlertDialog.Builder(this)
            .setTitle("Delete File")
            .setMessage("Delete \"${file.name}\" permanently from Private Folder?")
            .setPositiveButton("Delete") { _, _ ->
                file.delete()
                refreshFiles()
                Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = contentResolver.getType(uri) ?: "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share via"
            ))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share this file", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Settings ──────────────────────────────────────────────────────────────
    private fun showSettingsMenu() {
        val items = arrayOf("Change PIN", "Delete All Data")
        AlertDialog.Builder(this)
            .setTitle("Settings")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> navigateToChangePin()
                    1 -> confirmDeleteAllData()
                }
            }
            .show()
    }

    private fun navigateToChangePin() {
        // Navigate to SetupPinActivity — existing PIN will be cleared and re-entered
        AlertDialog.Builder(this)
            .setTitle("Change PIN")
            .setMessage("You will be asked to set a new PIN. Your files will remain safe.")
            .setPositiveButton("Continue") { _, _ ->
                PinManager.clearPin(this)
                startActivity(Intent(this, SetupPinActivity::class.java))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAllData() {
        // Two-step confirmation for destructive action
        AlertDialog.Builder(this)
            .setTitle("Delete All Data")
            .setMessage("This will permanently delete ALL files in your Private Folder and reset your PIN.\n\nThis cannot be undone.")
            .setPositiveButton("Delete Everything") { _, _ ->
                secondConfirmDeleteAll()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun secondConfirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle("Are you sure?")
            .setMessage("All files will be gone forever.")
            .setPositiveButton("Yes, Delete All") { _, _ ->
                PrivateFolderManager.deleteAllFiles(this)
                PinManager.clearAll(this)
                Toast.makeText(this, "All data deleted", Toast.LENGTH_SHORT).show()
                // Send to PIN setup so user can start fresh
                startActivity(Intent(this, SetupPinActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}