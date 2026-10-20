package com.example.beamshare.ui.receive

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.beamshare.R
import com.example.beamshare.ui.MainActivity

class ConnectedSuccessActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REMOTE_DEVICE_NAME = "extra_remote_device_name"
        const val EXTRA_CONNECTION_METHOD = "extra_connection_method"
        const val EXTRA_EST_SPEED = "extra_est_speed"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_connected_success)

        setupEdgeToEdge()
        initViews()
    }

    private fun setupEdgeToEdge() {
        val rootLayout = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, insets.top, 0, insets.bottom)
            windowInsets
        }
    }

    private fun initViews() {
        val remoteDeviceName = intent.getStringExtra(EXTRA_REMOTE_DEVICE_NAME) ?: "Unknown device"
        val connectionMethod = intent.getStringExtra(EXTRA_CONNECTION_METHOD) ?: "Wi-Fi Direct"
        val estSpeed = intent.getStringExtra(EXTRA_EST_SPEED) ?: "~90 Mbps"

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }

        val tvConnectedDesc = findViewById<TextView>(R.id.tvConnectedDesc)
        tvConnectedDesc.text = getString(R.string.receive_connected_desc, remoteDeviceName)

        val tvConnectionMethod = findViewById<TextView>(R.id.tvConnectionMethod)
        tvConnectionMethod.text = connectionMethod

        val tvEstSpeed = findViewById<TextView>(R.id.tvEstSpeed)
        tvEstSpeed.text = estSpeed

        val btnOpenSession = findViewById<TextView>(R.id.btnOpenSession)
        btnOpenSession.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(intent)
            finish()
        }
    }
}