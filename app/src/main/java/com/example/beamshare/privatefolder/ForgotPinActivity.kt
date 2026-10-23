package com.example.beamshare.privatefolder

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.beamshare.databinding.ActivityForgotPinBinding

class ForgotPinActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPinBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPinBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // Show the question the user previously set
        val question = PinManager.getSecurityQuestion(this)
        if (question == null) {
            // No security question set — only option is destructive reset
            binding.tvQuestion.text = "No recovery question was set."
            binding.etAnswer.isEnabled = false
            binding.btnVerify.isEnabled = false
            binding.tvDestructiveReset.visibility = android.view.View.VISIBLE
        } else {
            binding.tvQuestion.text = question
            binding.tvDestructiveReset.visibility = android.view.View.GONE
        }

        binding.btnVerify.setOnClickListener {
            val answer = binding.etAnswer.text.toString()
            if (answer.isBlank()) {
                binding.etAnswer.error = "Enter your answer"
                return@setOnClickListener
            }
            if (PinManager.verifySecurityAnswer(this, answer)) {
                // Correct — clear only the PIN, files stay safe
                PinManager.clearPin(this)
                startActivity(
                    Intent(this, SetupPinActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                )
                finish()
            } else {
                binding.tvError.visibility = android.view.View.VISIBLE
                binding.tvError.text = "Wrong answer. Try again."
                binding.etAnswer.text?.clear()
            }
        }

        // Destructive reset — only shown when no security question exists
        binding.tvDestructiveReset.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Everything?")
                .setMessage("This will permanently delete all files in your Private Folder and reset your PIN.")
                .setPositiveButton("Delete & Reset") { _, _ ->
                    PrivateFolderManager.deleteAllFiles(this)
                    PinManager.clearAll(this)
                    startActivity(
                        Intent(this, SetupPinActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
