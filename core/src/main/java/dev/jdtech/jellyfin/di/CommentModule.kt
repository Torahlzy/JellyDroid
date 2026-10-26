package dev.jdtech.jellyfin.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.core.comment.JavDbCommentSource
import dev.jdtech.jellyfin.core.comment.MovieCommentRepository
import dev.jdtech.jellyfin.core.comment.MovieCommentRepositoryImpl
import dev.jdtech.jellyfin.core.comment.MovieCommentSource
import javax.inject.Singleton

/** 外网短评的依赖装配：数据源与「缓存 + 抓取」仓库。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommentModule {

    @Binds
    @Singleton
    abstract fun bindMovieCommentSource(impl: JavDbCommentSource): MovieCommentSource

    @Binds
    @Singleton
    abstract fun bindMovieCommentRepository(impl: MovieCommentRepositoryImpl): MovieCommentRepository
}
