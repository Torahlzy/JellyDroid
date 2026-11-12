package dev.jdtech.jellyfin.film.presentation.di

import androidx.lifecycle.SavedStateHandle
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.repository.JellyfinRepositoryOfflineImpl

@Module
@InstallIn(ViewModelComponent::class)
object DetailRepositoryModule {
    @Provides
    @ViewModelScoped
    @DetailRepository
    fun provideDetailRepository(
        savedStateHandle: SavedStateHandle,
        repository: JellyfinRepository,
        offlineRepository: JellyfinRepositoryOfflineImpl,
    ): JellyfinRepository {
        // 参数随下载入口及其详情链路传递，进程重建后也能恢复本地数据来源。
        return if (savedStateHandle.get<Boolean>("useLocalData") == true) {
            offlineRepository
        } else {
            repository
        }
    }
}
