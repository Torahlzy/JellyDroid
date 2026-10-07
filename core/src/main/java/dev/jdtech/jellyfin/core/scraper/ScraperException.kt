package dev.jdtech.jellyfin.core.scraper

/**
 * 抓取相关的异常，对应 JavSP 的 `CrawlerError` 体系。
 *
 * 每个站点抓取失败都会抛出其中之一，由 [MetadataScraper] 收集后继续尝试下一个站点。
 */
sealed class ScraperException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    /** 站点上没有这部影片。 */
    class MovieNotFound(message: String) : ScraperException(message)

    /** 被站点封锁：CloudFlare 拦截、命中年龄验证页、IP 段限制等。 */
    class SiteBlocked(message: String) : ScraperException(message)

    /** 缺少访问权限：需要登录或站点限定 VIP 可见。 */
    class SitePermissionDenied(message: String) : ScraperException(message)

    /** 非预期的状态码或页面结构。 */
    class WebsiteError(message: String) : ScraperException(message)

    /** 网络本身出错（超时、DNS、连接被断开等）。 */
    class NetworkError(message: String, cause: Throwable?) : ScraperException(message, cause)
}
