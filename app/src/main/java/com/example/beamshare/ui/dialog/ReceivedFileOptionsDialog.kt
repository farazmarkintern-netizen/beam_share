// com/example/beamshare/ui/dialog/ReceivedFileOptionsDialog.kt
package com.example.beamshare.ui.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import com.example.beamshare.R
import com.example.beamshare.model.TransferHistoryItem

class ReceivedFileOptionsDialog(
    context: Context,
    private val item: TransferHistoryItem,
    private val onOpenFile: () -> Unit,
    private val onShowInFolder: () -> Unit,
    private val onDeleteFromHistory: () -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_received_file_options)

        // Transparent rounded window
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // Width = 90% of screen
            val displayWidth = context.resources.displayMetrics.widthPixels
            setLayout((displayWidth * 0.90).toInt(), android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        }

        // Populate title & details
        findViewById<TextView>(R.id.tvDialogTitle).text = item.title
        val detailText = buildString {
            append(item.totalSize)
            if (item.deviceName.isNotBlank()) {
                append(" · Received from ")
                append(item.deviceName)
            }
        }
        findViewById<TextView>(R.id.tvDialogDetails).text = detailText

        // Option clicks
        findViewById<LinearLayout>(R.id.optionOpenFile).setOnClickListener {
            dismiss()
            onOpenFile()
        }

        findViewById<LinearLayout>(R.id.optionShowInFolder).setOnClickListener {
            dismiss()
            onShowInFolder()
        }

        findViewById<LinearLayout>(R.id.optionDelete).setOnClickListener {
            dismiss()
            onDeleteFromHistory()
        }

        findViewById<TextView>(R.id.btnDialogCancel).setOnClickListener {
            dismiss()
        }
    }

}