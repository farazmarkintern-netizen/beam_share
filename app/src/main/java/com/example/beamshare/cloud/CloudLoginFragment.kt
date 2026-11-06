package com.example.beamshare.cloud

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentCloudLoginBinding
import kotlinx.coroutines.launch

class CloudLoginFragment : Fragment() {

    private var _binding: FragmentCloudLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCloudLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Already logged in → skip login
        if (CloudAuthManager.isLoggedIn()) {
            navigateToCloud()
            return
        }

        binding.cardGoogleSignIn.setOnClickListener {
            startGoogleSignIn()
        }

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    private fun startGoogleSignIn() {
        binding.cardGoogleSignIn.isEnabled = false

        lifecycleScope.launch {
            CloudAuthManager.signInWithGoogle(
                context = requireContext(),
                onSuccess = { uid ->
                    navigateToCloud()
                },
                onError = { error ->
                    binding.cardGoogleSignIn.isEnabled = true
                    Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun navigateToCloud() {
        findNavController().navigate(
            R.id.action_cloudLoginFragment_to_cloudUploadingFragment
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}