package com.example.beamshare.cloud

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentCloudDirectoryBinding

class CloudDirectoryFragment : Fragment() {

    private var _binding: FragmentCloudDirectoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCloudDirectoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStorageInfo()
        setupCategoryClicks()
        setupUploadButton()
    }

    private fun setupStorageInfo() {
        val totalGb = 200
        val usedGb  = 100
        val percent = ((usedGb.toFloat() / totalGb) * 100).toInt()

        binding.tvTotal.text = getString(R.string.cloud_dir_total)
        binding.tvUsed.text  = getString(R.string.cloud_dir_used)
        binding.progressStorage.progress = percent
        binding.tvProgressPercent.text   = "$percent%"
    }

    private fun setupCategoryClicks() {
        listOf(
            binding.categoryImages,
            binding.categoryVideos,
            binding.categoryAudios,
            binding.categoryContacts,
            binding.categoryDocuments
        ).forEach { it.setOnClickListener { onCategoryTapped() } }
    }

    private fun onCategoryTapped() {
        if (!CloudAuthManager.isLoggedIn()) {
            findNavController().navigate(R.id.action_cloudDirectoryFragment_to_cloudLoginFragment)
        }
        // TODO: navigate to file list for this category
    }

    private fun setupUploadButton() {
        binding.btnUploadToCloud.setOnClickListener {
            val dialog = CloudPermissionDialog()
            dialog.onAgree = {
                findNavController().navigate(
                    R.id.action_cloudDirectoryFragment_to_cloudStorageFragment
                )
            }
            dialog.show(parentFragmentManager, CloudPermissionDialog.TAG)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
