package dev.jdtech.jellyfin.di

import javax.inject.Qualifier

/** 标记下载专用的 OkHttp 客户端：绕过系统代理，超时策略与接口请求相互独立。 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class DownloadOkHttpClient
