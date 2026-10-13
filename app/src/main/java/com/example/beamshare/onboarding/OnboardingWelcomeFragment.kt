package com.example.beamshare.onboarding

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.fragment.app.Fragment
import com.example.beamshare.databinding.FragmentOnboardingWelcomeBinding

class OnboardingWelcomeFragment : Fragment() {

    private var _binding: FragmentOnboardingWelcomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingWelcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        startEntryAnimation()
    }

    private fun setupClickListeners() {
        binding.btnNext.setOnClickListener {
            (activity as? OnboardingActivity)?.goToNextPage()
        }

        binding.tvSkip.setOnClickListener {
            (activity as? OnboardingActivity)?.skipToComplete()
        }
    }

    private fun startEntryAnimation() {
        // Set initial states
        binding.ivIllustration.alpha = 0f
        binding.tvTitle.alpha = 0f
        binding.tvTitle.translationY = 20f
        binding.tvDescription.alpha = 0f
        binding.btnNext.alpha = 0f
        binding.tvSkip.alpha = 0f

        // Illustration fades in
        ObjectAnimator.ofFloat(binding.ivIllustration, "alpha", 0f, 1f).apply {
            duration = 600
            start()
        }

        // Title slides up
        binding.tvTitle.postDelayed({
            ObjectAnimator.ofFloat(binding.tvTitle, "alpha", 0f, 1f).setDuration(400).start()
            ObjectAnimator.ofFloat(binding.tvTitle, "translationY", 20f, 0f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                start()
            }
        }, 300)

        // Description
        binding.tvDescription.postDelayed({
            ObjectAnimator.ofFloat(binding.tvDescription, "alpha", 0f, 1f).setDuration(400).start()
        }, 500)

        // Button + Skip
        binding.btnNext.postDelayed({
            ObjectAnimator.ofFloat(binding.btnNext, "alpha", 0f, 1f).setDuration(300).start()
            ObjectAnimator.ofFloat(binding.tvSkip, "alpha", 0f, 1f).setDuration(300).start()
        }, 650)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}