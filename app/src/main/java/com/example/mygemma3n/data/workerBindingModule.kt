package com.example.mygemma3n.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
abstract class WorkerBindingModule {

    @Binds
    @IntoMap
    @ClassKey(ModelDownloadWorker::class)
    abstract fun bindModelDownloadWorker(factory: ModelDownloadWorker.Factory): ChildWorkerFactory

    // Bind other workers here
}
