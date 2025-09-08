package com.example.kioskhelper.di


import android.content.Context
import com.example.kioskhelper.vision.TfliteTaskObjectDetector
import com.example.kioskhelper.vision.YoloV8TfliteInterpreter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VisionModule {

    @Provides @Singleton
    fun provideYoloV8Detector(@ApplicationContext ctx: Context): YoloV8TfliteInterpreter {
        return YoloV8TfliteInterpreter(
            context = ctx,
            modelAsset = "model.tflite",
            inputSize = 640,      // 네 모델 입력 크기에 맞춰 320/640 중 선택
            confThresh = 0.25f,
            iouThresh = 0.45f,
            numThreads = 2
        )
    }
}