package com.example.beamshare.splash

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.beamshare.databinding.ActivitySplashBinding
import com.example.beamshare.language.SelectLanguageActivity
import com.example.beamshare.onboarding.OnboardingActivity
import com.example.beamshare.ui.MainActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val PREFS_NAME = "beamshare_prefs"
        private const val KEY_ONBOARDING = "onboarding_completed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.splashRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        startEntryAnimation()
    }

    private fun initViews() {
        binding.logoContainer.alpha = 0f
        binding.logoContainer.scaleX = 0.5f
        binding.logoContainer.scaleY = 0.5f
        binding.tvAppName.alpha = 0f
        binding.tvAppName.translationY = 30f
        binding.tvTagline.alpha = 0f
        binding.tvTagline.translationY = 20f
        binding.progressBar.alpha = 0f
    }

    private fun startEntryAnimation() {
        // Logo bounce in
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(binding.logoContainer, "scaleX", 0.5f, 1f).apply {
                    duration = 600; interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(binding.logoContainer, "scaleY", 0.5f, 1f).apply {
                    duration = 600; interpolator = DecelerateInterpolator()
                },
                ObjectAnimator.ofFloat(binding.logoContainer, "alpha", 0f, 1f).apply {
                    duration = 600
                }
            )
            start()
        }

        // App name
        handler.postDelayed({
            ObjectAnimator.ofFloat(binding.tvAppName, "alpha", 0f, 1f).apply {
                duration = 400; start()
            }
            ObjectAnimator.ofFloat(binding.tvAppName, "translationY", 30f, 0f).apply {
                duration = 400; interpolator = DecelerateInterpolator(); start()
            }
        }, 400)

        // Tagline
        handler.postDelayed({
            ObjectAnimator.ofFloat(binding.tvTagline, "alpha", 0f, 1f).apply {
                duration = 400; start()
            }
            ObjectAnimator.ofFloat(binding.tvTagline, "translationY", 20f, 0f).apply {
                duration = 400; interpolator = DecelerateInterpolator(); start()
            }
        }, 600)

        // Progress bar
        handler.postDelayed({
            ObjectAnimator.ofFloat(binding.progressBar, "alpha", 0f, 1f).apply {
                duration = 300; start()
            }
            startProgressAnimation()
        }, 900)
    }

    private fun startProgressAnimation() {
        ValueAnimator.ofInt(0, 100).apply {
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { binding.progressBar.progress = it.animatedValue as Int }
            start()
        }
        handler.postDelayed({ navigateToNext() }, 3500)
    }

    private fun navigateToNext() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val onboardingDone = prefs.getBoolean(KEY_ONBOARDING, false)
        val target = when {
            !prefs.contains("app_language") -> SelectLanguageActivity::class.java
            onboardingDone -> MainActivity::class.java
            else -> OnboardingActivity::class.java
        }
        startActivity(Intent(this, target))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}