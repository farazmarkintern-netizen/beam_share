// com/example/beamshare/ui/MainActivity.kt
package com.example.beamshare.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.beamshare.R

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    // Tab views
    private lateinit var navHome: View
    private lateinit var navTransfers: View
    private lateinit var navSettings: View

    // Indicators
    private lateinit var indicatorHome: View
    private lateinit var indicatorTransfers: View
    private lateinit var indicatorSettings: View

    // Icons
    private lateinit var iconHome: ImageView
    private lateinit var iconTransfers: ImageView
    private lateinit var iconSettings: ImageView

    // Labels
    private lateinit var labelHome: TextView
    private lateinit var labelTransfers: TextView
    private lateinit var labelSettings: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupNavigation()
        setupBottomNav()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController
    }

    private fun setupBottomNav() {
        // Bind views
        navHome = findViewById(R.id.navHome)
        navTransfers = findViewById(R.id.navTransfers)
        navSettings = findViewById(R.id.navSettings)

        indicatorHome = findViewById(R.id.indicatorHome)
        indicatorTransfers = findViewById(R.id.indicatorTransfers)
        indicatorSettings = findViewById(R.id.indicatorSettings)

        iconHome = findViewById(R.id.iconHome)
        iconTransfers = findViewById(R.id.iconTransfers)
        iconSettings = findViewById(R.id.iconSettings)

        labelHome = findViewById(R.id.labelHome)
        labelTransfers = findViewById(R.id.labelTransfers)
        labelSettings = findViewById(R.id.labelSettings)

        // Click listeners
        navHome.setOnClickListener {
            if (navController.currentDestination?.id != R.id.homeFragment) {
                navController.navigate(R.id.homeFragment)
                selectTab(Tab.HOME)
            }
        }

        navTransfers.setOnClickListener {
            if (navController.currentDestination?.id != R.id.transfersFragment) {
                navController.navigate(R.id.transfersFragment)
                selectTab(Tab.TRANSFERS)
            }
        }

        navSettings.setOnClickListener {
            if (navController.currentDestination?.id != R.id.settingsFragment) {
                navController.navigate(R.id.settingsFragment)
                selectTab(Tab.SETTINGS)
            }
        }
    }

    private fun selectTab(tab: Tab) {
        val primaryColor = ContextCompat.getColor(this, R.color.color_primary)
        val inactiveColor = ContextCompat.getColor(this, R.color.color_text_tertiary)

        // Reset all tabs
        indicatorHome.visibility = View.INVISIBLE
        indicatorTransfers.visibility = View.INVISIBLE
        indicatorSettings.visibility = View.INVISIBLE

        iconHome.setColorFilter(inactiveColor)
        iconTransfers.setColorFilter(inactiveColor)
        iconSettings.setColorFilter(inactiveColor)

        labelHome.setTextColor(inactiveColor)
        labelTransfers.setTextColor(inactiveColor)
        labelSettings.setTextColor(inactiveColor)

        // Activate selected tab
        when (tab) {
            Tab.HOME -> {
                indicatorHome.visibility = View.VISIBLE
                iconHome.setColorFilter(primaryColor)
                labelHome.setTextColor(primaryColor)
            }
            Tab.TRANSFERS -> {
                indicatorTransfers.visibility = View.VISIBLE
                iconTransfers.setColorFilter(primaryColor)
                labelTransfers.setTextColor(primaryColor)
            }
            Tab.SETTINGS -> {
                indicatorSettings.visibility = View.VISIBLE
                iconSettings.setColorFilter(primaryColor)
                labelSettings.setTextColor(primaryColor)
            }
        }
    }

    private enum class Tab {
        HOME, TRANSFERS, SETTINGS
    }
}