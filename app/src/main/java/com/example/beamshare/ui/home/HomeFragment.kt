package com.example.beamshare.ui.home

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentHomeBinding
import com.example.beamshare.privatefolder.PinManager
import com.example.beamshare.privatefolder.SetupPinActivity
import com.example.beamshare.privatefolder.UnlockPinActivity
import com.example.beamshare.session.SessionActivity
import com.example.beamshare.session.SessionManager
import com.example.beamshare.session.SessionState
import com.example.beamshare.ui.discovery.DeviceDiscoveryActivity
import com.example.beamshare.ui.receive.ReceiveWaitingActivity
import com.example.beamshare.ui.send.SendFilesActivity
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private var wasSessionActive = false

    // ──────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ──────────────────────────────────────────────────────────────────────────

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
        setupTopBar()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Hero card — default state
    // ──────────────────────────────────────────────────────────────────────────

    private fun setupHeroCard() {
        binding.tvDeviceName.text = Build.MODEL
    }


    // ──────────────────────────────────────────────────────────────────────────
    // Session state observer
    // ──────────────────────────────────────────────────────────────────────────

    private fun observeSessionState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.state.collect { state ->
                    Log.d(TAG, "SessionState changed: $state")
                    when (state) {
                        is SessionState.Connected -> {
                            wasSessionActive = true
                            updateSessionUI(
                                sessionActive = true,
                                remoteName = state.peerName,
                                statusText = getString(R.string.home_session_status)
                            )
                        }
                        is SessionState.Reconnecting -> {
                            wasSessionActive = true
                            updateSessionUI(
                                sessionActive = true,
                                remoteName = SessionManager.peerName().ifEmpty { "…" },
                                statusText = getString(R.string.home_session_reconnecting)
                            )
                        }
                        is SessionState.Hosting -> {
                            wasSessionActive = true
                            updateSessionUI(
                                sessionActive = true,
                                remoteName = getString(R.string.home_unknown_device),
                                statusText = getString(R.string.home_session_connecting)
                            )
                        }
                        is SessionState.Joining -> {
                            wasSessionActive = true
                            updateSessionUI(
                                sessionActive = true,
                                remoteName = getString(R.string.home_unknown_device),
                                statusText = getString(R.string.home_session_connecting)
                            )
                        }
                        is SessionState.Ended -> {
                            updateSessionUI(false, "", "")
                            if (wasSessionActive &&
                                state.reason != null &&
                                state.reason != "user_left"
                            ) {
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

    // ──────────────────────────────────────────────────────────────────────────
    // UI update — swap hero image + connected strip
    // ──────────────────────────────────────────────────────────────────────────

    private fun updateSessionUI(
        sessionActive: Boolean,
        remoteName: String,
        statusText: String
    ) {
        if (sessionActive) {
            binding.ivHeroBg.setImageResource(R.drawable.home_hero1)
            binding.tvThisDevice.text = getString(R.string.home_connected_to)
            binding.tvThisDevice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            binding.tvThisDevice.typeface = resources.getFont(R.font.pjs_extra_bold)
            binding.tvThisDevice.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))

            val cleanPeer = getCleanDeviceName(remoteName)
            binding.tvDeviceName.text = cleanPeer.ifEmpty { getString(R.string.home_unknown_device) }
            binding.tvDeviceName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            binding.tvDeviceName.typeface = resources.getFont(R.font.pjs_extra_bold)
            binding.tvDeviceName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))

            // Hide pill, show timer + disconnect
            binding.pillWifi.visibility = View.GONE
            binding.tvSessionTimer.visibility = View.VISIBLE
            binding.tvSessionTimer.text = statusText.ifEmpty { getString(R.string.home_session_status) }
            binding.btnDisconnect.visibility = View.VISIBLE
        } else {
            binding.ivHeroBg.setImageResource(R.drawable.home_hero)
            binding.tvThisDevice.text = getString(R.string.home_this_device)
            binding.tvThisDevice.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11f)
            binding.tvThisDevice.typeface = resources.getFont(R.font.pjs_medium)
            binding.tvThisDevice.setTextColor(android.graphics.Color.parseColor("#CCFFFFFF"))

            val cleanModel = getCleanDeviceName(Build.MODEL)
            binding.tvDeviceName.text = cleanModel
            binding.tvDeviceName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 22f)
            binding.tvDeviceName.typeface = resources.getFont(R.font.pjs_extra_bold)
            binding.tvDeviceName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white))

            // Show pill, hide timer + disconnect
            binding.pillWifi.visibility = View.VISIBLE
            binding.tvSessionTimer.visibility = View.GONE
            binding.btnDisconnect.visibility = View.GONE
            binding.pillWifi.text = getString(R.string.home_wifi_on)
        }
    }

    private fun getCleanDeviceName(name: String): String {
        if (name.contains("(")) {
            val start = name.indexOf('(')
            val end = name.indexOf(')')
            if (start != -1 && end != -1 && end > start) {
                return name.substring(start + 1, end).trim()
            }
        }
        return name.trim()
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Click listeners
    // ──────────────────────────────────────────────────────────────────────────

    private fun setupClickListeners() {

        // Top bar
        binding.btnNotifications.setOnClickListener {
            // TODO: open notifications screen
        }
        binding.btnPremium.setOnClickListener {
            // TODO: open premium screen
        }

        // Send / Receive
        binding.cardSend.setOnClickListener {
            startActivity(Intent(requireContext(), ReceiveWaitingActivity::class.java))
        }
        binding.cardReceive.setOnClickListener {
            startActivity(Intent(requireContext(), DeviceDiscoveryActivity::class.java))
        }

        // Disconnect button on hero card
        binding.btnDisconnect.setOnClickListener {
            SessionManager.setEnded("user_left")
        }

        // Hero card click when connected
        binding.heroCard.setOnClickListener {
            val s = SessionManager.state.value
            if (s is SessionState.Connected || s is SessionState.Reconnecting) {
                openSessionActivity()
            }
        }

        // Share Any File row
        binding.cardQuickShare.setOnClickListener {
            openSendFiles(SendFilesActivity.Category.PHOTOS)
        }

        // More Tools — Document Scanner (TODO: wire to scanner screen)
        binding.cardDocumentScanner.setOnClickListener {
            // TODO: open document scanner
        }

        // More Tools — Private Folder
        binding.cardPrivateFolder.setOnClickListener {
            val intent = if (PinManager.isPinSet(requireContext())) {
                Intent(requireContext(), UnlockPinActivity::class.java)
            } else {
                Intent(requireContext(), SetupPinActivity::class.java)
            }
            startActivity(intent)
        }

        // More Tools — Image Enhancer (TODO: wire to enhancer screen)
        binding.cardImageEnhancer.setOnClickListener {
            // TODO: open image enhancer
        }

        // More Tools — Video to Audio (MP3 converter)
        binding.cardVideoToAudio.setOnClickListener {
            findNavController().navigate(R.id.videoToAudioFragment)
        }

        // Quick Share Via Link (bottom full-width card)
        binding.liveTransfersCard.setOnClickListener {
            // TODO: open quick share link screen
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────────

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
    private fun setupTopBar() {
        val full = "Beamshare"
        val span = SpannableString(full)
        span.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.bs_dark)),
            0, 4,  // "Beam"
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        span.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.bs_blue)),
            4, full.length,  // "share"
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.tvAppTitle.text = span
    }
}