package dev.jdtech.jellyfin.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.core.scraper.MetadataScraper
import dev.jdtech.jellyfin.core.scraper.MetadataScraperImpl
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 抓取外部站点（JavDB / JavBus / Jav321）的网络依赖。
 *
 * 与下载链路分开：抓取要跟随站点自身的语言与跳转行为，因此不能像下载那样固定 NO_PROXY，
 * 也不能自动跟随重定向——JavBus 的数据就在重定向前的响应里。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ScraperModule {

    @Binds
    @Singleton
    abstract fun bindMetadataScraper(impl: MetadataScraperImpl): MetadataScraper

    companion object {
        /** 抓取是前台交互，超时给短一些，站点无响应时尽快切到下一个。 */
        private const val TIMEOUT_SECONDS = 15L

        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36"

        @Provides
        @Singleton
        @ScraperOkHttpClient
        fun provideScraperOkHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                // 由抓取器自行决定是否跟随重定向
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .build()
                    )
                }
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
    }
}
