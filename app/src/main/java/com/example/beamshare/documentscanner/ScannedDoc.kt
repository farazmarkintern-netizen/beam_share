package com.example.beamshare.documentscanner

data class ScannedDoc(
    val id: String,
    val name: String,
    val filePath: String,       // absolute path to saved PDF
    val sizeBytes: Long,
    val dateStamp: String       // "yyyy-MM-dd"
)