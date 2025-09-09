package com.example.kioskhelper.di


import android.content.Context
import com.example.kioskhelper.vision.IconRoleClassifier
import com.example.kioskhelper.vision.TfliteTaskObjectDetector
import com.example.kioskhelper.vision.YoloV8TfliteInterpreter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VisionModule {
    // ── 기본 설정값(원하면 바꿔서 바인딩 가능) ──────────────────────────────
    @Provides @Named("yolo_asset")
    fun provideYoloAssetName(): String = "best_int8.tflite"

    @Provides @Named("role_asset")
    fun provideRoleAssetName(): String = "icon16.tflite"

    @Provides @Named("yolo_input_size")
    fun provideYoloInputSize(): Int = 640

    @Provides @Named("yolo_conf_thresh")
    fun provideYoloConfThresh(): Float = 0.25f

    @Provides @Named("yolo_iou_thresh")
    fun provideYoloIouThresh(): Float = 0.45f

    @Provides @Named("yolo_threads")
    fun provideYoloNumThreads(): Int = 2

    // ── 모델 프로바이더 ──────────────────────────────────────────────────
    @Provides
    @Singleton
    fun provideYoloInterpreter(
        @ApplicationContext context: Context,
        @Named("yolo_asset") modelAsset: String,
        @Named("yolo_input_size") inputSize: Int,
        @Named("yolo_conf_thresh") confThresh: Float,
        @Named("yolo_iou_thresh") iouThresh: Float,
        @Named("yolo_threads") numThreads: Int
    ): YoloV8TfliteInterpreter {
        return YoloV8TfliteInterpreter(
            context = context,
            modelAsset = modelAsset,
            inputSize = inputSize,
            confThresh = confThresh,
            iouThresh = iouThresh,
            numThreads = numThreads
        )
    }
    @Provides
    @Singleton
    fun provideIconRoleClassifier(
        @ApplicationContext context: Context,
        @Named("role_asset") modelAsset: String
    ): IconRoleClassifier {
        return IconRoleClassifier(context, modelAsset)
    }
}