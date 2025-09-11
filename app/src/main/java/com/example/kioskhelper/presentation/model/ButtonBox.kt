package com.example.kioskhelper.presentation.model

import android.graphics.RectF

data class ButtonBox(
    val id: Int,
    val rect: RectF,
    val ocrLabel: String? = null,      // OCR 결과 텍스트
    val iconLabel: String? = null,     // ICON 결과
) {
    val displayLabel: String
        get() = when {
            !ocrLabel.isNullOrBlank() -> ocrLabel
            !iconLabel.isNullOrBlank() -> iconLabel
            else -> "unknown"
        }
}