package com.example.beamshare.ui.receive

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.beamshare.R
import com.example.beamshare.connection.ConnectionConfig
import com.example.beamshare.connection.QrCodeHelper
import com.example.beamshare.session.GroupSessionService
import com.example.beamshare.session.SessionActivity
import com.example.beamshare.session.SessionManager
import com.example.beamshare.session.SessionState
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class ReceiveWaitingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ReceiveWaiting"
    }

    private var navigatedAway = false
    private lateinit var ivQRCode: ImageView
    private lateinit var tvPairingCode: TextView
    private lateinit var tvDeviceName: TextView
    private lateinit var pulsingDot: View
    private lateinit var tvWaitingStatus: TextView
    private lateinit var btnStopReceiving: TextView

    private var pulseAnimator: ObjectAnimator? = null
    private lateinit var pairingCode: String

    private var isIpMode = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            Log.d(TAG, "Permissions result: $permissions, allGranted=$allGranted")
            if (allGranted) {
                generatePairingInfo()
                loadDeviceName()
                startGroupSession()
            } else {
                Log.e(TAG, "Permissions denied: ${permissions.filter { !it.value }.keys}")
                Toast.makeText(this, "Wi-Fi and location permissions are required to receive files", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_receive_waiting)

        initViews()
        setupEdgeToEdge()
        setupPulsingAnimation()

        if (hasRequiredPermissions()) {
            Log.d(TAG, "Permissions already granted")
            generatePairingInfo()
            loadDeviceName()
            startGroupSession()
        } else {
            Log.d(TAG, "Requesting permissions")
            requestRequiredPermissions()
        }
    }

    private fun initViews() {
        ivQRCode = findViewById(R.id.ivQRCode)
        tvPairingCode = findViewById(R.id.tvPairingCode)
        tvDeviceName = findViewById(R.id.tvDeviceName)
        pulsingDot = findViewById(R.id.pulsingDot)
        tvWaitingStatus = findViewById(R.id.tvWaitingStatus)
        btnStopReceiving = findViewById(R.id.btnStopReceiving)
        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        btnStopReceiving.setOnClickListener { showStopReceivingDialog() }
    }

    private fun setupEdgeToEdge() {
        val rootLayout = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, insets.top, 0, insets.bottom)
            windowInsets
        }
    }

    private fun setupPulsingAnimation() {
        pulseAnimator = ObjectAnimator.ofFloat(pulsingDot, "alpha", 1f, 0.3f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        Log.d(TAG, "hasPermissions: fineLocation=$fineLocation, nearbyDevices=$nearbyDevices")
        return fineLocation && nearbyDevices
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        Log.d(TAG, "Requesting permissions: $permissions")
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun generatePairingInfo() {
        Log.d(TAG, "generatePairingInfo: start")
        pairingCode = ConnectionConfig.generatePairingCode()
        val formatted = "${pairingCode.substring(0, 3)} ${pairingCode.substring(3)}"
        tvPairingCode.text = formatted
        Log.i(TAG, "═══ PAIRING CODE GENERATED ═══")
        Log.i(TAG, "  raw      = '$pairingCode'")
        Log.i(TAG, "  display  = '$formatted'")
        Log.i(TAG, "  length   = ${pairingCode.length}")

        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"

        val ownIp = getOwnIpAddress()
        Log.i(TAG, "═══ OWN IP ADDRESS ═══")
        Log.i(TAG, "  ip       = '$ownIp'")

        isIpMode = ownIp != null
        val config = ConnectionConfig(
            deviceName = deviceName,
            pairingCode = pairingCode,
            ipAddress = ownIp,
            mode = if (isIpMode) ConnectionConfig.MODE_IP else ConnectionConfig.MODE_WIFIDIRECT
        )
        val payload = config.toQrPayload()
        Log.i(TAG, "  mode     = '${config.mode}'")
        Log.i(TAG, "  payload  = '$payload'")

        try {
            val qrBitmap = QrCodeHelper.generateQrBitmap(payload, 512)
            Log.d(TAG, "QR bitmap generated: ${qrBitmap.width}x${qrBitmap.height}")
            ivQRCode.setImageBitmap(qrBitmap)
            ivQRCode.clearColorFilter()
            Log.d(TAG, "QR bitmap set on ImageView")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate QR bitmap", e)
        }
    }

    private fun getOwnIpAddress(): String? {
        // 1. Try WifiManager (works when connected to a Wi-Fi network as a client)
        try {
            @Suppress("DEPRECATION")
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            val ipInt = wifiManager.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                val bytes = ByteArray(4)
                bytes[0] = (ipInt and 0xFF).toByte()
                bytes[1] = (ipInt shr 8 and 0xFF).toByte()
                bytes[2] = (ipInt shr 16 and 0xFF).toByte()
                bytes[3] = (ipInt shr 24 and 0xFF).toByte()
                val ip = Inet4Address.getByAddress(bytes).hostAddress
                if (!ip.isNullOrEmpty() && ip != "0.0.0.0") return ip
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager IP lookup failed: ${e.message}")
        }

        // 2. Enumerate network interfaces — picks up hotspot (wlan0 in AP mode / ap0)
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val preferred = listOf("wlan0", "ap0", "wlan1", "eth0")
            val candidates = mutableMapOf<String, String>()
            for (iface in interfaces) {
                if (!iface.isUp || iface.isLoopback) continue
                for (addr in iface.inetAddresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        candidates[iface.name] = addr.hostAddress ?: continue
                    }
                }
            }
            Log.d(TAG, "Network interface candidates: $candidates")
            for (name in preferred) {
                candidates[name]?.let { return it }
            }
            return candidates.values.firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "NetworkInterface IP lookup failed: ${e.message}")
        }
        return null
    }

    private fun loadDeviceName() {
        val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}"
        tvDeviceName.text = deviceName
        Log.d(TAG, "Device name: $deviceName")
    }

    private fun startGroupSession() {
        Log.i(TAG, "═══ STARTING HOST SESSION ═══")
        Log.i(TAG, "  pairingCode = '$pairingCode'")
        Log.i(TAG, "  isIpMode    = $isIpMode")
        if (isIpMode) {
            GroupSessionService.hostIp(this, pairingCode)
        } else {
            GroupSessionService.host(this, pairingCode)
        }
        observeSessionState()
    }

    private fun observeSessionState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state ->
                    Log.d(TAG, "SessionState: $state")
                    when (state) {
                        is SessionState.Connected -> {
                            if (!navigatedAway) {
                                navigatedAway = true
                                Log.i(TAG, "═══ PEER CONNECTED ═══ peerName='${state.peerName}'")
                                Toast.makeText(
                                    this@ReceiveWaitingActivity,
                                    "Connected to ${state.peerName}!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                navigateToSessionActivity(state.peerName)
                            }
                        }
                        is SessionState.Ended -> {
                            if (!navigatedAway) {
                                Log.e(TAG, "Session ended: ${state.reason}")
                                Toast.makeText(
                                    this@ReceiveWaitingActivity,
                                    "Connection failed: ${state.reason}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun showStopReceivingDialog() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_stop_receiving, null)

        view.findViewById<TextView>(R.id.btnKeepWaiting).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<TextView>(R.id.btnStopReceiving).setOnClickListener {
            dialog.dismiss()
            Log.d(TAG, "User stopped receiving")
            stopService(Intent(this, GroupSessionService::class.java))
            finish()
        }

        dialog.setContentView(view)
        dialog.show()
    }

    /**
     * Navigate to SessionActivity — the same screen the sender lands on after
     * connection is established. Both sides are now in the same session screen
     * and can send files to each other (Zapya-style bidirectional).
     */
    private fun navigateToSessionActivity(peerName: String) {
        Log.i(TAG, "navigateToSessionActivity: peerName='$peerName'")
        val intent = Intent(this, SessionActivity::class.java).apply {
            putExtra(SessionActivity.EXTRA_REMOTE_DEVICE_NAME, peerName)
            putExtra(SessionActivity.EXTRA_LOCAL_DEVICE_NAME, "${Build.MANUFACTURER} ${Build.MODEL}")
            // Clear the stack so Back from SessionActivity goes to Home, not back to ReceiveWaiting
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy navigatedAway=$navigatedAway")
        pulseAnimator?.cancel()
        if (!navigatedAway) {
            Log.d(TAG, "onDestroy: no connection established, stopping GroupSessionService")
            stopService(Intent(this, GroupSessionService::class.java))
        }
    }
}