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
import com.example.beamshare.databinding.FragmentVa03ConvertingBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConvertingFragment : Fragment() {

    private var _binding: FragmentVa03ConvertingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VideoToAudioViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVa03ConvertingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsets()
        setupStepPill(activeStep = 3)
        setupClickListeners()
        observeConversionState()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            viewModel.cancelConversion()
            if (!findNavController().popBackStack(R.id.videoToAudioFragment, false)) {
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

            // Footer gets nav bar inset only — NestedScrollView is constrained to footer top
            binding.footer.updatePadding(
                bottom = initialFooterBottom + systemBars.bottom
            )

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

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            viewModel.cancelConversion()
            if (!findNavController().popBackStack(R.id.videoToAudioFragment, false)) {
                findNavController().navigate(R.id.homeFragment)
            }
        }
        binding.btnCancel.setOnClickListener {
            viewModel.cancelConversion()
            if (!findNavController().popBackStack(R.id.videoToAudioFragment, false)) {
                findNavController().navigate(R.id.homeFragment)
            }
        }
    }

    private fun observeConversionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.conversionState.collectLatest { state ->
                when (state) {
                    is ConversionState.Converting -> {
                        binding.circularProgress.setProgress(state.progressPercent.toFloat())
                        binding.tvPercent.text = "${state.progressPercent}%"
                        binding.linearProgress.progress = state.progressPercent
                        val elapsedSec = state.elapsedMs / 1000
                        binding.tvElapsed.text = formatElapsed(elapsedSec)
                        binding.tvSpeed.text = state.speedLabel
                    }
                    is ConversionState.Done -> {
                        findNavController().navigate(R.id.action_convertingFragment_to_doneFragment)
                    }
                    is ConversionState.Error -> {
                        // Custom error message
                        binding.tvPercent.text = "!"
                        binding.tvStatusTitle.text = "Conversion Failed"
                        binding.tvStatusSub.text =
                            "Invalid or unsupported file format. Please select another video and try again."

                        // Change circular progress ring to red
                        binding.circularProgress.setProgressColor(
                            androidx.core.content.ContextCompat.getColor(
                                requireContext(),
                                R.color.perm_denied
                            )
                        )

                        // Change horizontal progress bar to red
                        binding.linearProgress.setIndicatorColor(
                            androidx.core.content.ContextCompat.getColor(
                                requireContext(),
                                R.color.perm_denied
                            )
                        )
                    }
                    is ConversionState.Cancelled -> Unit
                    else -> Unit
                }
            }
        }
    }

    private fun formatElapsed(seconds: Long): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}