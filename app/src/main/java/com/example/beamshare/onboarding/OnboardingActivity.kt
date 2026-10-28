package com.example.beamshare.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.beamshare.R
import com.example.beamshare.databinding.ActivityOnboardingBinding
import com.example.beamshare.ui.MainActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)) {
            navigateToMain()
            return
        }

        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.viewPager.adapter = OnboardingAdapter(this)
//        binding.viewPager.isUserInputEnabled = false

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                binding.btnNext.text = if (position == 2) {
                    getString(R.string.onboarding_get_started)
                } else {
                    getString(R.string.onboarding_next)
                }
            }
        })

        binding.btnNext.setOnClickListener {
            if (binding.viewPager.currentItem < 2) {
                binding.viewPager.currentItem += 1
            } else {
                finishOnboarding()
            }
        }
    }

    private fun updateDots(position: Int) {
        val activeDp = (22 * resources.displayMetrics.density).toInt()
        val inactiveDp = (8 * resources.displayMetrics.density).toInt()

        val dots = listOf(binding.dot1, binding.dot2, binding.dot3)
        dots.forEachIndexed { index, dot ->
            val params = dot.layoutParams
            if (index == position) {
                params.width = activeDp
                dot.background = ContextCompat.getDrawable(this, R.drawable.dot_active)
            } else {
                params.width = inactiveDp
                dot.background = ContextCompat.getDrawable(this, R.drawable.dot_inactive)
            }
            dot.layoutParams = params
        }
    }

    fun goToNextPage() {
        if (binding.viewPager.currentItem < 2) {
            binding.viewPager.currentItem += 1
        } else {
            finishOnboarding()
        }
    }

    fun skipToComplete() {
        binding.viewPager.currentItem = 2
    }

    fun finishOnboarding() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .apply()
        navigateToMain()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, com.example.beamshare.permissions.PermissionsActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        private const val PREFS_NAME = "beamshare_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}