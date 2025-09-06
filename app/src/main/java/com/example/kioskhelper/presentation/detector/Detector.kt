package com.example.kioskhelper.presentation.detector

import androidx.camera.core.ImageProxy
import com.example.kioskhelper.presentation.kiosk.KioskViewModel

interface Detector {
    /**
     * @param image       CameraX ImageProxy
     * @param uiButtons   현재 화면의 UiButton 목록 (KioskVM의 상태 스냅샷)
     * @return 감지된 버튼 사각형들(원본 이미지 좌표계)
     */
    fun detect(image: ImageProxy, uiButtons: List<KioskViewModel.UiButton>): List<DetectedButton>
}