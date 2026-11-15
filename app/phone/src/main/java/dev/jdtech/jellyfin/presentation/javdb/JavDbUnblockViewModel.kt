package dev.jdtech.jellyfin.presentation.javdb

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.scraper.ScraperCookieJar
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.core.scraper.ScraperUrlProvider
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.JavDbLoginResult
import dev.jdtech.jellyfin.settings.domain.JavDbSession
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * JavDB 过盾页：在内置 WebView 里打开站点，让用户手动通过 Cloudflare 人机验证。
 *
 * 验证通过后站点给 WebView 下发 cf_clearance，它与 UA 和出口 IP 绑定，因此三样要对齐：
 * 页面每次加载完成后把 Cookie 与 WebView 的 UA 同步出来（[onPageFinished]，UA 由后续的
 * 抓取请求复用，见 `:core` 的 `ScraperHttp.withUserAgentOverride`）；WebView 的出口 IP
 * 与短评抓取的实际出口对齐（开了评论代理就用抓取代理，否则直连，见 [proxyAddress]）。
 *
 * 同一页面就是站点账号的**登录页**（[startUrl] 传 `requireLogin = true` 时从登录页开始）：
 * 账号密码、图形验证码与人机验证都在网页里完成，点「完成」时由 [finish] 把网页会话
 * 接管回 App，见 [adoptWebSession]。
 */
@HiltViewModel
class JavDbUnblockViewModel
@Inject
constructor(
    private val appPreferences: AppPreferences,
    private val cookieJar: ScraperCookieJar,
    private val javDbSession: JavDbSession,
    private val scraperUrlProvider: ScraperUrlProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(JavDbUnblockState())
    val state = _state.asStateFlow()

    /** 最近一次加载完成的页面地址与整段 Cookie，网页版登录接管会话时要用（见 [finish]）。 */
    private var lastPageUrl: String? = null
    private var lastCookieHeader: String? = null

    /**
     * 起始地址：过盾从站点首页开始（cf_clearance 是域级的，过哪个页面都行）；
     * 网页版登录直接打开登录页，省得用户自己在首页找入口。
     */
    fun startUrl(requireLogin: Boolean): String {
        val baseUrl = scraperUrlProvider.baseUrl(ScraperSite.JAVDB).orEmpty()
        if (baseUrl.isEmpty()) return ""
        return if (requireLogin) "$baseUrl/login" else baseUrl
    }

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
        lastPageUrl = url
        lastCookieHeader = cookieHeader
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

    /**
     * 点「完成」：先把网页里的登录态搬回 App，再让界面返回上一页。
     *
     * [requireLogin] 为 true（从「站点账号登录」进来的网页版登录）时，网页里没登录就留在本页
     * 并用提示说明，免得用户以为登录成功；为 false（过盾入口）时只做尽力而为的同步，
     * 即使失败也照常返回，不打扰只想通过人机验证的用户。
     */
    fun finish(requireLogin: Boolean) {
        if (_state.value.isSyncing) return
        viewModelScope.launch {
            _state.emit(_state.value.copy(isSyncing = true, message = null))
            val failure = adoptWebSession()
            if (failure != null && requireLogin) {
                _state.emit(_state.value.copy(isSyncing = false, message = failure))
            } else {
                _state.emit(_state.value.copy(isSyncing = false, isFinished = true))
            }
        }
    }

    /** 提示展示过后清空，避免界面重组时重复弹出。 */
    fun onMessageShown() {
        viewModelScope.launch { _state.emit(_state.value.copy(message = null)) }
    }

    /**
     * 把网页里的登录会话接管给抓取，返回失败原因（null 表示成功或无需处理）。
     *
     * App 已登录时直接跳过：网页里多半是匿名新会话，整段导入会顶掉已有登录态；
     * cf_clearance 已在 [onPageFinished] 单独同步过，这里不需要再处理。
     */
    private suspend fun adoptWebSession(): String? {
        if (javDbSession.loggedInAccount().isNotBlank()) return null
        val pageUrl = lastPageUrl ?: return null
        return try {
            when (val result = javDbSession.adoptWebViewSession(pageUrl, lastCookieHeader)) {
                is JavDbLoginResult.Success -> null
                is JavDbLoginResult.Failure -> result.message
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(e, "JavDB 网页版登录状态同步失败")
            e.message ?: "同步网页版登录状态失败"
        }
    }
}
