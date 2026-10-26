package com.example.beamshare.privatefolder

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.beamshare.R
import com.example.beamshare.databinding.ActivitySetupPinBinding

class SetupPinActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupPinBinding

    private var firstPin = ""
    private var confirmPin = ""
    private var isConfirming = false

    private val dots: List<View> by lazy {
        listOf(binding.dot1, binding.dot2, binding.dot3, binding.dot4)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupPinBinding.inflate(layoutInflater)
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

        binding.btnBack.setOnClickListener {
            if (isConfirming) {
                isConfirming = false
                firstPin = ""
                confirmPin = ""
                updateDots(0)
                updateTitle("Create a 4-digit PIN", "You'll use this to unlock\nyour private folder")
            } else {
                finish()
            }
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isConfirming) {
                    isConfirming = false
                    firstPin = ""
                    confirmPin = ""
                    updateDots(0)
                    updateTitle("Create a 4-digit PIN", "You'll use this to unlock\nyour private folder")
                } else {
                    finish()
                }
            }
        })

        val keys = listOf(
            binding.key1 to "1", binding.key2 to "2", binding.key3 to "3",
            binding.key4 to "4", binding.key5 to "5", binding.key6 to "6",
            binding.key7 to "7", binding.key8 to "8", binding.key9 to "9",
            binding.key0 to "0"
        )

        keys.forEach { (view, digit) ->
            view.setOnClickListener { onDigit(digit) }
        }

        binding.keyBackspace.setOnClickListener { onBackspace() }
    }

    private fun onDigit(digit: String) {
        if (!isConfirming) {
            if (firstPin.length >= 4) return
            firstPin += digit
            updateDots(firstPin.length)
            if (firstPin.length == 4) {
                isConfirming = true
                confirmPin = ""
                updateDots(0)
                binding.root.postDelayed({
                    updateTitle("Confirm your PIN", "Enter the same PIN again")
                }, 200)
            }
        } else {
            if (confirmPin.length >= 4) return
            confirmPin += digit
            updateDots(confirmPin.length)
            if (confirmPin.length == 4) {
                if (confirmPin == firstPin) {
                    PinManager.savePin(this, firstPin)
                    startActivity(Intent(this, SetupSecurityQuestionActivity::class.java))
                    finish()
                } else {
                    firstPin = ""
                    confirmPin = ""
                    isConfirming = false
                    updateDots(0)
                    updateTitle("Create a 4-digit PIN", "PINs did not match. Create PIN again.")
                    shakeDotsAndReset()
                }
            }
        }
    }

    private fun onBackspace() {
        if (!isConfirming) {
            if (firstPin.isNotEmpty()) {
                firstPin = firstPin.dropLast(1)
                updateDots(firstPin.length)
                }
        } else {
            if (confirmPin.isNotEmpty()) {
                confirmPin = confirmPin.dropLast(1)
                updateDots(confirmPin.length)
            }
        }
    }

    private fun updateDots(filled: Int) {
        dots.forEachIndexed { index, dot ->
            dot.setBackgroundResource(
                if (index < filled) R.drawable.bg_pin_dot_filled
                else R.drawable.bg_pin_dot_empty
            )
        }
    }

    private fun updateTitle(title: String, subtitle: String) {
        binding.tvTitle.text = title
        binding.tvSubtitle.text = subtitle
    }

    private fun shakeDotsAndReset() {
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
