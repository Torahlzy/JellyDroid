package dev.jdtech.jellyfin.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.jdtech.jellyfin.core.scraper.ActorImageScraper
import dev.jdtech.jellyfin.core.scraper.ActorImageScraperImpl
import dev.jdtech.jellyfin.core.scraper.ImageScraper
import dev.jdtech.jellyfin.core.scraper.ImageScraperImpl
import dev.jdtech.jellyfin.core.scraper.MetadataScraper
import dev.jdtech.jellyfin.core.scraper.MetadataScraperImpl
import dev.jdtech.jellyfin.core.scraper.ScraperCookieJar
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/**
 * 抓取外部站点（Jav321 / JavBus / JavDB，按 `ScraperSite` 的顺序）的网络依赖。
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

    @Binds
    @Singleton
    abstract fun bindImageScraper(impl: ImageScraperImpl): ImageScraper

    @Binds
    @Singleton
    abstract fun bindActorImageScraper(impl: ActorImageScraperImpl): ActorImageScraper

    companion object {
        /** 抓取是前台交互，超时给短一些，站点无响应时尽快切到下一个。 */
        private const val TIMEOUT_SECONDS = 15L

        /**
         * 用桌面 UA，不能用手上的手机 UA。
         *
         * jav321 会把手机访客跳到它的「下载 / 镜像」域名（实测手机 UA 连首页都被 302 到
         * `xiazai.it.com`，再往下是广告域名，跟过去只会超时），桌面 UA 才返回正常页面。
         * 这个值与 JavSP 参考实现里的一致（三个站点都靠它工作）。
         */
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"

        /**
         * 浏览器都会带、而爬虫常常缺的请求头。
         *
         * 站点会据此判断请求是否来自真实浏览器；抓取器自己指定过的头（如 JavDB / JavBus 的
         * `Accept-Language`）优先，不被这里覆盖。
         * 刻意不带 `sec-fetch-*` / `sec-ch-ua-*`：它们的取值取决于具体请求上下文
         * （顶层导航还是子资源、同站还是跨站），硬套一个值反而更像伪造。
         */
        private val BROWSER_HEADERS =
            mapOf(
                "Accept" to
                    "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Accept-Language" to "zh-CN,zh;q=0.9,en;q=0.8",
                "Upgrade-Insecure-Requests" to "1",
                "DNT" to "1",
            )

        @Provides
        @Singleton
        @ScraperOkHttpClient
        fun provideScraperOkHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                // 由抓取器自行决定是否跟随重定向
                .followRedirects(false)
                .followSslRedirects(false)
                // 站点常用 Cookie 标记正常访客，而 OkHttp 默认一个都不存（重定向链里也会丢）
                .cookieJar(ScraperCookieJar())
                .addInterceptor { chain ->
                    val request = chain.request()
                    val builder = request.newBuilder().header("User-Agent", USER_AGENT)
                    BROWSER_HEADERS.forEach { (name, value) ->
                        if (request.header(name) == null) builder.header(name, value)
                    }
                    chain.proceed(builder.build())
                }
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
    }
}
