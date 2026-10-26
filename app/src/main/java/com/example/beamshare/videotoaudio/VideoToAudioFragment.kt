package com.example.beamshare.videotoaudio

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentVa01SelectVideoBinding
import com.example.beamshare.videotoaudio.adapter.RecentVideoAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


class VideoToAudioFragment : Fragment() {

    private var _binding: FragmentVa01SelectVideoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VideoToAudioViewModel by activityViewModels()
    private lateinit var recentAdapter: RecentVideoAdapter

    // ── File picker launcher ──────────────────────────────────────────────────
    private val pickVideo = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        viewModel.selectVideoFromUri(requireContext(), uri)
        findNavController().navigate(R.id.action_videoToAudioFragment_to_formatQualityFragment)
    }

    // ── READ_MEDIA_VIDEO permission (Android 13+) ─────────────────────────────
    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.loadRecentVideos()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVa01SelectVideoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsets()
        setupStepPill(activeStep = 1)
        setupRecyclerView()
        setupClickListeners()
        observeVideos()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.homeFragment)
            }
        }

        // Request permission and load videos
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermission.launch(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            requestPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        binding.btnBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.homeFragment)
            }
        }
    }

    private fun setupStepPill(activeStep: Int) {
        // Step 1 active, rest inactive
        val dots = listOf(
            binding.stepsPill.tvStep1Dot,
            binding.stepsPill.tvStep2Dot,
            binding.stepsPill.tvStep3Dot,
            binding.stepsPill.tvStep4Dot
        )
        dots.forEachIndexed { index, dot ->
            if (index + 1 == activeStep) {
                dot.setBackgroundResource(R.drawable.bg_va_step_dot_active)
                dot.setTextColor(android.graphics.Color.WHITE)
            } else {
                dot.setBackgroundResource(R.drawable.bg_va_step_dot_inactive)
                dot.setTextColor(
                    androidx.core.content.ContextCompat.getColor(
                        requireContext(), R.color.va_fg2
                    )
                )
            }
        }
    }

    private fun setupRecyclerView() {
        recentAdapter = RecentVideoAdapter { video ->
            viewModel.selectVideo(video)
            findNavController().navigate(R.id.action_videoToAudioFragment_to_formatQualityFragment)
        }
        binding.rvRecentVideos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
            isNestedScrollingEnabled = false
        }
    }

    private fun setupClickListeners() {
        // Drop zone tap → open file picker
        binding.dropZone.setOnClickListener {
            pickVideo.launch("video/*")
        }
        // See all recent
        binding.tvSeeAll.setOnClickListener {
            pickVideo.launch("video/*")
        }
    }

    private fun observeVideos() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.videos.collectLatest { list ->
                recentAdapter.submitList(list)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            view.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }
}
