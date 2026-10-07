package dev.jdtech.jellyfin.core.scraper

import java.util.concurrent.ConcurrentHashMap
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 抓取专用的 Cookie 存储。
 *
 * OkHttp 默认一个 Cookie 都不保存（[CookieJar.NO_COOKIES]），而这类站点常用 Cookie 标记「正常访客」：
 * jav321 会给访问过首页的浏览器发 `is_loyal=1`，没有这个 Cookie 的请求（连同重定向时的后续请求）
 * 更像爬虫，容易被丢到不明域名。
 *
 * 只存在内存里：抓取是临时会话，不需要落盘，进程结束即失效。
 */
class ScraperCookieJar : CookieJar {
    private val cookiesByHost = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val stored = cookiesByHost.getOrPut(url.host) { mutableListOf() }
        synchronized(stored) {
            cookies.forEach { cookie ->
                // 同名同路径的直接替换，避免重复下发时越积越多
                stored.removeAll { it.name == cookie.name && it.path == cookie.path }
                stored += cookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val stored = cookiesByHost[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return synchronized(stored) {
            stored.removeAll { it.expiresAt <= now }
            stored.filter { it.matches(url) }
        }
    }
}
