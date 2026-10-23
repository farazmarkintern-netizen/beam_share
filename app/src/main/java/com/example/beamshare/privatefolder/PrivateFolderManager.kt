package com.example.beamshare.privatefolder

import android.content.Context
import java.io.File

object PrivateFolderManager {

    private const val FOLDER_NAME = "private_vault"

    /**
     * Returns the directory where private folder files are stored.
     * Uses internal storage — not accessible to other apps.
     */
    fun getVaultDir(context: Context): File {
        val dir = File(context.filesDir, FOLDER_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Deletes every file inside the vault directory.
     * Called when the user resets their PIN (forgot PIN flow).
     */
    fun deleteAllFiles(context: Context) {
        val dir = getVaultDir(context)
        if (dir.exists()) {
            dir.listFiles()?.forEach { file ->
                file.delete()
            }
        }
    }

    /**
     * Returns the list of files currently in the vault.
     */
    fun listFiles(context: Context): List<File> {
        return getVaultDir(context).listFiles()?.toList() ?: emptyList()
    }
}
