package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.core.comment.CommentCache
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.JavDbLoginResult
import dev.jdtech.jellyfin.settings.domain.JavDbSession
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup

/**
 * JavDB 账号会话。
 *
 * 站点登录（图形验证码与 Cloudflare 人机验证）完全在过盾页的 WebView 里由用户完成，本类不接触
 * 账号密码，只负责接管网页登录后的会话：[adoptWebViewSession] 把整段 Cookie 搬进
 * [ScraperCookieJar]（含落盘），校验一次有效后回填账号名与 VIP 状态，之后的短评请求自动带上会话。
 */
class JavDbSessionImpl
@Inject
constructor(
    private val http: ScraperHttp,
    private val scraperUrlProvider: ScraperUrlProvider,
    private val cookieJar: ScraperCookieJar,
    private val appPreferences: AppPreferences,
    private val commentCache: CommentCache,
) : JavDbSession {

    /** 接管网页会话与短评侧的会话校验可能并发，串行化避免两次请求互相覆盖 Cookie 与登录状态。 */
    private val mutex = Mutex()

    override fun loggedInAccount(): String =
        appPreferences.getValue(appPreferences.javdbLoggedInUser)

    override suspend fun adoptWebViewSession(
        pageUrl: String,
        cookieHeader: String?,
    ): JavDbLoginResult =
        mutex.withLock {
            if (cookieHeader.isNullOrBlank()) {
                return@withLock JavDbLoginResult.Failure(WEB_LOGIN_REQUIRED)
            }
            // 整段导入：走到这里说明 App 未登录，网页里的会话就是之后抓取要用的会话
            cookieJar.importCookies(pageUrl, cookieHeader)
            val baseUrl = requireBaseUrl()
            val check =
                try {
                    http.getFollowingRedirects(
                        "$baseUrl/users/profile",
                        HEADERS,
                        // 出口要与过盾页 WebView 保持一致（cf_clearance 与 IP 绑定）：网页打开时按
                        // 「评论使用代理」决定走代理还是直连，这里用同一个开关，否则 IP 不同会被判 403
                        useProxy = appPreferences.getValue(appPreferences.commentsUseProxy),
                    )
                } catch (e: ScraperException) {
                    AppLog.w(e, "JavDB 网页登录后校验会话失败")
                    return@withLock JavDbLoginResult.Failure(e.message ?: "校验网页登录状态失败")
                }
            // 与 ensureSessionValid 同理：未登录时站点回 JS 跳转页或完整登录页（HTTP 仍是 200）
            val invalid =
                check.url.contains("/login") ||
                    check.body.contains("window.location.href='/login'") ||
                    check.body.length < PROFILE_MIN_LENGTH
            if (invalid) {
                AppLog.i("JavDB 网页登录未生效：profile 页仍是未登录状态")
                return@withLock JavDbLoginResult.Failure(WEB_LOGIN_REQUIRED)
            }

            // 网页登录没有用户输入的账号可存，名字只能从 profile 页解析；解析不到时用站点名占位
            // ——javdbLoggedInUser 空串即「未登录」，不能留空
            val account = parseAccountName(check.body).ifBlank { UNKNOWN_ACCOUNT_NAME }
            appPreferences.setValue(appPreferences.javdbLoggedInUser, account)
            appPreferences.setValue(appPreferences.javdbVip, parseVip(check.body))
            // 登录前缓存的短评可能只有站点给访客的那几条，作废后下次进详情页会重新抓
            commentCache.clear()
            AppLog.i("JavDB 网页登录成功：%s", account)
            JavDbLoginResult.Success(account)
        }

    override suspend fun ensureSessionValid(): Boolean {
        if (loggedInAccount().isBlank()) return false
        val baseUrl = requireBaseUrl()
        val check =
            try {
                http.getFollowingRedirects("$baseUrl/users/profile", HEADERS)
            } catch (e: ScraperException) {
                // 网络异常不代表会话失效，按仍有效处理，避免偶发失败误清登录态
                AppLog.w(e, "JavDB 会话校验请求失败，按有效处理")
                return true
            }
        // 站点对未登录访问返回 JS 跳转页或完整登录页（HTTP 都是 200），必须看 body
        val invalid =
            check.url.contains("/login") ||
                check.body.contains("window.location.href='/login'") ||
                check.body.length < PROFILE_MIN_LENGTH
        if (!invalid) return true

        // 会话已失效：清掉本地登录痕迹（与登出相同的清理，但不打「已退出」日志）
        val host = runCatching { URI(baseUrl).host }.getOrNull()
        host?.let { cookieJar.clearHost(it) }
        appPreferences.setValue(appPreferences.javdbLoggedInUser, "")
        appPreferences.setValue(appPreferences.javdbVip, false)
        AppLog.i("JavDB 会话已失效，已清除登录状态，请重新登录")
        return false
    }

    override fun logout() {
        val host =
            scraperUrlProvider.baseUrl(ScraperSite.JAVDB)?.let { url ->
                runCatching { URI(url).host }.getOrNull()
            }
        host?.let { cookieJar.clearHost(it) }
        appPreferences.setValue(appPreferences.javdbLoggedInUser, "")
        appPreferences.setValue(appPreferences.javdbVip, false)
        // 退出后能看到的评论会变少，旧缓存同样作废
        CoroutineScope(Dispatchers.IO).launch { commentCache.clear() }
        AppLog.i("JavDB 已退出登录")
    }

    /**
     * 从 profile 页解析账号是否 VIP：个人信息的「用戶類型」一栏是
     * `<span class="label">用戶類型:</span><span class="tag ...">永久VIP</span>` 这样的结构，
     * VIP 用户的值里带「VIP」字样；非 VIP 该栏可能没有 tag 或不含「VIP」。
     */
    private fun parseVip(profileBody: String): Boolean =
        readProfileField(profileBody, "用戶類型").contains("VIP", ignoreCase = true)

    /** 从 profile 页解析用户名；网页登录没有用户输入的账号可存，只能从这里取。 */
    private fun parseAccountName(profileBody: String): String = readProfileField(profileBody, "用戶名")

    /**
     * 读 profile 页某一栏的值：`span.label` 是字段名，它与值同在一个父节点里，
     * 值通常是 `span.value` 或 `span.tag`，两者都没有时退回父节点去掉字段名后的文本。
     */
    private fun readProfileField(profileBody: String, labelKeyword: String): String {
        val label =
            Jsoup.parse(profileBody)
                .select("span.label")
                .firstOrNull { it.text().contains(labelKeyword) }
                ?: return ""
        val parent = label.parent() ?: return ""
        val value = parent.selectFirst("span.value")?.text()?.trim()
        if (!value.isNullOrEmpty()) return value
        val tag = parent.selectFirst("span.tag")?.text()?.trim()
        if (!tag.isNullOrEmpty()) return tag
        // 兜底取的是整个父节点的文本，结构不符时会拿到一大段内容；超过字段长度上限的宁可不用
        return parent
            .text()
            .removePrefix(label.text())
            .trim()
            .takeIf { it.length <= MAX_PROFILE_FIELD_LENGTH }
            .orEmpty()
    }

    private fun requireBaseUrl(): String =
        scraperUrlProvider.baseUrl(ScraperSite.JAVDB)
            ?: throw ScraperException.WebsiteError("JavDB: 没有配置站点网址")

    private companion object {
        /** 网页里还没登录时的统一提示。 */
        const val WEB_LOGIN_REQUIRED = "网页里还没有登录，请先登录站点账号"

        /** profile 页解析不到用户名时的占位：登录态不能存空串（空串即未登录）。 */
        const val UNKNOWN_ACCOUNT_NAME = "JavDB"

        /** profile 页字段值（用户名、用户类型）的长度上限，超过它的兜底文本一律视为解析失败。 */
        const val MAX_PROFILE_FIELD_LENGTH = 40

        /** profile 页真实内容远大于此；小于它基本就是站点的 JS 跳转 / 异常短响应。 */
        const val PROFILE_MIN_LENGTH = 1000

        val HEADERS =
            mapOf(
                "Accept-Language" to "zh-CN,zh;q=0.9,zh-TW;q=0.8,en-US;q=0.7,en;q=0.6,ja;q=0.5"
            )
    }
}
