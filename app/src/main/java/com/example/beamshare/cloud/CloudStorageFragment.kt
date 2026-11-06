package com.example.beamshare.cloud

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentCloudStorageBinding

class CloudStorageFragment : Fragment() {

    private var _binding: FragmentCloudStorageBinding? = null
    private val binding get() = _binding!!

    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                // File select ho gaya → upload pe jao
                navigateToUpload(uri)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCloudStorageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInsets()
        setupClickListeners()
    }

    private fun applyInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootCloudStorage) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        binding.btnUploadData.setOnClickListener { startFlow() }
        binding.cardUpload.setOnClickListener { startFlow() }
        binding.btnExplore.setOnClickListener { navigateToDirectory() }
        binding.cardExplore.setOnClickListener { navigateToDirectory() }
    }

    // Step 1: Cloud Permission Dialog
    private fun startFlow() {
        val dialog = CloudPermissionDialog()
        dialog.onAgree = {
            showMediaPermissionDialog()
        }
        dialog.show(parentFragmentManager, CloudPermissionDialog.TAG)
    }

    // Step 2: Media Permission Dialog
    private fun showMediaPermissionDialog() {
        val dialog = MediaPermissionDialog()
        dialog.onAllow = {
            openFilePicker()  // File manager kholo
        }
        dialog.onCancel = {}
        dialog.show(parentFragmentManager, MediaPermissionDialog.TAG)
    }

    // Step 3: File Picker open karo
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"  // Sab files
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
        }
        filePickerLauncher.launch(intent)
    }

    private fun navigateToDirectory() {
        findNavController().navigate(
            R.id.action_cloudStorageFragment_to_cloudDirectoryFragment
        )
    }

    // Step 4: Auth check → navigate
    private fun navigateToUpload(uri: Uri) {
        if (CloudAuthManager.isLoggedIn()) {
            // Logged in → upload fragment pe jao with file URI
            val action = CloudStorageFragmentDirections
                .actionCloudStorageFragmentToCloudUploadingFragment(uri.toString())
            findNavController().navigate(action)
        } else {
            // Login pehle
            findNavController().navigate(
                R.id.action_cloudStorageFragment_to_cloudLoginFragment
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}