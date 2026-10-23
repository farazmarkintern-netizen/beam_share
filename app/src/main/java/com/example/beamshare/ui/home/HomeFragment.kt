package com.example.beamshare.ui.home

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentHomeBinding
import com.example.beamshare.permissions.PermissionsActivity
import com.example.beamshare.privatefolder.PinManager
import com.example.beamshare.privatefolder.SetupPinActivity
import com.example.beamshare.privatefolder.UnlockPinActivity
import com.example.beamshare.session.SessionManager
import com.example.beamshare.session.SessionState
import com.example.beamshare.ui.discovery.DeviceDiscoveryActivity
import com.example.beamshare.ui.receive.ReceiveWaitingActivity
import com.example.beamshare.ui.send.SendFilesActivity
import com.example.beamshare.session.SessionActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var wasSessionActive = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeroCard()
        setupClickListeners()
        observeSessionState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
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
                            if (wasSessionActive && state.reason != null && state.reason != "user_left") {
                                Log.w(TAG, "Session ended unexpectedly: ${state.reason}")
                                Toast.makeText(
                                    requireContext(),
                                    "Connection lost: ${state.reason}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            wasSessionActive = false
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
        if (sessionActive) {
            binding.heroCard.visibility = View.GONE
            binding.layoutNotConnected.visibility = View.GONE
            binding.layoutConnected.visibility = View.VISIBLE

            binding.tvSessionPeer.text = remoteName.ifEmpty { getString(R.string.home_unknown_device) }
            binding.tvSessionStatus.text = statusText
        } else {
            binding.heroCard.visibility = View.VISIBLE
            binding.layoutNotConnected.visibility = View.VISIBLE
            binding.layoutConnected.visibility = View.GONE
        }
    }

    private fun setupHeroCard() {
        binding.tvDeviceName.text = Build.MODEL
        binding.pillShared.text = getString(R.string.home_total_shared, "0 MB")
    }

    private fun setupClickListeners() {
        // ── Disconnected state ────────────────────────────────────────
        binding.cardSend.setOnClickListener {
            startActivity(Intent(requireContext(), ReceiveWaitingActivity::class.java))
        }
        binding.cardReceive.setOnClickListener {
            startActivity(Intent(requireContext(), DeviceDiscoveryActivity::class.java))
        }

        // ── Connected state ───────────────────────────────────────────
        binding.cardSendFiles.setOnClickListener {
            openSendFiles(SendFilesActivity.Category.PHOTOS)
        }
        binding.sessionBanner.setOnClickListener {
            val s = SessionManager.state.value
            if (s is SessionState.Connected || s is SessionState.Reconnecting) {
                openSessionActivity()
            }
        }

        // ── Browse grid ───────────────────────────────────────────────
        binding.itemPhotos.setOnClickListener    { openSendFiles(SendFilesActivity.Category.PHOTOS) }
        binding.itemVideos.setOnClickListener    { openSendFiles(SendFilesActivity.Category.VIDEOS) }
        binding.itemMusic.setOnClickListener     { openSendFiles(SendFilesActivity.Category.MUSIC) }
        binding.itemDocuments.setOnClickListener { openSendFiles(SendFilesActivity.Category.DOCUMENTS) }
        binding.itemApps.setOnClickListener      { openSendFiles(SendFilesActivity.Category.APPS) }
        binding.itemContacts.setOnClickListener  { openSendFiles(SendFilesActivity.Category.CONTACTS) }

        // ── More Tools ────────────────────────────────────────────────
        binding.cardPrivateFolder.setOnClickListener {
            val intent = if (PinManager.isPinSet(requireContext())) {
                Intent(requireContext(), UnlockPinActivity::class.java)
            } else {
                Intent(requireContext(), SetupPinActivity::class.java)
            }
            startActivity(intent)
        }

        // ── Permissions ───────────────────────────────────────────────
        binding.btnPermission.setOnClickListener {
            startActivity(Intent(requireContext(), PermissionsActivity::class.java))
        }
    }

    private fun openSessionActivity() {
        val intent = Intent(requireContext(), SessionActivity::class.java).apply {
            putExtra(SessionActivity.EXTRA_REMOTE_DEVICE_NAME, SessionManager.peerName())
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
}