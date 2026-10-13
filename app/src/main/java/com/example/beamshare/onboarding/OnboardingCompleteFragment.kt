package com.example.beamshare.onboarding

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.fragment.app.Fragment
import com.example.beamshare.databinding.FragmentOnboardingCompleteBinding

class OnboardingCompleteFragment : Fragment() {

    private var _binding: FragmentOnboardingCompleteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingCompleteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners()
        startEntryAnimation()
    }

    private fun setupClickListeners() {
        binding.btnStart.setOnClickListener {
            (activity as? OnboardingActivity)?.finishOnboarding()
        }
    }

    private fun startEntryAnimation() {
        // Set initial states
        binding.checkContainer.alpha = 0f
        binding.checkContainer.scaleX = 0.3f
        binding.checkContainer.scaleY = 0.3f
        binding.ivCheck.alpha = 0f
        binding.tvTitle.alpha = 0f
        binding.tvTitle.translationY = 20f
        binding.tvDescription.alpha = 0f
        binding.chipRow.alpha = 0f
        binding.chipRow.translationY = 15f
        binding.dotsContainer.alpha = 0f
        binding.btnStart.alpha = 0f
        binding.btnStart.translationY = 20f

        // Green circle bounces in
        val circleScaleX = ObjectAnimator.ofFloat(binding.checkContainer, "scaleX", 0.3f, 1f).apply {
            duration = 600
            interpolator = OvershootInterpolator(2f)
        }
        val circleScaleY = ObjectAnimator.ofFloat(binding.checkContainer, "scaleY", 0.3f, 1f).apply {
            duration = 600
            interpolator = OvershootInterpolator(2f)
        }
        val circleAlpha = ObjectAnimator.ofFloat(binding.checkContainer, "alpha", 0f, 1f).apply {
            duration = 400
        }

        AnimatorSet().apply {
            playTogether(circleScaleX, circleScaleY, circleAlpha)
            start()
        }

        // Checkmark fades in after circle
        binding.ivCheck.postDelayed({
            ObjectAnimator.ofFloat(binding.ivCheck, "alpha", 0f, 1f).setDuration(300).start()
        }, 400)

        // Title slides up
        binding.tvTitle.postDelayed({
            ObjectAnimator.ofFloat(binding.tvTitle, "alpha", 0f, 1f).setDuration(400).start()
            ObjectAnimator.ofFloat(binding.tvTitle, "translationY", 20f, 0f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                start()
            }
        }, 500)

        // Description
        binding.tvDescription.postDelayed({
            ObjectAnimator.ofFloat(binding.tvDescription, "alpha", 0f, 1f).setDuration(300).start()
        }, 650)

        // Chips slide up
        binding.chipRow.postDelayed({
            ObjectAnimator.ofFloat(binding.chipRow, "alpha", 0f, 1f).setDuration(400).start()
            ObjectAnimator.ofFloat(binding.chipRow, "translationY", 15f, 0f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                start()
            }
        }, 750)

        // Dots + button
        binding.dotsContainer.postDelayed({
            ObjectAnimator.ofFloat(binding.dotsContainer, "alpha", 0f, 1f).setDuration(300).start()
            ObjectAnimator.ofFloat(binding.btnStart, "alpha", 0f, 1f).setDuration(400).start()
            ObjectAnimator.ofFloat(binding.btnStart, "translationY", 20f, 0f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                start()
            }
        }, 900)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}