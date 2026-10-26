package com.example.beamshare.privatefolder

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.beamshare.R
import com.example.beamshare.databinding.ActivityUnlockPinBinding

class UnlockPinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUnlockPinBinding

    private var enteredPin = ""

    private val dots: List<View> by lazy {
        listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUnlockPinBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val root = binding.root
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { finish() }
        })

        binding.btnBack.setOnClickListener { finish() }

        val keys = listOf(
            binding.key1 to "1", binding.key2 to "2", binding.key3 to "3",
            binding.key4 to "4", binding.key5 to "5", binding.key6 to "6",
            binding.key7 to "7", binding.key8 to "8", binding.key9 to "9",
            binding.key0 to "0"
        )
        keys.forEach { (view, digit) -> view.setOnClickListener { onDigit(digit) } }
        binding.keyBackspace.setOnClickListener { onBackspace() }

        // Fingerprint — stub for future biometric implementation
        binding.btnFingerprint.setOnClickListener {
            binding.tvSubtitle.text = "Biometric unlock coming soon"
        }

        // Forgot PIN
        binding.tvForgotPin.setOnClickListener {
            startActivity(Intent(this, ForgotPinActivity::class.java))
        }

        updateDots(0)
    }

    private fun onDigit(digit: String) {
        if (enteredPin.length >= 4) return
        enteredPin += digit
        updateDots(enteredPin.length)

        if (enteredPin.length == 4) {
            val correct = PinManager.getPin(this)
            if (enteredPin == correct) {
                startActivity(Intent(this, PrivateFolderVaultActivity::class.java))
                finish()
            } else {
                shakeAndReset()
            }
        }
    }

    private fun onBackspace() {
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            updateDots(enteredPin.length)
            // Clear error message when user starts typing again
            binding.tvSubtitle.text = "Private Folder is locked"
        }
    }

    private fun updateDots(filled: Int) {
        dots.forEachIndexed { index, dot ->
            dot.setBackgroundResource(
                if (index < filled) com.example.beamshare.R.drawable.bg_pin_dot_filled
                else com.example.beamshare.R.drawable.bg_pin_dot_empty_dark
            )
        }
    }

    private fun shakeAndReset() {
        binding.tvSubtitle.text = "Incorrect PIN. Try again."
        enteredPin = ""
        updateDots(0)
        binding.layoutPinDots.animate()
            .translationX(12f).setDuration(60)
            .withEndAction {
                binding.layoutPinDots.animate()
                    .translationX(-12f).setDuration(60)
                    .withEndAction {
                        binding.layoutPinDots.animate()
                            .translationX(0f).setDuration(60).start()
                    }.start()
            }.start()
    }
}