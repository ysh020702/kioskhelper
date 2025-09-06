package com.example.kioskhelper.di

import com.example.kioskhelper.presentation.detector.Detector
import com.example.kioskhelper.presentation.detector.NoopDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VisionModule {
    @Binds @Singleton
    abstract fun bindDetectModule(
        impl: NoopDetector
    ): Detector
}