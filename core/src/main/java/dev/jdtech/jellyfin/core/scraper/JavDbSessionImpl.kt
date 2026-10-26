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
import org.jsoup.nodes.Document

/**
 * JavDB 网页端登录。
 *
 * 站点登录表单（`POST /user_sessions`）要求三样东西同时在场：
 * 1. 登录页里的一次性 `authenticity_token`；
 * 2. 单次有效的**图形验证码**（`GET /rucaptcha/?t=...` 返回 GIF，答案与当前会话绑定）；
 * 3. 账号（用户名或邮箱）与密码。
 *
 * 三者都绑在同一次会话的 Cookie 上，因此这里强制顺序：先取登录页 → 再取验证码 → 用户看清后一起提交。
 * 会话 Cookie 由 [ScraperCookieJar] 保存（含落盘），登录成功后短评等后续请求自动带上。
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

    /** 登录页拿到的一次性 token，登录成功或失败后都要重新取。 */
    @Volatile private var loginToken: String? = null

    /** 取验证码与提交登录是同一段流程，串行化避免两次点击互相覆盖 token。 */
    private val mutex = Mutex()

    override fun loggedInAccount(): String =
        appPreferences.getValue(appPreferences.javdbLoggedInUser)

    override suspend fun loadCaptcha(): ByteArray =
        mutex.withLock {
            val baseUrl = requireBaseUrl()
            val loginUrl = "$baseUrl/login"
            val page = http.getFollowingRedirects(loginUrl, HEADERS)
            if (page.code !in 200..299) {
                throw ScraperException.WebsiteError("JavDB: 登录页返回 ${page.code}")
            }
            loginToken =
                Jsoup.parse(page.body, loginUrl)
                    .selectFirst("form[action=/user_sessions] input[name=authenticity_token]")
                    ?.attr("value")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw ScraperException.WebsiteError("JavDB: 登录页结构变化，找不到表单 token")
            // 验证码取图会刷新会话 Cookie，必须与随后的提交处在同一次会话里
            http.getBytes("$baseUrl$CAPTCHA_PATH${System.currentTimeMillis()}", HEADERS)
        }

    override suspend fun login(
        account: String,
        password: String,
        captcha: String,
    ): JavDbLoginResult =
        mutex.withLock {
            val baseUrl = requireBaseUrl()
            val token =
                loginToken
                    ?: return@withLock JavDbLoginResult.Failure("验证码已失效，请刷新验证码后重试")
            val accountName = account.trim()
            if (accountName.isEmpty() || password.isEmpty()) {
                return@withLock JavDbLoginResult.Failure("请填写账号与密码")
            }
            if (captcha.isBlank()) {
                return@withLock JavDbLoginResult.Failure("请填写图形验证码")
            }

            val response =
                try {
                    http.postFollowingRedirects(
                        "$baseUrl/user_sessions",
                        form =
                            mapOf(
                                "authenticity_token" to token,
                                "email" to accountName,
                                "password" to password,
                                "_rucaptcha" to captcha.trim(),
                                "remember" to "1",
                                "commit" to "登入",
                            ),
                        headers = HEADERS,
                    )
                } catch (e: ScraperException) {
                    AppLog.w(e, "JavDB 登录请求失败")
                    return@withLock JavDbLoginResult.Failure(e.message ?: "登录请求失败")
                }

            // 失败时站点有两种形态：渲染完整登录页（带错误文案），或 30 字节的 JS 跳转页
            // （`window.location.href='/login'`，HTTP 仍是 200，无任何文案）。只比对 URL 会把
            // 后者漏判成成功（POST 后 URL 仍是 /user_sessions），必须同时看 body
            val bouncedToLogin =
                response.url.contains("/login") ||
                    response.body.contains("window.location.href='/login'")
            if (bouncedToLogin) {
                loginToken = null
                val message =
                    if (response.body.contains("message-header")) {
                        readLoginError(Jsoup.parse(response.body))
                    } else {
                        "登录失败：账号、密码或验证码不正确"
                    }
                return@withLock JavDbLoginResult.Failure(message)
            }

            // 用一个需要登录的页面复核，避免把「被挡在别处的 200」当成成功。
            // 与上面同理：未登录时该页面是 200 + JS 跳转（仅 30 字节），长度与内容都要看
            val profileUrl = "$baseUrl/users/profile"
            val check = http.getFollowingRedirects(profileUrl, HEADERS)
            val profileInvalid =
                check.url.contains("/login") ||
                    check.body.contains("window.location.href='/login'") ||
                    check.body.length < PROFILE_MIN_LENGTH
            if (profileInvalid) {
                loginToken = null
                return@withLock JavDbLoginResult.Failure("登录未生效，请确认账号密码后重试")
            }

            loginToken = null
            appPreferences.setValue(appPreferences.javdbAccount, accountName)
            appPreferences.setValue(appPreferences.javdbLoggedInUser, accountName)
            // 登录复核用的 profile 页里带着账号的「用戶類型」，顺手解析出 VIP 状态给设置页账号行显示
            appPreferences.setValue(appPreferences.javdbVip, parseVip(check.body))
            // 登录前缓存的短评可能只有站点给访客的那几条，作废后下次进详情页会重新抓
            commentCache.clear()
            AppLog.i("JavDB 登录成功：%s", accountName)
            JavDbLoginResult.Success(accountName)
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
        loginToken = null
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
        loginToken = null
        appPreferences.setValue(appPreferences.javdbLoggedInUser, "")
        appPreferences.setValue(appPreferences.javdbVip, false)
        // 退出后能看到的评论会变少，旧缓存同样作废
        CoroutineScope(Dispatchers.IO).launch { commentCache.clear() }
        AppLog.i("JavDB 已退出登录")
    }

    /**
     * 登录失败页上的提示文案。
     *
     * 站点把错误放在 `div.message-container > article.message > div.message-header` 里
     * （页面上还有一条「永久訪問 JavDB 的方法」的 `message-body`，不能笼统地取 message-body）。
     */
    private fun readLoginError(document: Document): String =
        document
            .select("div.message-container .message-header")
            .map { it.text().trim() }
            .firstOrNull { it.isNotEmpty() }
            ?: "登录失败：账号、密码或验证码不正确"

    /**
     * 从 profile 页解析账号是否 VIP：个人信息的「用戶類型」一栏是
     * `<span class="label">用戶類型:</span><span class="tag ...">永久VIP</span>` 这样的结构，
     * VIP 用户的值里带「VIP」字样；非 VIP 该栏可能没有 tag 或不含「VIP」。
     */
    private fun parseVip(profileBody: String): Boolean {
        val label =
            Jsoup.parse(profileBody)
                .select("span.label")
                .firstOrNull { it.text().contains("用戶類型") }
                ?: return false
        val value = label.parent()?.selectFirst("span.tag")?.text().orEmpty()
        return value.contains("VIP", ignoreCase = true)
    }

    private fun requireBaseUrl(): String =
        scraperUrlProvider.baseUrl(ScraperSite.JAVDB)
            ?: throw ScraperException.WebsiteError("JavDB: 没有配置站点网址")

    private companion object {
        const val CAPTCHA_PATH = "/rucaptcha/?t="

        /** profile 页真实内容远大于此；小于它基本就是站点的 JS 跳转 / 异常短响应。 */
        const val PROFILE_MIN_LENGTH = 1000

        val HEADERS =
            mapOf(
                "Accept-Language" to "zh-CN,zh;q=0.9,zh-TW;q=0.8,en-US;q=0.7,en;q=0.6,ja;q=0.5"
            )
    }
}
