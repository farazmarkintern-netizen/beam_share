package com.example.beamshare.documentscanner

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentPreviewSaveBinding
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import java.io.File
import java.io.FileOutputStream

class PreviewSaveFragment : Fragment() {

    private var _binding: FragmentPreviewSaveBinding? = null
    private val binding get() = _binding!!

    private val args: PreviewSaveFragmentArgs by navArgs()

    private val pages = mutableListOf<Bitmap>()
    private lateinit var pageAdapter: DocPageAdapter

    // ── Add Page launcher (ML Kit) ────────────────────────────────────────────
    private val addPageLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            scanResult?.pages?.forEach { page ->
                try {
                    val stream = requireContext().contentResolver.openInputStream(page.imageUri)
                    val bmp = android.graphics.BitmapFactory.decodeStream(stream)
                    stream?.close()
                    if (bmp != null) {
                        pages.add(bmp)
                        pageAdapter.notifyItemInserted(pages.size - 1)
                    }
                } catch (e: Exception) { /* ignore */ }
            }
            if (pages.isNotEmpty()) {
                binding.viewPagerDocs.setCurrentItem(pages.size - 1, true)
            }
            updateUI()
        }
    }

    private val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
        .setGalleryImportAllowed(true)
        .setPageLimit(10)
        .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
        .build()

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPreviewSaveBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInsets(view)
        loadInitialBitmap()
        setupViewPager()
        setupDragAndDrop()
        setupClickListeners()
        updateUI()
    }

    private fun setupInsets(view: View) {
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
    }

    // ── Load initial page ─────────────────────────────────────────────────────
    private fun loadInitialBitmap() {
        try {
            val uri = android.net.Uri.parse(args.imageUri)
            val stream = requireContext().contentResolver.openInputStream(uri)
            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
            stream?.close()
            if (bmp != null) pages.add(bmp)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error loading image", Toast.LENGTH_SHORT).show()
        }
    }

    // ── ViewPager2 ────────────────────────────────────────────────────────────
    private fun setupViewPager() {
        pageAdapter = DocPageAdapter(pages) { position -> showPageOptions(position) }
        binding.viewPagerDocs.adapter = pageAdapter
        binding.viewPagerDocs.registerOnPageChangeCallback(object :
            androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                updateCounter(position)
            }
        })
    }

    // ── Drag & Drop ───────────────────────────────────────────────────────────
    private fun setupDragAndDrop() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                rv: RecyclerView,
                vh: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                pageAdapter.movePage(vh.adapterPosition, target.adapterPosition)
                updateUI()
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {}
        }
        val rv = binding.viewPagerDocs.getChildAt(0) as? RecyclerView ?: return
        ItemTouchHelper(callback).attachToRecyclerView(rv)
    }

    // ── Click listeners ───────────────────────────────────────────────────────
    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { findNavController().popBackStack() }
        binding.btnRename.setOnClickListener { showRenameDialog() }
        binding.btnShare.setOnClickListener { sharePdf() }
        binding.btnAddPage.setOnClickListener { launchAddPage() }
        binding.btnSaveAsPdf.setOnClickListener { savePdf() }
    }

    // ── Page options — bottom sheet style ─────────────────────────────────────
    private fun showPageOptions(position: Int) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_page_options, null)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text =
            "Page ${position + 1} of ${pages.size}"

        // Hide move options if not applicable
        dialogView.findViewById<View>(R.id.optionMoveLeft).visibility =
            if (position > 0) View.VISIBLE else View.GONE
        dialogView.findViewById<View>(R.id.optionMoveRight).visibility =
            if (position < pages.size - 1) View.VISIBLE else View.GONE

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()

        dialogView.findViewById<View>(R.id.optionDelete).setOnClickListener {
            dialog.dismiss()
            if (pages.size <= 1) {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            } else {
                pageAdapter.deletePage(position)
                val newPos = if (position >= pages.size) pages.size - 1 else position
                binding.viewPagerDocs.setCurrentItem(newPos, false)
                updateUI()
            }
        }

        dialogView.findViewById<View>(R.id.optionMoveLeft).setOnClickListener {
            dialog.dismiss()
            pageAdapter.movePage(position, position - 1)
            binding.viewPagerDocs.setCurrentItem(position - 1, true)
            updateUI()
        }

        dialogView.findViewById<View>(R.id.optionMoveRight).setOnClickListener {
            dialog.dismiss()
            pageAdapter.movePage(position, position + 1)
            binding.viewPagerDocs.setCurrentItem(position + 1, true)
            updateUI()
        }

        dialogView.findViewById<View>(R.id.optionCancel).setOnClickListener {
            dialog.dismiss()
        }
    }
    // ── UI update ─────────────────────────────────────────────────────────────
    private fun updateUI() {
        val count = pages.size
        binding.tvPageCounter.visibility = if (count > 1) View.VISIBLE else View.GONE
        if (count > 1) {
            buildDots(count)
            updateDots(binding.viewPagerDocs.currentItem)
        } else {
            binding.dotsIndicator.removeAllViews()
        }
        updatePageLabel()
    }

    private fun updateCounter(position: Int) {
        binding.tvPageCounter.text = "${position + 1} / ${pages.size}"
    }

    private fun updatePageLabel() {
        val tv = binding.btnAddPage.getChildAt(1) as? TextView ?: return
        tv.text = if (pages.size > 1) "Pages (${pages.size})" else getString(R.string.doc_add_page)
    }

    // ── Dots ──────────────────────────────────────────────────────────────────
    private fun buildDots(count: Int) {
        binding.dotsIndicator.removeAllViews()
        val size = resources.getDimensionPixelSize(R.dimen.dot_size)
        val margin = resources.getDimensionPixelSize(R.dimen.dot_margin)
        repeat(count) {
            val dot = ImageView(requireContext())
            val params = ViewGroup.MarginLayoutParams(size, size)
            params.setMargins(margin, 0, margin, 0)
            dot.layoutParams = params
            dot.setImageResource(R.drawable.dot_inactive)
            binding.dotsIndicator.addView(dot)
        }
    }

    private fun updateDots(activeIndex: Int) {
        val container = binding.dotsIndicator
        for (i in 0 until container.childCount) {
            val dot = container.getChildAt(i) as? ImageView ?: continue
            dot.setImageResource(
                if (i == activeIndex) R.drawable.dot_active else R.drawable.dot_inactive
            )
        }
        updateCounter(activeIndex)
    }

    // ── Add page ──────────────────────────────────────────────────────────────
    private fun launchAddPage() {
        val scanner = GmsDocumentScanning.getClient(scannerOptions)
        scanner.getStartScanIntent(requireActivity())
            .addOnSuccessListener { intentSender ->
                addPageLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Scanner unavailable", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Save PDF ──────────────────────────────────────────────────────────────
    private fun savePdf() {
        if (pages.isEmpty()) {
            Toast.makeText(requireContext(), "No pages to save", Toast.LENGTH_SHORT).show()
            return
        }
        val docName = binding.tvFileName.text.toString()
            .ifBlank { "Document_${System.currentTimeMillis()}" }
        try {
            val file = createPdfFile(pages, docName)
            Toast.makeText(requireContext(), "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createPdfFile(bitmaps: List<Bitmap>, name: String): File {
        val pdf = PdfDocument()
        bitmaps.forEachIndexed { index, bmp ->
            val pageInfo = PdfDocument.PageInfo.Builder(bmp.width, bmp.height, index + 1).create()
            val page = pdf.startPage(pageInfo)
            page.canvas.drawBitmap(bmp, 0f, 0f, null)
            pdf.finishPage(page)
        }
        val dir = File(requireContext().filesDir, "scanned_docs").also { it.mkdirs() }
        val file = File(dir, "$name.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    // ── Share PDF ─────────────────────────────────────────────────────────────
    private fun sharePdf() {
        if (pages.isEmpty()) return
        try {
            val docName = binding.tvFileName.text.toString()
                .ifBlank { "Document_${System.currentTimeMillis()}" }
            val file = createPdfFile(pages, docName)
            val uri = FileProvider.getUriForFile(
                requireContext(), "${requireContext().packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF"))
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Share failed", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Rename dialog ─────────────────────────────────────────────────────────
    private fun showRenameDialog() {
        val input = EditText(requireContext()).apply {
            setText(binding.tvFileName.text)
        }
        AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.doc_rename))
            .setView(input)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) binding.tvFileName.text = name
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}