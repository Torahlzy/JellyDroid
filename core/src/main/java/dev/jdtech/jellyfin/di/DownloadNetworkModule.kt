package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.Dispatcher
import okhttp3.OkHttpClient

/**
 * 下载链路的网络依赖。
 *
 * 刻意不沿用系统网络设置：系统 DownloadManager 会把下载请求交给设备 / WLAN 代理，
 * 代理不可用或配置异常时请求会被劫持，导致下载迟迟连不上 Jellyfin 服务器
 * （旧实现在 `DownloaderImpl` 里专门打日志排查过这个问题）。
 * 这里显式设置 [Proxy.NO_PROXY]，让下载始终直连服务器。
 */
@Module
@InstallIn(SingletonComponent::class)
object DownloadNetworkModule {
    /** 大文件传输的读写超时按"两次数据之间"计算，30 秒足以容忍服务端抖动。 */
    private const val TIMEOUT_SECONDS = 30L

    /**
     * 同一 host 允许的并发连接数：分片下载单任务就要 4 条，沿用默认的 5
     * 会让两个条目同时下载时互相排队，等于白分了片。
     */
    private const val MAX_REQUESTS_PER_HOST = 8

    @Singleton
    @Provides
    @DownloadOkHttpClient
    fun provideDownloadOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = MAX_REQUESTS_PER_HOST })
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
}
