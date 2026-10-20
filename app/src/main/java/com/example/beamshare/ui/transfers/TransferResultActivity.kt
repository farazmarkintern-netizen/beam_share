package com.example.beamshare.ui.transfers

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.beamshare.R
import com.example.beamshare.ui.MainActivity
import java.io.File

class TransferResultActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TransferResultActivity"
        const val EXTRA_RESULT_TYPE = "result_type"
        const val EXTRA_FILE_COUNT = "file_count"
        const val EXTRA_TOTAL_SIZE = "total_size"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        const val EXTRA_SUCCESS_COUNT = "success_count"
        const val EXTRA_FAILED_COUNT = "failed_count"

        const val RESULT_SUCCESS = "success"
        const val RESULT_ERROR = "error"
        const val RESULT_PARTIAL = "partial"
    }

    private var resultType = RESULT_SUCCESS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        resultType = intent.getStringExtra(EXTRA_RESULT_TYPE) ?: RESULT_SUCCESS
        Log.d(TAG, "resultType=$resultType fileCount=${intent.getIntExtra(EXTRA_FILE_COUNT, -1)}")

        when (resultType) {
            RESULT_SUCCESS -> setupSuccessScreen()
            RESULT_ERROR -> setupErrorScreen()
            RESULT_PARTIAL -> setupPartialScreen()
        }
    }

    private fun openBeamShareFolder() {
        val beamShareDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "BeamShare"
        )
        Log.d(TAG, "Opening folder: ${beamShareDir.absolutePath}, exists=${beamShareDir.exists()}")

        // Try Downloads app directly
        val downloadsIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                android.net.Uri.parse("content://downloads/public_downloads"),
                "vnd.android.document/directory"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        // Try generic file manager with folder
        val folderIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_TITLE, "BeamShare — Downloads/BeamShare")
        }

        try {
            startActivity(downloadsIntent)
            Log.d(TAG, "Opened Downloads app")
        } catch (e1: Exception) {
            Log.w(TAG, "Downloads intent failed: ${e1.message}")
            try {
                startActivity(Intent.createChooser(folderIntent, "Open file manager → Downloads/BeamShare"))
                Log.d(TAG, "Opened file chooser")
            } catch (e2: Exception) {
                Log.e(TAG, "All intents failed", e2)
                android.widget.Toast.makeText(
                    this,
                    "Files saved to: Downloads/BeamShare",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun setupSuccessScreen() {
        setContentView(R.layout.activity_transfer_complete)
        setupEdgeToEdge()

        val fileCount = intent.getIntExtra(EXTRA_FILE_COUNT, 0)
        val totalSize = intent.getStringExtra(EXTRA_TOTAL_SIZE) ?: ""

        Log.d(TAG, "setupSuccessScreen: fileCount=$fileCount totalSize=$totalSize")

        findViewById<TextView>(R.id.tvTitle).text = getString(R.string.transfer_complete_title)
        findViewById<TextView>(R.id.tvSubtitle).text =
            getString(R.string.transfer_complete_desc_format, fileCount)

        findViewById<TextView>(R.id.btnViewFiles).setOnClickListener {
            openBeamShareFolder()
        }
        findViewById<TextView>(R.id.btnShareAgain).setOnClickListener {
            finish()
        }
        findViewById<TextView>(R.id.btnDone).setOnClickListener {
            navigateHome()
        }
    }

    private fun setupErrorScreen() {
        setContentView(R.layout.activity_transfer_error)
        setupEdgeToEdge()

        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: ""

        val tvShowDetails = findViewById<TextView>(R.id.tvShowErrorDetails)
        val tvDetails = findViewById<TextView>(R.id.tvErrorDetails)
        tvDetails.text = errorMessage

        tvShowDetails.setOnClickListener {
            if (tvDetails.isGone) {
                tvDetails.isVisible = true
                tvShowDetails.text = getString(R.string.hide_error_details)
            } else {
                tvDetails.isGone = true
                tvShowDetails.text = getString(R.string.show_error_details)
            }
        }

        findViewById<TextView>(R.id.btnTryAgain).setOnClickListener {
            finish()
        }
        findViewById<TextView>(R.id.btnGoHome).setOnClickListener {
            navigateHome()
        }
    }

    private fun setupPartialScreen() {
        setContentView(R.layout.activity_transfer_partial)
        setupEdgeToEdge()

        val successCount = intent.getIntExtra(EXTRA_SUCCESS_COUNT, 0)
        val failedCount = intent.getIntExtra(EXTRA_FAILED_COUNT, 0)
        val totalCount = successCount + failedCount

        findViewById<TextView>(R.id.tvSubtitle).text =
            getString(R.string.transfer_partial_desc_format, successCount, totalCount)
        findViewById<TextView>(R.id.tvShowFailedFiles).text =
            getString(R.string.failed_files_count_format, failedCount)

        val tvShowFailed = findViewById<TextView>(R.id.tvShowFailedFiles)
        val rvFailed = findViewById<RecyclerView>(R.id.rvFailedFiles)
        rvFailed.layoutManager = LinearLayoutManager(this)

        tvShowFailed.setOnClickListener {
            rvFailed.isVisible = rvFailed.isGone
        }

        findViewById<TextView>(R.id.btnViewFiles).setOnClickListener {
            openBeamShareFolder()
        }
        findViewById<TextView>(R.id.btnRetryFailed).setOnClickListener {
            finish()
        }
        findViewById<TextView>(R.id.btnDone).setOnClickListener {
            navigateHome()
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

    private fun navigateHome() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}