// com/example/beamshare/ui/send/MediaFile.kt
package com.example.beamshare.ui.send

import android.net.Uri

data class MediaFile(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val mimeType: String,
    val dateModified: Long,
    val packageName: String? = null,
    val isSelected: Boolean = false
)