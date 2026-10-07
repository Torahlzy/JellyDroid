package dev.jdtech.jellyfin.di

import javax.inject.Qualifier

/** 标记抓取专用（外部站点）的 OkHttp 客户端：不自动跟随重定向，超时比下载短得多。 */
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ScraperOkHttpClient
