package com.example.beamshare.ui.home

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.beamshare.R
import com.example.beamshare.permissions.PermissionsActivity
import com.example.beamshare.session.SessionManager
import com.example.beamshare.session.SessionState
import com.example.beamshare.ui.discovery.DeviceDiscoveryActivity
import com.example.beamshare.ui.receive.ReceiveWaitingActivity
import com.example.beamshare.ui.send.SendFilesActivity
import com.example.beamshare.session.SessionActivity
import kotlinx.coroutines.launch
import kotlin.jvm.java

class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
    }

    /** Track whether we were in a connecting/connected state so we can show
     *  an error toast if the session ends unexpectedly. */
    private var wasSessionActive = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeroCard(view)
        setupClickListeners(view)
        observeSessionState()
        openPermissionActivity()

    }

    private fun observeSessionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state ->
                    Log.d(TAG, "SessionState changed: $state")
                    when (state) {
                        is SessionState.Connected -> {
                            wasSessionActive = true
                            updateSessionUI(true, state.peerName, getString(R.string.home_session_status))
                        }
                        is SessionState.Reconnecting -> {
                            wasSessionActive = true
                            updateSessionUI(true, SessionManager.peerName().ifEmpty { "…" }, getString(R.string.home_session_reconnecting))
                        }
                        is SessionState.Hosting -> {
                            wasSessionActive = true
                            updateSessionUI(true, getString(R.string.home_unknown_device), getString(R.string.home_session_connecting))
                        }
                        is SessionState.Joining -> {
                            wasSessionActive = true
                            updateSessionUI(true, getString(R.string.home_unknown_device), getString(R.string.home_session_connecting))
                        }
                        is SessionState.Ended -> {
                            updateSessionUI(false, "", "")
                            if (wasSessionActive) {
                                wasSessionActive = false
                                Log.w(TAG, "Session ended: ${state.reason}")
                                Toast.makeText(
                                    requireContext(),
                                    "Connection failed: ${state.reason}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                        else -> {
                            wasSessionActive = false
                            updateSessionUI(false, "", "")
                        }
                    }
                }
            }
        }
    }

    private fun updateSessionUI(sessionActive: Boolean, remoteName: String, statusText: String) {
        val v = view ?: return

        val heroCard = v.findViewById<FrameLayout>(R.id.heroCard)
        val layoutNotConnected = v.findViewById<LinearLayout>(R.id.layoutNotConnected)
        val layoutConnected = v.findViewById<FrameLayout>(R.id.layoutConnected)

        if (sessionActive) {
            heroCard.visibility = View.GONE
            layoutNotConnected.visibility = View.GONE
            layoutConnected.visibility = View.VISIBLE

            val displayName = remoteName.ifEmpty { getString(R.string.home_unknown_device) }
            v.findViewById<TextView>(R.id.tvSessionPeer).text = displayName
            v.findViewById<TextView>(R.id.tvSessionStatus).text = statusText
        } else {
            heroCard.visibility = View.VISIBLE
            layoutNotConnected.visibility = View.VISIBLE
            layoutConnected.visibility = View.GONE
        }
    }

    private fun setupHeroCard(view: View) {
        val tvDeviceName = view.findViewById<TextView>(R.id.tvDeviceName)
        val pillShared = view.findViewById<TextView>(R.id.pillShared)

        tvDeviceName.text = Build.MODEL
        pillShared.text = getString(R.string.home_total_shared, "0 MB")
    }

    private fun setupClickListeners(view: View) {
        // ── Disconnected state ────────────────────────────────────────
        // "Create Group" — this device becomes the group owner (receiver/QR host)
        view.findViewById<View>(R.id.cardSend).setOnClickListener {
            startActivity(Intent(requireContext(), ReceiveWaitingActivity::class.java))
        }

        // "Join Group" — this device scans QR and connects as non-owner (sender)
        view.findViewById<View>(R.id.cardReceive).setOnClickListener {
            startActivity(Intent(requireContext(), DeviceDiscoveryActivity::class.java))
        }

        // ── Connected state ───────────────────────────────────────────
        // "Send Files" — opens file picker; SessionActivity is reached after selecting files
        view.findViewById<View>(R.id.cardSendFiles).setOnClickListener {
            openSendFiles(SendFilesActivity.Category.PHOTOS)
        }

        // Connected banner tap → SessionActivity (view active session)
        view.findViewById<View>(R.id.sessionBanner).setOnClickListener {
            val s = SessionManager.state.value
            if (s is SessionState.Connected || s is SessionState.Reconnecting) {
                openSessionActivity()
            }
        }

        // ── Browse grid — always open SendFilesActivity ───────────────
        view.findViewById<View>(R.id.itemPhotos).setOnClickListener {
            openSendFiles(SendFilesActivity.Category.PHOTOS)
        }
        view.findViewById<View>(R.id.itemVideos).setOnClickListener {
            openSendFiles(SendFilesActivity.Category.VIDEOS)
        }
        view.findViewById<View>(R.id.itemMusic).setOnClickListener {
            openSendFiles(SendFilesActivity.Category.MUSIC)
        }
        view.findViewById<View>(R.id.itemDocuments).setOnClickListener {
            openSendFiles(SendFilesActivity.Category.DOCUMENTS)
        }
        view.findViewById<View>(R.id.itemApps).setOnClickListener {
            openSendFiles(SendFilesActivity.Category.APPS)
        }
        view.findViewById<View>(R.id.itemContacts).setOnClickListener {
            openSendFiles(SendFilesActivity.Category.CONTACTS)
        }
    }

    private fun openSessionActivity() {
        val peerName = SessionManager.peerName()
        val intent = Intent(requireContext(), SessionActivity::class.java).apply {
            putExtra(SessionActivity.EXTRA_REMOTE_DEVICE_NAME, peerName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun openSendFiles(category: SendFilesActivity.Category) {
        val intent = Intent(requireContext(), SendFilesActivity::class.java).apply {
            putExtra(SendFilesActivity.EXTRA_CATEGORY, category.name)
        }
        startActivity(intent)
    }
    private fun openPermissionActivity() {
        view?.findViewById<View>(R.id.btnPermission)?.setOnClickListener {
            val intent = Intent(requireContext(), PermissionsActivity::class.java)
            startActivity(intent)
        }
    }
}