package com.example.beamshare.onboarding

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

        enableEdgeToEdge()
        setContentView(R.layout.activity_onboarding)

        val root = findViewById<View>(R.id.main)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }

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
