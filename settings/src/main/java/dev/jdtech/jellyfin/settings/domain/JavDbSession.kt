package dev.jdtech.jellyfin.settings.domain

/** 站点登录结果，[Failure.message] 已是可直接展示的文案。 */
sealed interface JavDbLoginResult {
    data class Success(val account: String) : JavDbLoginResult

    data class Failure(val message: String) : JavDbLoginResult
}

/**
 * 抓取站点的账号会话（目前只有 JavDB 需要登录）。
 *
 * 登录只在站点自己的网页里完成（图形验证码与 Cloudflare 人机验证都由站点页面处理），
 * App 不接触账号密码，只负责接管网页登录后的会话：会话 Cookie 留在抓取用的 Cookie 存储里，
 * 之后的短评请求自动带上。
 *
 * 接口定义放在 `:settings` 是为了让设置页的 ViewModel 能直接调用，实现在 `:core`
 * （只有它才依赖网络与抓取站点地址）。
 */
interface JavDbSession {
    /** 已登录的账号名，空串表示未登录。 */
    fun loggedInAccount(): String

    /**
     * 校验当前会话是否仍有效：已登录时访问一次需要登录的页面确认。
     *
     * 站点会话会静默失效（短评接口对失效会话按游客返回，不报错），因此加载短评前先校验；
     * 失效时清除本地登录状态（Cookie 与账号记录一并清掉）并返回 false，未登录也返回 false。
     * 网络异常时按「仍有效」处理并返回 true，避免偶发失败误清登录态。
     */
    suspend fun ensureSessionValid(): Boolean

    /**
     * 接管内置网页里拿到的登录会话。
     *
     * 用户在网页里登录后，这里把整段会话 Cookie 搬进抓取 Cookie 罐，再访问一次 profile 页
     * 确认会话有效，有效则回填账号名与 VIP 状态。
     *
     * **调用方必须保证此时 App 未登录**：网页里的会话多半是匿名新建的，整段导入会顶掉已有登录态。
     *
     * [pageUrl] 为网页当前地址（Cookie 按它的域名入库）；[cookieHeader] 是系统 WebView
     * CookieManager 给出的整段 Cookie（`k=v; k2=v2`）。网页里还没登录时返回
     * [JavDbLoginResult.Failure]，本地登录状态保持不变。
     */
    suspend fun adoptWebViewSession(pageUrl: String, cookieHeader: String?): JavDbLoginResult

    /** 退出登录：清掉会话 Cookie 与记录的账号名。 */
    fun logout()
}
