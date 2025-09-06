package com.example.kioskhelper.di

import com.example.kioskhelper.data.repositoryImpl.SttRepositoryImpl
import com.example.kioskhelper.data.repositoryImpl.TtsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.example.kioskhelper.domain.repository.SttRepository
import com.example.kioskhelper.domain.repository.TtsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton
    abstract fun bindTtsRepository(impl: TtsRepositoryImpl): TtsRepository

    @Binds @Singleton
    abstract fun bindSttRepository(impl: SttRepositoryImpl): SttRepository
}