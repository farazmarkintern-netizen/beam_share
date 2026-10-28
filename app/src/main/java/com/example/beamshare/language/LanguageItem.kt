package com.example.beamshare.language

data class LanguageItem(
    val name: String,
    val flagResId: Int,
    val code: String,
    var isSelected: Boolean = false
)