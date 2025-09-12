package com.example.kioskhelper.presentation.kiosk

import com.example.kioskhelper.presentation.overlayview.DetectionOverlayView
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.kioskhelper.utils.YuvToRgbConverter
import com.example.kioskhelper.vision.IconRoleClassifier
import com.example.kioskhelper.vision.YoloV8TfliteInterpreter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean


/**
 * CameraX Preview + ImageAnalysis 바인딩 및 Analyzer 연결.
 *
 * @param previewView     CameraX 프리뷰가 붙을 View (Compose의 AndroidView로 만든 그거)
 * @param overlayView     빨간 박스를 그릴 오버레이 View
 * @param lifecycleOwner  바인딩 대상 (Activity/Fragment/Compose LocalLifecycleOwner)
 * @param detector        TFLite 감지기 (Hilt @Singleton 권장)
 * @param throttleMs      탐지 주기 제한(ms). 과열/부하 시 100~150 추천. 기본 0(매 프레임)
 */
private val bound = AtomicBoolean(false)
@SuppressLint("UnsafeOptInUsageError")
fun setupCamera(
    previewView: PreviewView,
    overlayView: DetectionOverlayView,
    lifecycleOwner: LifecycleOwner,
    detector: YoloV8TfliteInterpreter,
    roleClf: IconRoleClassifier,
    kioskViewModel: KioskViewModel,
    throttleMs: Long = 0L
) {
    if (!bound.compareAndSet(false, true)) return // 두번째 호출 무시
    android.util.Log.d("SETUP", "setupCamera() called")

    val context: Context = previewView.context
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        android.util.Log.d("SETUP", "cameraProvider ready")
        val cameraProvider = cameraProviderFuture.get()

        // 1) Preview
        val preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // 2) Analysis + Analyzer
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        val analyzer = ObjectDetectAnalyzer(
            previewView = previewView,
            overlayView = overlayView,
            detector = detector,
            yuvConverter = YuvToRgbConverter(previewView.context),
            roleClf = roleClf,
            kioskViewModel = kioskViewModel,
            throttleMs = throttleMs
        )
        analysis.setAnalyzer(Executors.newSingleThreadExecutor(), analyzer)
        android.util.Log.d("SETUP", "setAnalyzer attached")

        // 3) Bind to lifecycle (후면 카메라)
        val selector = CameraSelector.DEFAULT_BACK_CAMERA
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle( lifecycleOwner, selector, preview, analysis )
        android.util.Log.d("SETUP", "bindToLifecycle done")
    }, ContextCompat.getMainExecutor(context))
}