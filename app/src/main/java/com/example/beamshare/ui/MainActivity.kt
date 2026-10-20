package com.example.beamshare.ui

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.example.beamshare.R

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController

    private lateinit var navHome: View
    private lateinit var navTransfers: View
    private lateinit var navSettings: View

    private lateinit var indicatorHome: View
    private lateinit var indicatorTransfers: View
    private lateinit var indicatorSettings: View

    private lateinit var iconHome: ImageView
    private lateinit var iconTransfers: ImageView
    private lateinit var iconSettings: ImageView

    private lateinit var labelHome: TextView
    private lateinit var labelTransfers: TextView
    private lateinit var labelSettings: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

        setupEdgeToEdge()
        setupBottomNav()
        setupNavigation()
    }

    private fun setupEdgeToEdge() {
        val root = findViewById<View>(R.id.main)
        val bottomNav = findViewById<View>(R.id.bottomNavigation)
        val initialBottomPadding = bottomNav.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )

            // Apply status bar padding to root layout
            view.setPadding(
                view.paddingLeft,
                systemBars.top,
                view.paddingRight,
                0
            )

            // Apply navigation bar inset to bottomNav bottom padding
            bottomNav.setPadding(
                bottomNav.paddingLeft,
                bottomNav.paddingTop,
                bottomNav.paddingRight,
                initialBottomPadding + systemBars.bottom
            )

            insets
        }

        ViewCompat.requestApplyInsets(root)
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment

        navController = navHostFragment.navController

        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.homeFragment -> selectTab(Tab.HOME)
                R.id.transfersFragment -> selectTab(Tab.TRANSFERS)
                R.id.settingsFragment -> selectTab(Tab.SETTINGS)
            }
        }
    }

    private fun setupBottomNav() {
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

        navHome.setOnClickListener {
            navigateTo(R.id.homeFragment, Tab.HOME)
        }

        navTransfers.setOnClickListener {
            navigateTo(R.id.transfersFragment, Tab.TRANSFERS)
        }

        navSettings.setOnClickListener {
            navigateTo(R.id.settingsFragment, Tab.SETTINGS)
        }
    }

    private fun navigateTo(destinationId: Int, tab: Tab) {
        if (navController.currentDestination?.id != destinationId) {
            navController.navigate(destinationId)
        }
        selectTab(tab)
    }

    private fun selectTab(tab: Tab) {
        val primaryColor = ContextCompat.getColor(
            this, R.color.color_primary
        )
        val inactiveColor = ContextCompat.getColor(
            this, R.color.color_text_tertiary
        )

        indicatorHome.visibility = View.INVISIBLE
        indicatorTransfers.visibility = View.INVISIBLE
        indicatorSettings.visibility = View.INVISIBLE

        iconHome.setColorFilter(inactiveColor)
        iconTransfers.setColorFilter(inactiveColor)
        iconSettings.setColorFilter(inactiveColor)

        labelHome.setTextColor(inactiveColor)
        labelTransfers.setTextColor(inactiveColor)
        labelSettings.setTextColor(inactiveColor)

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
