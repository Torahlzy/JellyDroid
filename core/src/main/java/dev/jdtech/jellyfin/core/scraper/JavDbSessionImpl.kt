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

            // 失败时站点会退回登录页并渲染错误提示；成功则跳到站内其它页面
            if (response.url.contains("/login")) {
                loginToken = null
                return@withLock JavDbLoginResult.Failure(readLoginError(Jsoup.parse(response.body)))
            }

            // 用一个需要登录的页面复核，避免把「被挡在别处的 200」当成成功
            val profileUrl = "$baseUrl/users/profile"
            val check = http.getFollowingRedirects(profileUrl, HEADERS)
            if (check.url.contains("/login")) {
                loginToken = null
                return@withLock JavDbLoginResult.Failure("登录未生效，请确认账号密码后重试")
            }

            loginToken = null
            appPreferences.setValue(appPreferences.javdbAccount, accountName)
            appPreferences.setValue(appPreferences.javdbLoggedInUser, accountName)
            // 登录前缓存的短评可能只有站点给访客的那几条，作废后下次进详情页会重新抓
            commentCache.clear()
            AppLog.i("JavDB 登录成功：%s", accountName)
            JavDbLoginResult.Success(accountName)
        }

    override fun logout() {
        val host =
            scraperUrlProvider.baseUrl(ScraperSite.JAVDB)?.let { url ->
                runCatching { URI(url).host }.getOrNull()
            }
        host?.let { cookieJar.clearHost(it) }
        loginToken = null
        appPreferences.setValue(appPreferences.javdbLoggedInUser, "")
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

    private fun requireBaseUrl(): String =
        scraperUrlProvider.baseUrl(ScraperSite.JAVDB)
            ?: throw ScraperException.WebsiteError("JavDB: 没有配置站点网址")

    private companion object {
        const val CAPTCHA_PATH = "/rucaptcha/?t="

        val HEADERS =
            mapOf(
                "Accept-Language" to "zh-CN,zh;q=0.9,zh-TW;q=0.8,en-US;q=0.7,en;q=0.6,ja;q=0.5"
            )
    }
}
