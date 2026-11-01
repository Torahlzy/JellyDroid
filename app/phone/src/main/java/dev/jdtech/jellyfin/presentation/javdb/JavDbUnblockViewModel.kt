package dev.jdtech.jellyfin.presentation.javdb

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.scraper.ScraperCookieJar
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.core.scraper.ScraperUrlProvider
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import javax.inject.Inject

/**
 * JavDB 过盾页：在内置 WebView 里打开站点，让用户手动通过 Cloudflare 人机验证。
 *
 * 验证通过后站点给 WebView 下发 cf_clearance，它与 UA 和出口 IP 绑定，因此三样要对齐：
 * 页面每次加载完成后把 Cookie 与 WebView 的 UA 同步出来（[onPageFinished]，UA 由后续的
 * 抓取请求复用，见 `:core` 的 `ScraperHttp.withUserAgentOverride`）；WebView 的出口 IP
 * 与短评抓取的实际出口对齐（开了评论代理就用抓取代理，否则直连，见 [proxyAddress]）。
 */
@HiltViewModel
class JavDbUnblockViewModel
@Inject
constructor(
    private val appPreferences: AppPreferences,
    private val cookieJar: ScraperCookieJar,
    scraperUrlProvider: ScraperUrlProvider,
) : ViewModel() {

    /** 过盾起始地址：站点首页（cf_clearance 是域级的，过哪个页面都行）。 */
    val startUrl: String = scraperUrlProvider.baseUrl(ScraperSite.JAVDB).orEmpty()

    /**
     * WebView 使用的代理地址，原样交给 WebView 配置。
     *
     * 必须与短评抓取的实际出口一致（cf_clearance 与 IP 绑定）：短评开了「外网使用代理」
     * 就跟随抓取代理，关了就同样直连——而不是无条件用抓取代理，否则代理地址在手机上
     * 不可达时 WebView 会连接超时。
     */
    val proxyAddress: String =
        if (appPreferences.getValue(appPreferences.commentsUseProxy)) {
            appPreferences.getValue(appPreferences.scrapeProxy)
        } else {
            ""
        }

    /**
     * 页面加载完成后同步一次 Cookie 与 UA（幂等，验证通过前没有 cf_clearance 也无害）。
     * [cookieHeader] 是系统 WebView CookieManager 里的整段 Cookie（`k=v; k2=v2` 形式）。
     */
    fun onPageFinished(url: String, cookieHeader: String?, userAgent: String) {
        appPreferences.setValue(appPreferences.javdbWebViewUserAgent, userAgent)
        if (!cookieHeader.isNullOrBlank()) {
            // 只收 Cloudflare 的放行 Cookie（cf_clearance / __cf_bm 这类 cf / __cf 前缀）：
            // WebView 里的站点会话 Cookie 是匿名新会话，一并导入会把 App 里已登录的会话顶掉，
            // 下次会话校验就被判失效，连带把刚拿到的 cf_clearance 一并清光
            val cfCookies =
                cookieHeader
                    .split(';')
                    .map { it.trim() }
                    .filter {
                        it.startsWith("cf", ignoreCase = true) ||
                            it.startsWith("__cf", ignoreCase = true)
                    }
            if (cfCookies.isNotEmpty()) {
                cookieJar.importCookies(url, cfCookies.joinToString("; "))
                AppLog.d(
                    "JavDB 过盾同步：导入 Cookie [%s]",
                    cfCookies.joinToString(", ") { it.substringBefore('=') },
                )
            }
        }
        AppLog.d("JavDB 过盾页加载完成，已同步 Cookie 与 UA：%s", url)
    }
}
