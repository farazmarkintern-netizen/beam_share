package com.example.beamshare.onboarding

import android.animation.ObjectAnimator
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentOnboardingShareBinding

class OnboardingShareFragment : Fragment() {

    private var _binding: FragmentOnboardingShareBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingShareBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setSpannableTitle()
        startEntryAnimation()
    }

    private fun setSpannableTitle() {
        val title = "Share Files"
        val spannable = SpannableString(title)
        spannable.setSpan(
            ForegroundColorSpan(
                ContextCompat.getColor(requireContext(), R.color.bs_blue)
            ),
            6, 11,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvTitle.text = spannable
    }

    private fun startEntryAnimation() {
        binding.bottomCard.alpha = 0f
        binding.bottomCard.translationY = 60f
        binding.tvTitle.alpha = 0f
        binding.tvTitle.translationY = 20f
        binding.tvDescription.alpha = 0f

        ObjectAnimator.ofFloat(binding.bottomCard, "alpha", 0f, 1f).apply {
            duration = 500; start()
        }
        ObjectAnimator.ofFloat(binding.bottomCard, "translationY", 60f, 0f).apply {
            duration = 500; interpolator = DecelerateInterpolator(); start()
        }
        binding.tvTitle.postDelayed({
            ObjectAnimator.ofFloat(binding.tvTitle, "alpha", 0f, 1f).apply { duration = 400; start() }
            ObjectAnimator.ofFloat(binding.tvTitle, "translationY", 20f, 0f).apply {
                duration = 400; interpolator = DecelerateInterpolator(); start()
            }
        }, 300)
        binding.tvDescription.postDelayed({
            ObjectAnimator.ofFloat(binding.tvDescription, "alpha", 0f, 1f).apply { duration = 400; start() }
        }, 450)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}