package com.fliptofocus.di

import com.fliptofocus.data.repository.AppConfigRepositoryImpl
import com.fliptofocus.data.repository.BlockedAppRepositoryImpl
import com.fliptofocus.domain.repository.AppConfigRepository
import com.fliptofocus.domain.repository.BlockedAppRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindBlockedAppRepository(
        impl: BlockedAppRepositoryImpl
    ): BlockedAppRepository

    @Binds
    @Singleton
    abstract fun bindAppConfigRepository(
        impl: AppConfigRepositoryImpl
    ): AppConfigRepository
}
