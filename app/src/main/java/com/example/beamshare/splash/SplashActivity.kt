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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import com.example.beamshare.R
import com.example.beamshare.onboarding.OnboardingActivity

class SplashActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvLoadingText: TextView
    private lateinit var logoContainer: View
    private lateinit var tvAppName: TextView
    private lateinit var tvTagline: TextView

    private val handler = Handler(Looper.getMainLooper())

    private val loadingTexts = arrayOf(
        "Initializing BeamShare...",
        "Setting up secure transfer...",
        "Preparing file system...",
        "Almost ready..."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initViews()
        startEntryAnimation()
    }

    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        tvLoadingText = findViewById(R.id.tvLoadingText)
        logoContainer = findViewById(R.id.logoContainer)
        tvAppName = findViewById(R.id.tvAppName)
        tvTagline = findViewById(R.id.tvTagline)

        // Start invisible for entry animation
        logoContainer.alpha = 0f
        logoContainer.scaleX = 0.5f
        logoContainer.scaleY = 0.5f
        tvAppName.alpha = 0f
        tvAppName.translationY = 30f
        tvTagline.alpha = 0f
        tvTagline.translationY = 20f
        progressBar.alpha = 0f
        tvLoadingText.alpha = 0f
    }

    private fun startEntryAnimation() {
        // Logo scale + fade in
        val logoScaleX = ObjectAnimator.ofFloat(logoContainer, "scaleX", 0.5f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
        }
        val logoScaleY = ObjectAnimator.ofFloat(logoContainer, "scaleY", 0.5f, 1f).apply {
            duration = 600
            interpolator = DecelerateInterpolator()
        }
        val logoAlpha = ObjectAnimator.ofFloat(logoContainer, "alpha", 0f, 1f).apply {
            duration = 600
        }

        AnimatorSet().apply {
            playTogether(logoScaleX, logoScaleY, logoAlpha)
            start()
        }

        // App name fade in + slide up
        handler.postDelayed({
            ObjectAnimator.ofFloat(tvAppName, "alpha", 0f, 1f).apply {
                duration = 400
                start()
            }
            ObjectAnimator.ofFloat(tvAppName, "translationY", 30f, 0f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                start()
            }
        }, 400)

        // Tagline fade in
        handler.postDelayed({
            ObjectAnimator.ofFloat(tvTagline, "alpha", 0f, 1f).apply {
                duration = 400
                start()
            }
            ObjectAnimator.ofFloat(tvTagline, "translationY", 20f, 0f).apply {
                duration = 400
                interpolator = DecelerateInterpolator()
                start()
            }
        }, 600)

        // Show progress bar + loading text
        handler.postDelayed({
            ObjectAnimator.ofFloat(progressBar, "alpha", 0f, 1f).setDuration(300).start()
            ObjectAnimator.ofFloat(tvLoadingText, "alpha", 0f, 1f).setDuration(300).start()
            startProgressAnimation()
        }, 900)
    }

    private fun startProgressAnimation() {
        ValueAnimator.ofInt(0, 100).apply {
            duration = 3000
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Int
                progressBar.progress = progress

                val textIndex = when {
                    progress < 25 -> 0
                    progress < 50 -> 1
                    progress < 80 -> 2
                    else -> 3
                }
                tvLoadingText.text = loadingTexts[textIndex]
            }
            start()
        }

        // Navigate to main screen after animation
        handler.postDelayed({
            navigateToMain()
        }, 3500)
    }

    private fun navigateToMain() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}