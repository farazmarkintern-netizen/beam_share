package com.example.beamshare.cloud

import android.net.Uri
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentCloudUploadingBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CloudUploadingFragment : Fragment() {

    private var _binding: FragmentCloudUploadingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCloudUploadingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets()
        setupTitle()
        setupClickListeners()

        // URI receive karo aur upload shuru karo
        val fileUriString = arguments?.getString("fileUri") ?: ""
        if (fileUriString.isNotEmpty()) {
            val uri = Uri.parse(fileUriString)
            startUpload(uri)
        }
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
    }

    private fun setupTitle() {
        val full = "Uploading to Cloud"
        val span = SpannableString(full)
        span.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.bs_dark)),
            0, 13,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        span.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.bs_blue)),
            13, full.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvUploadingTitle.text = span
    }

    private fun setupClickListeners() {
        binding.btnCancelUpload.setOnClickListener {
            showUploadComplete()
        }
    }

    private fun startUpload(uri: Uri) {
        val userId = CloudAuthManager.getUserId() ?: return

        lifecycleScope.launch {
            try {
                // URI se file copy karo temp mein
                val tempFile = withContext(Dispatchers.IO) {
                    uriToFile(uri)
                }

                val fileName = tempFile.name
                val totalMb = tempFile.length().toFloat() / (1024 * 1024)

                // R2 pe upload
                withContext(Dispatchers.IO) {
                    CloudflareR2.client.putObject(
                        CloudflareR2.BUCKET,
                        "users/$userId/$fileName",
                        tempFile
                    )
                }

                // Upload complete
                withContext(Dispatchers.Main) {
                    updateProgress(totalMb, totalMb)
                    tempFile.delete()
                    showUploadComplete()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    // Error show karo
                    binding.tvUploadSize.text = "Upload failed: ${e.message}"
                }
            }
        }
    }

    private fun uriToFile(uri: Uri): File {
        val contentResolver = requireContext().contentResolver
        val fileName = getFileName(uri)
        val tempFile = File(requireContext().cacheDir, fileName)
        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        }
        return tempFile
    }

    private fun getFileName(uri: Uri): String {
        var name = "file_${System.currentTimeMillis()}"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index != -1) {
                name = cursor.getString(index)
            }
        }
        return name
    }

    fun updateProgress(uploadedMb: Float, totalMb: Float) {
        val percent = ((uploadedMb / totalMb) * 100).toInt()
        binding.progressBarUpload.progress = percent
        binding.tvUploadSize.text = "%.2f MB / %.2f MB".format(uploadedMb, totalMb)
    }

    private fun showUploadComplete() {
        val dialog = UploadCompleteDialog()
        dialog.onDone = {
            findNavController().popBackStack()
        }
        dialog.show(parentFragmentManager, UploadCompleteDialog.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}