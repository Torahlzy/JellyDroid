package dev.jdtech.jellyfin.film.presentation.di

import javax.inject.Qualifier

/** 仅为详情页选择数据来源，不改变全局离线设置。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DetailRepository
