package com.example.beamshare.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.data.TransferHistoryRepository
import com.example.beamshare.model.HistoryStatus
import com.example.beamshare.model.TransferDirection as HistoryDirection
import com.example.beamshare.session.LiveTransfer
import com.example.beamshare.session.SessionFileAdapter
import com.example.beamshare.session.SessionManager
import com.example.beamshare.session.TransferDirection
import com.example.beamshare.session.TransferStatus
import kotlinx.coroutines.launch

class ReceivedFilesFragment : Fragment() {

    private lateinit var rvFiles: RecyclerView
    private lateinit var adapter: SessionFileAdapter
    private lateinit var repo: TransferHistoryRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_received_files, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repo = TransferHistoryRepository.getInstance(requireContext())
        rvFiles = view.findViewById(R.id.rvReceivedFiles)

        adapter = SessionFileAdapter(
            onCancel = {},
            onDelete = { transfer -> SessionManager.removeTransfer(transfer.id) },
            onPause  = {},
            onResume = {}
        )

        rvFiles.layoutManager = LinearLayoutManager(requireContext())
        rvFiles.adapter = adapter

        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            findNavController().popBackStack()
        }

        // Observe live transfers — auto-updates
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SessionManager.transfers.collect { liveAll ->
                    val merged = buildMergedList(liveAll)
                    adapter.submitList(merged)
                }
            }
        }
    }

    private fun buildMergedList(liveAll: List<LiveTransfer>): List<LiveTransfer> {
        // 1. Live received files (IN direction)
        val liveReceived = liveAll.filter { it.direction == TransferDirection.IN }
        val liveIds = liveReceived.map { it.id.toString() }.toSet()

        // 2. DB history — convert TransferHistoryItem → LiveTransfer
        val historyReceived = repo.getAllItems()
            .filter {
                it.direction == HistoryDirection.RECEIVED &&
                        it.status == HistoryStatus.DONE &&
                        it.id !in liveIds          // skip duplicates
            }
            .map { item ->
                LiveTransfer(
                    id        = item.id.hashCode().toLong(),
                    name      = item.title,
                    size      = parseSize(item.totalSize),
                    direction = TransferDirection.IN,
                    peerName  = item.deviceName,
                    progress  = 100,
                    transferred = parseSize(item.totalSize),
                    status    = TransferStatus.COMPLETED,
                    savedPath = item.filePath
                )
            }

        // 3. Live first, then old history
        return liveReceived + historyReceived
    }

    /** Parse "1.2 MB" / "500 KB" / "2.3 GB" back to bytes */
    private fun parseSize(size: String): Long {
        return try {
            val parts = size.trim().split(" ")
            val num = parts[0].toDouble()
            when (parts.getOrNull(1)?.uppercase()) {
                "GB" -> (num * 1_073_741_824).toLong()
                "MB" -> (num * 1_048_576).toLong()
                "KB" -> (num * 1_024).toLong()
                else -> num.toLong()
            }
        } catch (_: Exception) { 0L }
    }

    companion object {
        fun newInstance() = ReceivedFilesFragment()
    }
}