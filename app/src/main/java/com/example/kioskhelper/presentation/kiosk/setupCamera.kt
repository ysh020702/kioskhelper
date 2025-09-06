package com.example.kioskhelper.presentation.kiosk

import android.annotation.SuppressLint
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.example.kioskhelper.presentation.detector.Detector
import com.example.kioskhelper.presentation.detector.FrameAnalyzer
import com.example.kioskhelper.presentation.model.ButtonBox
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
fun setupCamera(
    previewView: PreviewView,
    lifecycleOwner: LifecycleOwner,
    detector: Detector,
    uiButtonsProvider: () -> List<KioskViewModel.UiButton>,
    onDetections: (List<ButtonBox>) -> Unit
) {
    val context = previewView.context
    val provider = ProcessCameraProvider.getInstance(context).get()

    val preview = Preview.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_16_9)
        .build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

    val analysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .build().also {
            it.setAnalyzer(
                Executors.newSingleThreadExecutor(),
                FrameAnalyzer(previewView, detector, uiButtonsProvider, onDetections)
            )
        }

    provider.unbindAll()
    provider.bindToLifecycle(
        lifecycleOwner,
        CameraSelector.DEFAULT_BACK_CAMERA,
        preview, analysis
    )
}