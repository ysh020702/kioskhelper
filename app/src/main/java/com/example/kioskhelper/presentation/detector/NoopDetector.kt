package com.example.kioskhelper.presentation.detector

import android.graphics.RectF
import androidx.camera.core.ImageProxy
import com.example.kioskhelper.presentation.kiosk.KioskViewModel
import javax.inject.Inject
import javax.inject.Singleton

/** 아무 것도 감지하지 않는 더미 Detector 구현 */
//di.VisionModule 로 가서 구현한 후 바꾸면 됨
@Singleton
class NoopDetector @Inject constructor() : Detector {
    override fun detect(
        image: ImageProxy,
        uiButtons: List<KioskViewModel.UiButton>
    ): List<DetectedButton> = emptyList()
}