package com.example.kioskhelper.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.example.kioskhelper.data.platform.SimpleTts
import com.example.kioskhelper.data.platform.SimpleStt

@Module
@InstallIn(SingletonComponent::class)
object TtsSttModule {
    @Provides @Singleton
    fun provideTts(@ApplicationContext ctx: Context) = SimpleTts(ctx)

    @Provides @Singleton
    fun provideStt(@ApplicationContext ctx: Context) =
        SimpleStt(ctx, onPartial = {}, onFinal = {})
}