package com.example.beamshare.videotoaudio

import androidx.activity.addCallback
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.beamshare.databinding.BottomSheetAudioOptionsBinding
import com.example.beamshare.R
import com.example.beamshare.databinding.FragmentVa04DoneBinding
import com.example.beamshare.videotoaudio.model.ConversionResult
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

class DoneFragment : Fragment() {

    private var _binding: FragmentVa04DoneBinding? = null
    private val binding get() = _binding!!

    private val viewModel: VideoToAudioViewModel by activityViewModels()

    // The result delivered from VA03
    private var doneResult: ConversionResult? = null

    // ── Permission launcher ───────────────────────────────────────────────────

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            performSave()
        } else if (!shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            showPermanentlyDeniedDialog()
        }
        // else: denied once — user can tap Save again
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVa04DoneBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyWindowInsets()
        setupStepPill()
        setupClickListeners()
        observeState()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            viewModel.resetConversion()
            if (!findNavController().popBackStack(R.id.videoToAudioFragment, false)) {
                findNavController().navigate(R.id.homeFragment)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Window insets ─────────────────────────────────────────────────────────

    private fun applyWindowInsets() {
        val initialFooterBottom = binding.footer.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // Top padding on root (for status bar)
            view.updatePadding(top = systemBars.top)

            // Footer gets nav bar inset — NestedScrollView is constrained to footer top
            binding.footer.updatePadding(
                bottom = initialFooterBottom + systemBars.bottom
            )

            // NO scrollContent.updatePadding — NestedScrollView is already bounded above footer

            insets
        }

        ViewCompat.requestApplyInsets(binding.root)
    }

    // ── Step pill ─────────────────────────────────────────────────────────────

    private fun setupStepPill() {
        val dots = listOf(
            binding.stepsPill.tvStep1Dot,
            binding.stepsPill.tvStep2Dot,
            binding.stepsPill.tvStep3Dot,
            binding.stepsPill.tvStep4Dot
        )
        // VA04: all 4 steps done
        dots.forEach { dot ->
            dot.setBackgroundResource(R.drawable.bg_va_step_dot_done)
            dot.setTextColor(android.graphics.Color.WHITE)
        }
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener {
            viewModel.resetConversion()
            if (!findNavController().popBackStack(R.id.videoToAudioFragment, false)) {
                findNavController().navigate(R.id.homeFragment)
            }
        }

        binding.cardAudioFile.setOnClickListener {
            showAudioOptionsSheet()
        }

        binding.btnFooterSave.setOnClickListener {
            handleSaveClick()
        }

        binding.btnFooterShare.setOnClickListener {
            shareFile()
        }
    }

    // ── Observe ───────────────────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.conversionState.collectLatest { state ->
                if (state is ConversionState.Done) {
                    doneResult = state.result
                    bindResult(state.result)
                }
            }
        }
    }

    private fun bindResult(result: ConversionResult) {
        binding.tvOutputFileName.text = result.outputFileName

        // e.g. "4.2 MB · M4A · 192 kbps · 3:45"
        val sizeMb   = "%.1f MB".format(result.fileSizeBytes / (1024.0 * 1024.0))
        val duration = formatDuration(result.durationMs)
        binding.tvOutputFileMeta.text = "$sizeMb · M4A · ${result.bitrateKbps} kbps · $duration"

        binding.tvOutputSize.text     = sizeMb
        binding.tvOutputBitrate.text  = "${result.bitrateKbps} kbps"
        binding.tvOutputDuration.text = duration
    }

    // ── Save logic ────────────────────────────────────────────────────────────

    /**
     * Android 10+ (API 29+): file already saved to MediaStore inside startConversion().
     * Android 9-: WRITE_EXTERNAL_STORAGE permission needed to copy to public Music folder.
     */
    private fun handleSaveClick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            showSnackbar("File saved to Music/BeamShare")
            return
        }
        when {
            hasStoragePermission() -> performSave()
            shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE) ->
                showRationaleDialog()
            else ->
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    private fun hasStoragePermission() =
        ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    private fun performSave() {
        val result = doneResult ?: return
        showSnackbar("Saved: ${result.outputFileName}")
    }

    // ── Share logic ───────────────────────────────────────────────────────────

    private fun shareFile() {
        val result = doneResult ?: return
        val uri: Uri = if (result.outputPath.startsWith("content://")) {
            Uri.parse(result.outputPath)
        } else {
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                File(result.outputPath)
            )
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, result.outputFileName))
    }

    // ── Bottom sheet ─────────────────────────────────────────────────────────

    private fun showAudioOptionsSheet() {
        val sheet = BottomSheetDialog(requireContext())
        val sheetBinding = BottomSheetAudioOptionsBinding.inflate(layoutInflater)
        sheet.setContentView(sheetBinding.root)

        sheetBinding.optionPlayInPlayer.setOnClickListener {
            sheet.dismiss()
            playFile()
        }
        sheetBinding.optionShareFile.setOnClickListener {
            sheet.dismiss()
            shareFile()
        }
        sheetBinding.optionSaveToDevice.setOnClickListener {
            sheet.dismiss()
            handleSaveClick()
        }
        sheetBinding.optionConvertAnother.setOnClickListener {
            sheet.dismiss()
            viewModel.resetConversion()
            findNavController().navigate(R.id.videoToAudioFragment)
        }

        sheet.show()
    }

    private fun playFile() {
        val result = doneResult ?: return
        val uri: Uri = if (result.outputPath.startsWith("content://")) {
            Uri.parse(result.outputPath)
        } else {
            FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                File(result.outputPath)
            )
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "audio/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Play with…"))
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private fun showRationaleDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Storage Permission")
            .setMessage("To save the audio file to your device, BeamShare needs storage access.")
            .setPositiveButton("Allow") { _, _ ->
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermanentlyDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Storage Permission")
            .setMessage("Storage permission was denied. Please enable it in App Settings to save files.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", requireContext().packageName, null)
                    }
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    private fun showSnackbar(msg: String) {
        if (_binding == null) return
        Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).apply {
            anchorView = binding.footer
            show()
        }
    }
}