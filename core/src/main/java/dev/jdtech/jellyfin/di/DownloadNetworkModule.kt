package dev.jdtech.jellyfin.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.net.Proxy
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
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

    @Singleton
    @Provides
    @DownloadOkHttpClient
    fun provideDownloadOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
}
