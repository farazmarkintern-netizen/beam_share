package com.example.beamshare.ui.discovery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.connection.ConnectionConfig
import com.example.beamshare.connection.ConnectionState
import com.example.beamshare.connection.QrCodeHelper
import com.example.beamshare.connection.WifiDirectManager
import com.example.beamshare.model.NearbyDevice
import com.example.beamshare.session.GroupSessionService
import com.example.beamshare.session.SessionActivity
import com.example.beamshare.session.SessionManager
import com.example.beamshare.session.SessionState
import com.example.beamshare.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DeviceDiscoveryActivity : AppCompatActivity() {

    private lateinit var tabScanQR: TextView
    private lateinit var tabEnterCode: TextView
    private lateinit var scannerContainer: View
    private lateinit var enterCodeSection: LinearLayout
    private lateinit var bottomSection: LinearLayout
    private lateinit var rvNearbyDevices: RecyclerView
    private lateinit var searchingState: LinearLayout
    private lateinit var tvCodeError: TextView
    private lateinit var codeErrorRow: LinearLayout
    private lateinit var btnConnect: TextView
    private lateinit var btnTryAgain: TextView
    private lateinit var enterCodeNormalHeader: LinearLayout
    private lateinit var enterCodeErrorHeader: LinearLayout
    private lateinit var codeInputs: List<EditText>
    private lateinit var previewView: PreviewView

    private lateinit var deviceAdapter: NearbyDeviceAdapter
    private var isQRTabActive = true

    private lateinit var wifiDirectManager: WifiDirectManager
    private var qrScanned = false

    private var currentCodeIndex = 0

    private var pendingDeviceName = ""
    private var pendingPairingCode = ""

    private var selectedFileUris: ArrayList<Uri> = arrayListOf()
    private var selectedFileNames: ArrayList<String> = arrayListOf()
    private var selectedFileSizes: LongArray = longArrayOf()

    // Guard against navigating twice if state fires multiple times
    private var navigatedToSession = false

    // The coroutine that waits for SessionManager.Connected state
    private var sessionWatchJob: Job? = null

    companion object {
        private const val TAG = "DeviceDiscovery"
        const val EXTRA_FILE_URIS = "file_uris"
        const val EXTRA_FILE_NAMES = "file_names"
        const val EXTRA_FILE_SIZES = "file_sizes"
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startQrScanning()
        else {
            switchToEnterCode()
            Toast.makeText(this, R.string.camera_denied_message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_device_discovery)

        extractIntentExtras()
        initViews()
        setupEdgeToEdge()
        setupTabs()
        setupNearbyDevices()
        setupNumpad()
        initWifiDirect()
        checkCameraAndStartScanning()
    }

    private fun extractIntentExtras() {
        selectedFileUris = intent.getParcelableArrayListExtra(EXTRA_FILE_URIS) ?: arrayListOf()
        selectedFileNames = intent.getStringArrayListExtra(EXTRA_FILE_NAMES) ?: arrayListOf()
        selectedFileSizes = intent.getLongArrayExtra(EXTRA_FILE_SIZES) ?: longArrayOf()
    }

    private fun initViews() {
        tabScanQR = findViewById(R.id.tabScanQR)
        tabEnterCode = findViewById(R.id.tabEnterCode)
        scannerContainer = findViewById(R.id.scannerContainer)
        enterCodeSection = findViewById(R.id.enterCodeSection)
        bottomSection = findViewById(R.id.bottomSection)
        rvNearbyDevices = findViewById(R.id.rvNearbyDevices)
        searchingState = findViewById(R.id.searchingState)
        tvCodeError = findViewById(R.id.tvCodeError)
        codeErrorRow = findViewById(R.id.codeErrorRow)
        btnConnect = findViewById(R.id.btnConnect)
        btnTryAgain = findViewById(R.id.btnTryAgain)
        enterCodeNormalHeader = findViewById(R.id.enterCodeNormalHeader)
        enterCodeErrorHeader = findViewById(R.id.enterCodeErrorHeader)
        previewView = findViewById(R.id.cameraPreview)

        codeInputs = listOf(
            findViewById(R.id.etCode1), findViewById(R.id.etCode2),
            findViewById(R.id.etCode3), findViewById(R.id.etCode4),
            findViewById(R.id.etCode5), findViewById(R.id.etCode6)
        )

        findViewById<ImageView>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<ImageView>(R.id.btnClose).setOnClickListener { finish() }

        btnConnect.setOnClickListener {
            val code = codeInputs.joinToString("") { it.text.toString() }
            if (code.length == 6) connectWithCode(code)
        }

        btnTryAgain.setOnClickListener {
            resetCodeError()
        }
    }

    private fun setupEdgeToEdge() {
        val rootLayout = findViewById<View>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, insets.top, 0, insets.bottom)
            windowInsets
        }
    }

    private fun setupTabs() {
        tabScanQR.setOnClickListener { if (!isQRTabActive) switchToScanQR() }
        tabEnterCode.setOnClickListener { if (isQRTabActive) switchToEnterCode() }
    }

    private fun switchToScanQR() {
        isQRTabActive = true
        tabScanQR.setBackgroundResource(R.drawable.bg_tab_selected)
        tabScanQR.setTextColor(getColor(android.R.color.white))
        tabScanQR.setTypeface(null, android.graphics.Typeface.BOLD)
        tabEnterCode.setBackgroundResource(R.drawable.bg_tab_unselected)
        tabEnterCode.setTextColor(getColor(R.color.color_text_secondary))
        scannerContainer.isVisible = true
        bottomSection.isVisible = true
        enterCodeSection.isGone = true
        checkCameraAndStartScanning()
    }

    private fun switchToEnterCode() {
        isQRTabActive = false
        tabEnterCode.setBackgroundResource(R.drawable.bg_tab_selected)
        tabEnterCode.setTextColor(getColor(android.R.color.white))
        tabEnterCode.setTypeface(null, android.graphics.Typeface.BOLD)
        tabScanQR.setBackgroundResource(R.drawable.bg_tab_unselected)
        tabScanQR.setTextColor(getColor(R.color.color_text_secondary))
        scannerContainer.isGone = true
        bottomSection.isGone = true
        enterCodeSection.isVisible = true
        QrCodeHelper.stopScanning(previewView)
        currentCodeIndex = 0
        highlightCurrentBox()
    }

    private fun setupNearbyDevices() {
        deviceAdapter = NearbyDeviceAdapter { device -> connectToNearbyDevice(device) }
        rvNearbyDevices.layoutManager = LinearLayoutManager(this)
        rvNearbyDevices.adapter = deviceAdapter
    }

    private fun setupNumpad() {
        val digitKeys = listOf(
            R.id.key0, R.id.key1, R.id.key2, R.id.key3, R.id.key4,
            R.id.key5, R.id.key6, R.id.key7, R.id.key8, R.id.key9
        )
        for (keyId in digitKeys) {
            findViewById<TextView>(keyId).setOnClickListener { v ->
                onNumpadDigit((v as TextView).text.toString())
            }
        }
        findViewById<ImageView>(R.id.keyBackspace).setOnClickListener {
            onNumpadBackspace()
        }
    }

    private fun onNumpadDigit(digit: String) {
        if (currentCodeIndex < codeInputs.size) {
            codeInputs[currentCodeIndex].setText(digit)
            currentCodeIndex++
            highlightCurrentBox()

            if (currentCodeIndex == codeInputs.size) {
                btnConnect.alpha = 1f
            }
        }
    }

    private fun onNumpadBackspace() {
        if (currentCodeIndex > 0) {
            currentCodeIndex--
            codeInputs[currentCodeIndex].setText("")
            highlightCurrentBox()
        }
    }

    private fun highlightCurrentBox() {
        for (i in codeInputs.indices) {
            if (i == currentCodeIndex) {
                codeInputs[i].setBackgroundResource(R.drawable.bg_code_input_focused)
            } else {
                codeInputs[i].setBackgroundResource(R.drawable.bg_code_input)
            }
        }
    }

    private fun initWifiDirect() {
        wifiDirectManager = WifiDirectManager(applicationContext)
        wifiDirectManager.initialize()
        wifiDirectManager.discoverPeers()

        lifecycleScope.launch {
            wifiDirectManager.peers.collect { peerList ->
                val nearbyDevices = peerList.map { device ->
                    NearbyDevice(
                        id = device.deviceAddress,
                        name = device.deviceName ?: "Unknown Device",
                        signalStrength = mapSignalStatus(device.status)
                    )
                }
                if (nearbyDevices.isNotEmpty()) {
                    searchingState.isGone = true
                    rvNearbyDevices.isVisible = true
                    deviceAdapter.submitList(nearbyDevices)
                }
            }
        }

        lifecycleScope.launch {
            wifiDirectManager.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected -> onDeviceConnected(state.groupOwnerAddress, state.isGroupOwner)
                    is ConnectionState.Error -> Toast.makeText(this@DeviceDiscoveryActivity, state.message, Toast.LENGTH_LONG).show()
                    else -> {}
                }
            }
        }
    }

    private fun mapSignalStatus(status: Int): String = when (status) {
        WifiP2pDevice.CONNECTED -> "Connected"
        WifiP2pDevice.INVITED -> "Invited"
        WifiP2pDevice.AVAILABLE -> "Available"
        else -> "Unavailable"
    }

    private fun checkCameraAndStartScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            startQrScanning()
        else
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun startQrScanning() {
        qrScanned = false
        QrCodeHelper.startScanning(
            previewView = previewView,
            lifecycleOwner = this,
            onResult = { config ->
                if (!qrScanned) {
                    qrScanned = true
                    runOnUiThread { onQrCodeScanned(config) }
                }
            },
            onError = { message -> runOnUiThread { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() } }
        )
    }

    private fun onQrCodeScanned(config: ConnectionConfig) {
        QrCodeHelper.stopScanning(previewView)
        Toast.makeText(this, "Found: ${config.deviceName}", Toast.LENGTH_SHORT).show()
        pendingDeviceName = config.deviceName
        pendingPairingCode = config.pairingCode

        Log.i(TAG, "═══ QR SCANNED ═══")
        Log.i(TAG, "  deviceName  = '${config.deviceName}'")
        Log.i(TAG, "  pairingCode = '${config.pairingCode}'")
        Log.i(TAG, "  mode        = '${config.mode}'")
        Log.i(TAG, "  ipAddress   = '${config.ipAddress}'")
        Log.i(TAG, "  port        = ${config.port}")

        if (config.mode == ConnectionConfig.MODE_IP && config.ipAddress != null) {
            Log.d(TAG, "QR mode=IP, starting joinIp to ${config.ipAddress}:${config.port} code='${config.pairingCode}'")
            startSessionAndWait {
                GroupSessionService.joinIp(this, config.pairingCode, config.ipAddress, config.port)
            }
            return
        }

        // Wi-Fi Direct mode — find peer by name and connect
        val matchingPeer = wifiDirectManager.peers.value.find { it.deviceName == config.deviceName }
        if (matchingPeer != null) {
            pendingDeviceName = matchingPeer.deviceName ?: config.deviceName
            wifiDirectManager.connectToDevice(matchingPeer)
        } else {
            wifiDirectManager.discoverPeers()
            Toast.makeText(this, "Searching for ${config.deviceName}…", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                wifiDirectManager.peers.collect { peers ->
                    val peer = peers.find { it.deviceName == config.deviceName }
                    if (peer != null) {
                        pendingDeviceName = peer.deviceName ?: config.deviceName
                        wifiDirectManager.connectToDevice(peer)
                        return@collect
                    }
                }
            }
        }
    }

    private fun connectToNearbyDevice(device: NearbyDevice) {
        val updatedList = deviceAdapter.currentList.map {
            if (it.id == device.id) it.copy(isConnecting = true) else it
        }
        deviceAdapter.submitList(updatedList)

        val p2pDevice = wifiDirectManager.peers.value.find { it.deviceAddress == device.id }
        if (p2pDevice != null) {
            pendingDeviceName = p2pDevice.deviceName ?: device.name
            wifiDirectManager.connectToDevice(p2pDevice)
        } else {
            Toast.makeText(this, "Device no longer available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectWithCode(code: String) {
        pendingPairingCode = code
        val peers = wifiDirectManager.peers.value
        if (peers.isNotEmpty()) {
            pendingDeviceName = peers.first().deviceName ?: "Unknown device"
            wifiDirectManager.connectToDevice(peers.first())
        } else {
            showCodeError()
        }
    }

    private fun onDeviceConnected(groupOwnerAddress: String, isGroupOwner: Boolean) {
        // Wi-Fi Direct group formed — use TCP direct to the group owner
        Log.i(TAG, "═══ WIFI DIRECT CONNECTED ═══ ownerAddress=$groupOwnerAddress isOwner=$isGroupOwner")
        startSessionAndWait {
            GroupSessionService.joinIp(this, pendingPairingCode, groupOwnerAddress)
        }
    }

    /**
     * Start the GroupSessionService via [startAction], then watch SessionManager
     * until the session reaches Connected (or Ended), then navigate.
     *
     * This replaces the old navigateToSession() which fired immediately without
     * waiting for the TCP handshake to succeed. Now the user sees a "Connecting…"
     * toast and the screen stays until we know the result.
     */
    private fun startSessionAndWait(startAction: () -> Unit) {
        if (navigatedToSession) return
        Log.i(TAG, "startSessionAndWait: launching service + watching SessionManager")
        try {
            startAction()
            Log.i(TAG, "startSessionAndWait: startForegroundService intent sent OK")
        } catch (e: Exception) {
            Log.e(TAG, "startSessionAndWait: FAILED to start GroupSessionService: ${e.javaClass.simpleName}: ${e.message}", e)
            Toast.makeText(this, "Service start failed: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Connecting…", Toast.LENGTH_SHORT).show()

        sessionWatchJob?.cancel()
        sessionWatchJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state ->
                    Log.d(TAG, "SessionManager state → $state")
                    when (state) {
                        is SessionState.Connected -> {
                            if (!navigatedToSession) {
                                navigatedToSession = true
                                Log.i(TAG, "═══ CONNECTION CONFIRMED — navigating to SessionActivity ═══ peer='${state.peerName}'")
                                Toast.makeText(
                                    this@DeviceDiscoveryActivity,
                                    "Connected to ${state.peerName}!",
                                    Toast.LENGTH_SHORT
                                ).show()
                                navigateToSession(state.peerName)
                            }
                        }
                        is SessionState.Ended -> {
                            if (!navigatedToSession) {
                                Log.e(TAG, "Session ended before connected: ${state.reason}")
                                Toast.makeText(
                                    this@DeviceDiscoveryActivity,
                                    "Connection failed: ${state.reason}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        else -> { /* Hosting / Joining / Reconnecting — still waiting */ }
                    }
                }
            }
        }
    }

    private fun showCodeError() {
        enterCodeNormalHeader.isGone = true
        enterCodeErrorHeader.isVisible = true
        btnConnect.isGone = true
        btnTryAgain.isVisible = true
        codeErrorRow.isVisible = true
        tvCodeError.text = getString(R.string.connect_wrong_code_tries, 2)
        codeInputs.forEach { it.setBackgroundResource(R.drawable.bg_code_input_error) }
    }

    private fun resetCodeError() {
        enterCodeNormalHeader.isVisible = true
        enterCodeErrorHeader.isGone = true
        btnConnect.isVisible = true
        btnTryAgain.isGone = true
        codeErrorRow.isGone = true
        currentCodeIndex = 0
        codeInputs.forEach {
            it.setText("")
            it.setBackgroundResource(R.drawable.bg_code_input)
        }
        highlightCurrentBox()
    }

    /**
     * Navigate to SessionActivity once we have confirmed the session is Connected.
     * The connection banner on HomeFragment also becomes visible automatically
     * because SessionManager.state is already Connected by the time we arrive.
     */
    private fun navigateToSession(peerName: String) {
        if (selectedFileUris.isNotEmpty()) {
            val sessionIntent = Intent(this, SessionActivity::class.java).apply {
                putExtra(SessionActivity.EXTRA_REMOTE_DEVICE_NAME, peerName)
                putExtra(SessionActivity.EXTRA_LOCAL_DEVICE_NAME, "${Build.MANUFACTURER} ${Build.MODEL}")
                putParcelableArrayListExtra(SessionActivity.EXTRA_PENDING_URIS, selectedFileUris)
            }
            startActivity(sessionIntent)
        } else {
            val sessionIntent = Intent(this, SessionActivity::class.java).apply {
                putExtra(SessionActivity.EXTRA_REMOTE_DEVICE_NAME, peerName)
                putExtra(SessionActivity.EXTRA_LOCAL_DEVICE_NAME, "${Build.MANUFACTURER} ${Build.MODEL}")
            }
            startActivity(sessionIntent)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        sessionWatchJob?.cancel()
        wifiDirectManager.stopDiscovery()
        wifiDirectManager.release()
    }
}