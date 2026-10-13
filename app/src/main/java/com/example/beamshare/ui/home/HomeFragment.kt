// com/example/beamshare/ui/home/HomeFragment.kt
package com.example.beamshare.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.beamshare.R

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClickListeners(view)
    }

    private fun setupClickListeners(view: View) {
        // Send / Receive cards
        view.findViewById<View>(R.id.cardSend).setOnClickListener {
            // TODO: Navigate to Send screen
            Toast.makeText(requireContext(), "Send Files — coming soon", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.cardReceive).setOnClickListener {
            // TODO: Navigate to Receive screen
            Toast.makeText(requireContext(), "Receive Files — coming soon", Toast.LENGTH_SHORT).show()
        }

        // Browse categories
        view.findViewById<View>(R.id.itemPhotos).setOnClickListener {
            // TODO: Open Photos browser
            Toast.makeText(requireContext(), "Photos — coming soon", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.itemVideos).setOnClickListener {
            Toast.makeText(requireContext(), "Videos — coming soon", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.itemMusic).setOnClickListener {
            Toast.makeText(requireContext(), "Music — coming soon", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.itemDocuments).setOnClickListener {
            Toast.makeText(requireContext(), "Documents — coming soon", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.itemApps).setOnClickListener {
            Toast.makeText(requireContext(), "Apps — coming soon", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.itemContacts).setOnClickListener {
            Toast.makeText(requireContext(), "Contacts — coming soon", Toast.LENGTH_SHORT).show()
        }
    }
}