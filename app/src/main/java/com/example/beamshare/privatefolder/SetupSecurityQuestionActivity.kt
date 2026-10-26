package com.example.beamshare.privatefolder

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.beamshare.R
import com.example.beamshare.databinding.ActivitySetupSecurityQuestionBinding

class SetupSecurityQuestionActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupSecurityQuestionBinding

    private val questions = listOf(
        "What was the name of your first pet?",
        "What city were you born in?",
        "What is your mother's maiden name?",
        "What was the name of your first school?",
        "What is your favourite childhood movie?",
        "What street did you grow up on?"
    )

    private var selectedQuestion = questions[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupSecurityQuestionBinding.inflate(layoutInflater)
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

        // Populate spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, questions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerQuestion.adapter = adapter

        binding.spinnerQuestion.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
                selectedQuestion = questions[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        binding.btnSave.setOnClickListener {
            val answer = binding.etAnswer.text.toString().trim()
            if (answer.isEmpty()) {
                binding.etAnswer.error = "Please enter an answer"
                return@setOnClickListener
            }
            PinManager.saveSecurityQuestion(this, selectedQuestion, answer)
            // Go to vault
            startActivity(Intent(this, PrivateFolderVaultActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }

        binding.tvSkip.setOnClickListener {
            // Allow skipping — fallback will be destructive reset
            startActivity(Intent(this, PrivateFolderVaultActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
            finish()
        }
    }
}
