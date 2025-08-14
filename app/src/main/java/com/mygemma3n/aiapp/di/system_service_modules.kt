package com.mygemma3n.aiapp.di


import android.content.Context
import android.hardware.camera2.CameraManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SystemServicesModule {

    @Provides
    @Singleton
    fun provideCameraManager(
        @ApplicationContext context: Context
    ): CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
}
