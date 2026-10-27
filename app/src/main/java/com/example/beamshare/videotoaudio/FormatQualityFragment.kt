package com.example.beamshare.videotoaudio

import androidx.activity.addCallback
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentVa02FormatQualityBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * VA02 — Format & Quality screen.
 * User picks bitrate (64 / 128 / 192 / 320 kbps) and reviews the selected video.
 * Navigates to VA03 (ConvertingFragment) on Convert tap.
 */
class FormatQualityFragment : Fragment() {

    private var _binding: FragmentVa02FormatQualityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VideoToAudioViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVa02FormatQualityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        applyWindowInsets()
        setupStepPill(activeStep = 2)
        setupBitrateChips()
        setupClickListeners()
        observeState()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.homeFragment)
            }
        }
    }

    private fun applyWindowInsets() {
        val initialFooterBottom = binding.footer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Top padding on root (for status bar)
            view.updatePadding(top = systemBars.top)

            // Footer gets nav bar inset — that's it
            binding.footer.updatePadding(
                bottom = initialFooterBottom + systemBars.bottom
            )

            // NestedScrollView is constrained to footer top, so NO extra padding needed
            // Removing scrollContent padding was causing the extra empty space

            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private fun setupStepPill(activeStep: Int) {
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
            } else if (index + 1 < activeStep) {
                dot.setBackgroundResource(R.drawable.bg_va_step_dot_done)
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

    private fun setupBitrateChips() {
        val chipMap = mapOf(
            binding.chip64   to 64,
            binding.chip128  to 128,
            binding.chip192  to 192,
            binding.chip256  to 256,
            binding.chip320  to 320
        )

        chipMap.forEach { (chip, kbps) ->
            chip.setOnClickListener {
                viewModel.setBitrate(kbps)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.bitrateKbps.collectLatest { selected ->
                chipMap.forEach { (chip, kbps) ->
                    chip.isChecked = (kbps == selected)
                }
                binding.tvEstSize.text = viewModel.getEstimatedSize()
            }
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            if (!findNavController().popBackStack()) {
                findNavController().navigate(R.id.homeFragment)
            }
        }

        binding.btnStartConverting.setOnClickListener {
            viewModel.startConversion()
            findNavController().navigate(
                R.id.action_formatQualityFragment_to_convertingFragment
            )
        }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedVideo.collectLatest { video ->
                if (video != null) {
                    binding.tvSelectedFileName.text = video.displayName
                    binding.tvSelectedFileMeta.text =
                        "${video.formattedDuration}  •  ${video.formattedSize}"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}