package com.example.beamshare.connection

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.WpsInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WifiDirectManager(private val context: Context) {

    companion object {
        private const val TAG = "WifiDirectManager"
    }

    private var manager: WifiP2pManager? = null
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _peers = MutableStateFlow<List<WifiP2pDevice>>(emptyList())
    val peers: StateFlow<List<WifiP2pDevice>> = _peers.asStateFlow()

    private val _connectionInfo = MutableStateFlow<WifiP2pInfo?>(null)
    val connectionInfo: StateFlow<WifiP2pInfo?> = _connectionInfo.asStateFlow()

    fun initialize() {
        Log.d(TAG, "initialize: getting WifiP2pManager")
        manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
        channel = manager?.initialize(context, Looper.getMainLooper(), null)

        if (manager == null || channel == null) {
            Log.e(TAG, "initialize: WifiP2pManager or channel is null — Wi-Fi Direct not supported")
            _connectionState.value = ConnectionState.Error("Wi-Fi Direct is not supported on this device")
            return
        }

        Log.d(TAG, "initialize: success, registering receiver")
        registerReceiver()
    }

    private fun registerReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                Log.d(TAG, "BroadcastReceiver: action=${intent.action}")
                when (intent.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(
                            WifiP2pManager.EXTRA_WIFI_STATE,
                            WifiP2pManager.WIFI_P2P_STATE_DISABLED
                        )
                        val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                        Log.d(TAG, "Wi-Fi P2P state changed: ${if (enabled) "ENABLED" else "DISABLED"}")
                        if (!enabled) {
                            _connectionState.value = ConnectionState.Error("Wi-Fi Direct is disabled")
                        }
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        Log.d(TAG, "Peers changed — requesting peer list")
                        requestPeers()
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        Log.d(TAG, "Connection changed — requesting connection info")
                        requestConnectionInfo()
                    }
                    WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                        Log.d(TAG, "This device changed")
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, intentFilter)
        }
        Log.d(TAG, "BroadcastReceiver registered")
    }

    fun discoverPeers(retryOnBusy: Boolean = true) {
        if (!hasPermissions()) {
            _connectionState.value = ConnectionState.Error("Missing Wi-Fi/location permissions")
            return
        }

        Log.d(TAG, "discoverPeers: starting discovery")
        _connectionState.value = ConnectionState.Discovering
        try {
            manager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "discoverPeers: discovery initiated successfully")
                }
                override fun onFailure(reason: Int) {
                    if (reason == WifiP2pManager.BUSY && retryOnBusy) {
                        Log.w(TAG, "discoverPeers: BUSY — retrying in 1s")
                        Handler(Looper.getMainLooper()).postDelayed({
                            discoverPeers(retryOnBusy = false)
                        }, 1000)
                    } else {
                        val msg = when (reason) {
                            WifiP2pManager.P2P_UNSUPPORTED -> "Wi-Fi Direct not supported"
                            WifiP2pManager.BUSY -> "System is busy, try again"
                            WifiP2pManager.ERROR -> "Internal error"
                            else -> "Discovery failed (code $reason)"
                        }
                        Log.e(TAG, "discoverPeers: FAILED — $msg (code $reason)")
                        _connectionState.value = ConnectionState.Error(msg)
                    }
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "discoverPeers: SecurityException", e)
            _connectionState.value = ConnectionState.Error("Permission denied")
        }
    }

    fun stopDiscovery() {
        Log.d(TAG, "stopDiscovery")
        try {
            manager?.stopPeerDiscovery(channel, null)
        } catch (_: Exception) {}
    }

    private fun requestPeers() {
        try {
            manager?.requestPeers(channel) { peerList ->
                val deviceList = peerList?.deviceList?.toList() ?: emptyList()
                Log.d(TAG, "requestPeers: found ${deviceList.size} peers")
                deviceList.forEachIndexed { i, device ->
                    Log.d(TAG, "  peer[$i]: name='${device.deviceName}' addr=${device.deviceAddress} status=${device.status}")
                }
                _peers.value = deviceList
                if (deviceList.isNotEmpty()) {
                    _connectionState.value = ConnectionState.DevicesFound(deviceList)
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "requestPeers: SecurityException", e)
        }
    }

    private fun requestConnectionInfo() {
        manager?.requestConnectionInfo(channel) { info ->
            Log.d(TAG, "requestConnectionInfo: groupFormed=${info?.groupFormed}, isGroupOwner=${info?.isGroupOwner}, ownerAddr=${info?.groupOwnerAddress?.hostAddress}")
            _connectionInfo.value = info
            if (info?.groupFormed == true && info.groupOwnerAddress != null) {
                val addr = info.groupOwnerAddress.hostAddress ?: ""
                Log.d(TAG, "Group formed! isGroupOwner=${info.isGroupOwner}, address=$addr")
                _connectionState.value = ConnectionState.Connected(
                    groupOwnerAddress = addr,
                    isGroupOwner = info.isGroupOwner
                )
            } else {
                Log.d(TAG, "requestConnectionInfo: no group formed yet")
            }
        }
    }

    fun connectToDevice(device: WifiP2pDevice) {
        if (!hasPermissions()) {
            Log.e(TAG, "connectToDevice: missing permissions")
            _connectionState.value = ConnectionState.Error("Missing permissions")
            return
        }

        Log.d(TAG, "connectToDevice: ${device.deviceName} (${device.deviceAddress})")

        // FIX: use WPS Push-Button Connect (PBC) so no PIN / password dialog appears.
        // Without this, some devices default to WPS_PIN and show a PIN entry screen.
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
        }

        _connectionState.value = ConnectionState.Connecting(device.deviceName ?: "Unknown")

        try {
            manager?.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "connectToDevice: connect initiated to ${device.deviceName}")
                }
                override fun onFailure(reason: Int) {
                    Log.e(TAG, "connectToDevice: FAILED to ${device.deviceName}, reason=$reason")
                    _connectionState.value = ConnectionState.Error("Connection failed (code $reason)")
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "connectToDevice: SecurityException", e)
            _connectionState.value = ConnectionState.Error("Permission denied")
        }
    }

    fun disconnect() {
        Log.d(TAG, "disconnect: removing group")
        manager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "disconnect: group removed successfully")
                _connectionState.value = ConnectionState.Disconnected
            }
            override fun onFailure(reason: Int) {
                Log.e(TAG, "disconnect: removeGroup failed, reason=$reason")
            }
        })
    }

    fun createGroup() {
        if (!hasPermissions()) {
            Log.e(TAG, "createGroup: missing permissions")
            _connectionState.value = ConnectionState.Error("Missing permissions")
            return
        }

        Log.d(TAG, "createGroup: creating Wi-Fi Direct group")
        try {
            manager?.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "createGroup: group created successfully (this device is group owner)")
                }
                override fun onFailure(reason: Int) {
                    Log.e(TAG, "createGroup: FAILED, reason=$reason")
                    _connectionState.value = ConnectionState.Error("Failed to create group (code $reason)")
                }
            })
        } catch (e: SecurityException) {
            Log.e(TAG, "createGroup: SecurityException", e)
            _connectionState.value = ConnectionState.Error("Permission denied")
        }
    }

    fun getDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    private fun hasPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val nearbyDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        Log.d(TAG, "hasPermissions: fineLocation=$fineLocation, nearbyDevices=$nearbyDevices")
        return fineLocation && nearbyDevices
    }

    fun release() {
        Log.d(TAG, "release")
        stopDiscovery()
        try {
            receiver?.let { context.unregisterReceiver(it) }
        } catch (_: Exception) {}
        receiver = null
        channel = null
        manager = null
    }
}