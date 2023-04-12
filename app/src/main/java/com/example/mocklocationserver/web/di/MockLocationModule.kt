package com.example.mocklocationserver.web.di

import com.example.mocklocationserver.web.data.InMemoryLocationRequestRepository
import com.example.mocklocationserver.web.service.MockLocationProvideService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MockLocationModule {

    @Provides
    @Singleton
    fun provideFakeLocationRepository(
    ): InMemoryLocationRequestRepository {
        return InMemoryLocationRequestRepository()
    }


    @Provides
    @Singleton
    fun provideFakeLocationService(
        repository: InMemoryLocationRequestRepository,
    ): MockLocationProvideService {
        return MockLocationProvideService(repository)
    }
}
