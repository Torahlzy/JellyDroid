package dev.jdtech.jellyfin.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.cache.CacheManager
import dev.jdtech.jellyfin.cache.FileCacheManager
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {
    @Provides
    @Singleton
    fun provideCacheManager(@ApplicationContext context: Context, preferences: AppPreferences): CacheManager =
        FileCacheManager(context, preferences)
}
