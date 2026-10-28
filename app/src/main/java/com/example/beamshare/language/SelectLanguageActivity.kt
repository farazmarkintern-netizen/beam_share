package com.example.beamshare.language

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.beamshare.R
import com.example.beamshare.databinding.ActivitySelectLanguageBinding
import com.example.beamshare.onboarding.OnboardingActivity
import java.util.Locale

class SelectLanguageActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySelectLanguageBinding
    private lateinit var adapter: LanguageAdapter

    private val languages = mutableListOf(
        LanguageItem("English", R.drawable.flag_en, "en"),
        LanguageItem("Urdu",    R.drawable.flag_ur, "ur"),
        LanguageItem("Italian", R.drawable.flag_it, "it"),
        LanguageItem("German",  R.drawable.flag_de, "de"),
        LanguageItem("Korean",  R.drawable.flag_ko, "ko"),
        LanguageItem("Arabic",  R.drawable.flag_ar, "ar"),
        LanguageItem("French",  R.drawable.flag_fr, "fr"),
        LanguageItem("Spanish", R.drawable.flag_es, "es"),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySelectLanguageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Pre-select current app language (default to English if not matched)
        val currentLang = Locale.getDefault().language
        languages.find { it.code == currentLang }?.isSelected = true
        if (languages.none { it.isSelected }) {
            languages[0].isSelected = true
        }

        adapter = LanguageAdapter(languages) { position ->
            // Deselect all, select tapped
            languages.forEachIndexed { i, item ->
                item.isSelected = i == position
            }
            adapter.notifyDataSetChanged()
        }

        binding.rvLanguages.layoutManager = LinearLayoutManager(this)
        binding.rvLanguages.adapter = adapter

        binding.ivDone.setOnClickListener {
            val selected = languages.find { it.isSelected } ?: languages[0]
            applyLanguage(selected.code)
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
        }
    }

    private fun applyLanguage(code: String) {
        val locale = Locale(code)
        Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)

        // Save preference
        getSharedPreferences("beamshare_prefs", MODE_PRIVATE)
            .edit()
            .putString("app_language", code)
            .apply()
    }
}