// com/example/beamshare/model/NearbyDevice.kt
package com.example.beamshare.model

data class NearbyDevice(
    val id: String,
    val name: String,
    val signalStrength: String,
    val isConnecting: Boolean = false
)