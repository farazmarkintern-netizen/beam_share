package com.example.beamshare.documentscanner

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentDocumentScannerBinding
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File

class DocumentScannerFragment : Fragment() {

    private var _binding: FragmentDocumentScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var repo: ScannedDocRepository
    private lateinit var adapter: DocumentScannerAdapter

    // ── ML Kit scanner launcher ───────────────────────────────────────────────

    private val scannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val imageUri = scanResult?.pages?.firstOrNull()?.imageUri
            if (imageUri != null) {
                navigateToPreview(imageUri)
            } else {
                Toast.makeText(requireContext(), "No image captured", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDocumentScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repo = ScannedDocRepository.getInstance(requireContext())
        setupInsets()
        setupRecyclerView()
        setupClicks()
        loadDocs()
    }

    override fun onResume() {
        super.onResume()
        loadDocs()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupInsets() {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    private fun setupRecyclerView() {
        adapter = DocumentScannerAdapter { anchor, doc ->
            showPopupMenu(anchor, doc)
        }
        binding.rvScannedDocs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvScannedDocs.adapter = adapter
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        binding.btnScanNow.setOnClickListener { openScanner() }
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private fun loadDocs() {
        val docs = repo.getAll()
        adapter.submitList(docs)
        binding.layoutEmpty.visibility = if (docs.isEmpty()) View.VISIBLE else View.GONE
    }

    // ── Scanner launch ────────────────────────────────────────────────────────

    private fun openScanner() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setScannerMode(SCANNER_MODE_FULL)
            .setResultFormats(RESULT_FORMAT_JPEG)
            .build()

        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(requireActivity())
            .addOnSuccessListener { intentSender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), "Scanner failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigateToPreview(imageUri: Uri) {
        val action = DocumentScannerFragmentDirections
            .actionDocumentScannerFragmentToPreviewSaveFragment(imageUri.toString())
        findNavController().navigate(action)
    }

    // ── Popup menu — same pattern as SessionFileAdapter ───────────────────────

    private fun showPopupMenu(anchor: View, doc: ScannedDoc) {
        val popupView = LayoutInflater.from(requireContext())
            .inflate(R.layout.popup_session_file_menu, null)

        // Measure first to get real dimensions (same as SessionFileAdapter)
        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth  = popupView.measuredWidth
        val popupHeight = popupView.measuredHeight

        val popup = PopupWindow(
            popupView,
            popupWidth,
            popupHeight,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 16f
            isOutsideTouchable = true
        }

        popupView.findViewById<LinearLayout>(R.id.menuPreview).setOnClickListener {
            popup.dismiss()
            previewDoc(doc)
        }
        popupView.findViewById<LinearLayout>(R.id.menuRename).setOnClickListener {
            popup.dismiss()
            showRenameDialog(doc)
        }
        popupView.findViewById<LinearLayout>(R.id.menuShare).setOnClickListener {
            popup.dismiss()
            shareDoc(doc)
        }
        popupView.findViewById<LinearLayout>(R.id.menuDelete).setOnClickListener {
            popup.dismiss()
            deleteDoc(doc)
        }

        // Position: right edge of popup aligns with right edge of anchor
        val xOffset = -popupWidth
        val yOffset = 0
        popup.showAsDropDown(anchor, xOffset, yOffset, Gravity.START)
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private fun previewDoc(doc: ScannedDoc) {
        val file = File(doc.filePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.beamshare.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            val fallback = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                startActivity(Intent.createChooser(fallback, "Open document with"))
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "No app found to open PDF", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareDoc(doc: ScannedDoc) {
        val file = File(doc.filePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.beamshare.fileprovider", file        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share document"))
    }

    private fun showRenameDialog(doc: ScannedDoc) {
        val edit = EditText(requireContext()).apply {
            setText(doc.name)
            selectAll()
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Rename")
            .setView(edit)
            .setPositiveButton("Save") { _, _ ->
                val newName = edit.text.toString().trim()
                if (newName.isNotEmpty()) {
                    repo.rename(doc.id, newName)
                    loadDocs()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDoc(doc: ScannedDoc) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete")
            .setMessage("Delete \"${doc.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                runCatching { File(doc.filePath).delete() }
                repo.delete(doc.id)
                loadDocs()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}