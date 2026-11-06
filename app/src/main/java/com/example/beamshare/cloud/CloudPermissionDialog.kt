package com.example.beamshare.cloud

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.beamshare.R
import com.example.beamshare.databinding.DialogCloudPermissionBinding

class CloudPermissionDialog : DialogFragment() {

    private var _binding: DialogCloudPermissionBinding? = null
    private val binding get() = _binding!!

    var onAgree: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogCloudPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Transparent window background so our rounded XML shows correctly
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        binding.btnCloseDialog.setOnClickListener {
            dismiss()
        }

        binding.btnAgree.setOnClickListener {
            dismiss()
            onAgree?.invoke()
        }

        binding.tvPrivacyPolicy.setOnClickListener {
            // TODO: open privacy policy URL
        }
    }

    override fun onStart() {
        super.onStart()
        // Set dialog width to 90% of screen
        val width = (resources.displayMetrics.widthPixels * 0.90).toInt()
        dialog?.window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CloudPermissionDialog"
    }
}