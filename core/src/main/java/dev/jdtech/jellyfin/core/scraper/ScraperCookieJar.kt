package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 抓取专用的 Cookie 存储。
 *
 * OkHttp 默认一个 Cookie 都不保存（[CookieJar.NO_COOKIES]），而这类站点常用 Cookie 标记「正常访客」：
 * jav321 会给访问过首页的浏览器发 `is_loyal=1`，没有这个 Cookie 的请求（连同重定向时的后续请求）
 * 更像爬虫，容易被丢到不明域名。
 *
 * 另外站点账号登录后的会话也以 Cookie 形式存在，因此这里**落盘**一份：
 * 落盘后才重启 App 仍保持登录（抓取站点自己的 Cookie，与 Jellyfin 凭据无关）。
 */
@Singleton
class ScraperCookieJar
@Inject
constructor(private val appPreferences: AppPreferences) : CookieJar {
    private val cookiesByHost = ConcurrentHashMap<String, MutableList<Cookie>>()

    /** 落盘内容只在首次访问时读一次，之后以内存为准。 */
    @Volatile private var restored = false

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        restoreIfNeeded()
        val stored = cookiesByHost.getOrPut(url.host) { mutableListOf() }
        synchronized(stored) {
            cookies.forEach { cookie ->
                // 同名同路径的直接替换，避免重复下发时越积越多
                stored.removeAll { it.name == cookie.name && it.path == cookie.path }
                stored += cookie
            }
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        restoreIfNeeded()
        val stored = cookiesByHost[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return synchronized(stored) {
            stored.removeAll { it.expiresAt <= now }
            stored.filter { it.matches(url) }
        }
    }

    /** 清掉某个站点的全部 Cookie（退出登录用），内存与落盘同时清。 */
    fun clearHost(host: String) {
        restoreIfNeeded()
        cookiesByHost.remove(host)
        persist()
    }

    /** 把内存里的 Cookie 写成「每行 `host\tSet-Cookie 行`」，下次启动据此恢复。 */
    private fun persist() {
        val now = System.currentTimeMillis()
        val lines =
            cookiesByHost.entries.flatMap { (host, stored) ->
                synchronized(stored) {
                    stored.filter { it.expiresAt > now }.map { cookie -> "$host\t$cookie" }
                }
            }
        appPreferences.setValue(appPreferences.scraperCookies, lines.joinToString("\n"))
    }

    private fun restoreIfNeeded() {
        if (restored) return
        synchronized(this) {
            if (restored) return
            restored = true
            val saved = appPreferences.getValue(appPreferences.scraperCookies)
            if (saved.isBlank()) return
            val now = System.currentTimeMillis()
            saved.lineSequence().forEach { line ->
                val separator = line.indexOf('\t')
                if (separator <= 0) return@forEach
                val host = line.substring(0, separator)
                // host 是裸主机名（HttpUrl.host 不带 scheme），而 HttpUrl 必须带 scheme 才能解析，
                // 直接 toHttpUrlOrNull() 恒为 null、会把落盘的 Cookie 全部丢掉，需补上 scheme
                val url = "https://$host".toHttpUrlOrNull() ?: return@forEach
                val cookie = Cookie.parse(url, line.substring(separator + 1)) ?: return@forEach
                if (cookie.expiresAt <= now) return@forEach
                val stored = cookiesByHost.getOrPut(host) { mutableListOf() }
                synchronized(stored) {
                    stored.removeAll { it.name == cookie.name && it.path == cookie.path }
                    stored += cookie
                }
            }
        }
    }
}
