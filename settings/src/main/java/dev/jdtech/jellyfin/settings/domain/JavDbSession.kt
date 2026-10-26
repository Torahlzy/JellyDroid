package dev.jdtech.jellyfin.settings.domain

/** 站点登录结果，[Failure.message] 已是可直接展示的文案。 */
sealed interface JavDbLoginResult {
    data class Success(val account: String) : JavDbLoginResult

    data class Failure(val message: String) : JavDbLoginResult
}

/**
 * 抓取站点的账号会话（目前只有 JavDB 需要登录）。
 *
 * 登录成功后会话 Cookie 留在抓取用的 Cookie 存储里，之后的短评请求自动带上；
 * 站点登录需要图形验证码，因此登录分两步：[loadCaptcha] 拿图，用户看清后连账号密码一起提交。
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
     * 取一张登录验证码图片（同时准备好本次登录所需的会话与表单 token）。
     *
     * 返回的是原始图片字节（站点给 GIF），由界面自行解码展示。
     */
    suspend fun loadCaptcha(): ByteArray

    /** 提交账号密码与验证码；验证码错了会返回 [JavDbLoginResult.Failure]，需要换一张再试。 */
    suspend fun login(account: String, password: String, captcha: String): JavDbLoginResult

    /** 退出登录：清掉会话 Cookie 与记录的账号名。 */
    fun logout()
}
