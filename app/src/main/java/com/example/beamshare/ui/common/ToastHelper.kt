// com/example/beamshare/ui/common/ToastHelper.kt
package com.example.beamshare.ui.common

import android.content.Context
import android.widget.Toast

object ToastHelper {

    fun showFailedToSend(context: Context) {
        Toast.makeText(context, "Failed to send files. Please try again.", Toast.LENGTH_LONG).show()
    }

    fun showFileTooLarge(context: Context) {
        Toast.makeText(context, "File is too large to transfer.", Toast.LENGTH_LONG).show()
    }

    fun showExportFailed(context: Context) {
        Toast.makeText(context, "Couldn't export the file. Check storage permissions.", Toast.LENGTH_LONG).show()
    }

    fun showInstallFailed(context: Context) {
        Toast.makeText(context, "App installation failed.", Toast.LENGTH_SHORT).show()
    }

    fun showContactsSaved(context: Context, count: Int) {
        Toast.makeText(context, "$count contact${if (count > 1) "s" else ""} saved successfully.", Toast.LENGTH_SHORT).show()
    }

    fun showTransferCancelled(context: Context) {
        Toast.makeText(context, "Transfer cancelled.", Toast.LENGTH_SHORT).show()
    }

    fun showDeviceNameSaved(context: Context) {
        Toast.makeText(context, "Device name updated.", Toast.LENGTH_SHORT).show()
    }
}