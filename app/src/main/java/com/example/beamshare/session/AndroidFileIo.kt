package com.example.beamshare.session

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Android implementation of the file I/O seam.
 *
 * Sending: wraps a content:// [Uri] read through ContentResolver.
 * Receiving: saves into Download/BeamShare/<category>/ reusing the exact folder
 * and de-dup rules from the old FileTransferSocket, then triggers a MediaStore scan.
 */

/** Build an [OutgoingFile] from a picked content Uri. */
class UriOutgoingFile(
    private val context: Context,
    private val uri: Uri,
    override val name: String,
    override val size: Long,
    override val mime: String = ""
) : OutgoingFile {
    override fun openInput(): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open file: $name")
}

/** Saves incoming files into the public Download/BeamShare tree. */
class AndroidIncomingFileFactory(
    private val context: Context
) : IncomingFileFactory {

    private val root: File by lazy {
        File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "BeamShare"
        ).apply { mkdirs() }
    }

    override fun create(name: String, size: Long, mime: String): IncomingFile {
        val dir = File(root, subFolderFor(name)).apply { mkdirs() }
        val target = uniqueFile(dir, name)
        return object : IncomingFile {
            private var stream: FileOutputStream? = null
            override val name: String = name
            override val size: Long = size

            override fun output(): OutputStream =
                FileOutputStream(target).also { stream = it }

            override fun finish(): String {
                try { stream?.flush() } catch (_: Exception) {}
                try { stream?.close() } catch (_: Exception) {}
                MediaScannerConnection.scanFile(
                    context, arrayOf(target.absolutePath), null
                ) { path, uri -> Log.d(TAG, "MediaStore scanned: $path -> $uri") }
                return target.absolutePath
            }

            override fun abort() {
                try { stream?.close() } catch (_: Exception) {}
                try { if (target.exists()) target.delete() } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val TAG = "AndroidFileIo"

        /** Same category mapping the old FileTransferSocket used. */
        fun subFolderFor(fileName: String): String {
            return when (fileName.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp", "svg" -> "Images"
                "mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "ts" -> "Videos"
                "mp3", "aac", "flac", "wav", "ogg", "m4a", "opus" -> "Music"
                "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
                "txt", "csv", "rtf", "odt", "ods", "odp" -> "Documents"
                "apk", "xapk", "aab" -> "Apps"
                "vcf" -> "Contacts"
                "zip", "rar", "7z", "tar", "gz" -> "Archives"
                else -> "Other"
            }
        }

        /** Same de-dup rule the old FileTransferSocket used (name_1.ext, name_2.ext …). */
        fun uniqueFile(dir: File, name: String): File {
            var file = File(dir, name)
            if (!file.exists()) return file
            val dot = name.lastIndexOf('.')
            val base = if (dot > 0) name.substring(0, dot) else name
            val ext = if (dot > 0) name.substring(dot) else ""
            var i = 1
            while (file.exists()) {
                file = File(dir, "${base}_$i$ext")
                i++
            }
            return file
        }
    }
}
