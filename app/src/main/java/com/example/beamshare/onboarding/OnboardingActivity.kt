// com/example/beamshare/onboarding/OnboardingActivity.kt
package com.example.beamshare.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.beamshare.R
import com.example.beamshare.ui.MainActivity

class OnboardingActivity : AppCompatActivity() {

    private lateinit var viewPager: ViewPager2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Skip onboarding if already completed
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)) {
            navigateToMain()
            return
        }

        setContentView(R.layout.activity_onboarding)

        viewPager = findViewById(R.id.viewPager)
        viewPager.adapter = OnboardingAdapter(this)
        viewPager.isUserInputEnabled = false // Disable swipe — navigate via buttons
    }

    fun goToNextPage() {
        if (viewPager.currentItem < 2) {
            viewPager.currentItem += 1
        }
    }

    fun skipToComplete() {
        viewPager.currentItem = 2
    }

    fun finishOnboarding() {
        // Save onboarding as completed
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .apply()

        navigateToMain()
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        private const val PREFS_NAME = "beamshare_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
    }
}