package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.di.ScraperOkHttpClient
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.IOException
import java.net.Proxy
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/** 一次抓取请求的原始响应。 */
data class ScraperResponse(
    val code: Int,
    /** 实际请求到的地址（不跟随重定向时即请求地址）。 */
    val url: String,
    val body: String,
    val location: String?,
) {
    val isRedirect: Boolean
        get() = code in REDIRECT_CODES && !location.isNullOrBlank()

    private companion object {
        val REDIRECT_CODES = 300..399
    }
}

/**
 * 抓取专用的 HTTP 出口。
 *
 * 客户端关闭了自动重定向：JavBus 在重定向前的响应里才带影片数据，
 * 因此是否跟随 [ScraperResponse.location] 由各抓取器自己决定。
 * 每次请求还会按设置里填写的本地代理派生客户端，见 [clientWithProxy]。
 */
class ScraperHttp
@Inject
constructor(
    @ScraperOkHttpClient private val client: OkHttpClient,
    private val appPreferences: AppPreferences,
) {
    /** 上次解析代理用的地址与结果，见 [proxyFor]；地址未变时直接复用。 */
    @Volatile private var cachedProxyAddress: String? = null
    @Volatile private var cachedProxy: Proxy? = null

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): ScraperResponse =
        execute(Request.Builder().url(url).get().withHeaders(headers).build())

    suspend fun post(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): ScraperResponse {
        val body =
            FormBody.Builder().apply { form.forEach { (name, value) -> add(name, value) } }.build()
        return execute(Request.Builder().url(url).post(body).withHeaders(headers).build())
    }

    /** 手动跟随重定向，返回最终响应，[ScraperResponse.url] 为最终地址。 */
    suspend fun getFollowingRedirects(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): ScraperResponse {
        var currentUrl = url
        var response = get(currentUrl, headers)
        var hops = 0
        while (response.isRedirect && hops < MAX_REDIRECTS) {
            currentUrl = resolveUrl(currentUrl, response.location) ?: break
            response = get(currentUrl, headers)
            hops++
        }
        return response
    }

    /**
     * 带上当前代理设置的客户端。
     *
     * 每次请求都从单例客户端派生：OkHttp 的连接池按 `Address` 复用连接，而 `Address` 里含代理，
     * 因此改完代理设置后不会误用之前直连或旧代理留下的连接（派生出的客户端与单例共用连接池、
     * 线程池，开销可以忽略）。
     */
    private fun clientWithProxy(): OkHttpClient {
        val proxy = proxyFor(appPreferences.getValue(appPreferences.scrapeProxy))
        if (proxy == null) return client
        return client.newBuilder().proxy(proxy).build()
    }

    /**
     * 按地址解析代理，并记住上一次的结果。
     *
     * 地址在一次抓取里不会变，缓存后既省掉反复解析，也避免地址填错时每条请求都重复打同一条告警。
     * 字段标了 `@Volatile`：理论上单次抓取只有一个协程在跑，这里只是为将来可能的并发留个保险。
     */
    private fun proxyFor(address: String): Proxy? {
        if (address == cachedProxyAddress) return cachedProxy
        val proxy = ScraperProxy.parse(address)
        cachedProxyAddress = address
        cachedProxy = proxy
        if (proxy != null) AppLog.d("抓取使用代理：%s", proxy.address())
        return proxy
    }

    private suspend fun execute(request: Request): ScraperResponse =
        withContext(Dispatchers.IO) {
            try {
                clientWithProxy().newCall(request).execute().use { response ->
                    ScraperResponse(
                        code = response.code,
                        url = response.request.url.toString(),
                        body = response.body?.string().orEmpty(),
                        location = response.header("Location"),
                    )
                }
            } catch (e: IOException) {
                throw ScraperException.NetworkError("请求失败：${request.url}", e)
            }
        }

    private fun Request.Builder.withHeaders(headers: Map<String, String>): Request.Builder =
        apply { headers.forEach { (name, value) -> header(name, value) } }

    private companion object {
        const val MAX_REDIRECTS = 5

        /** 把 Location 头解析成绝对地址，只处理站点实际会用的三种形态。 */
        fun resolveUrl(base: String, location: String?): String? {
            if (location.isNullOrBlank()) return null
            if (location.startsWith("http://") || location.startsWith("https://")) return location
            val schemeEnd = base.indexOf("://")
            if (schemeEnd < 0) return location
            val originEnd = base.indexOf('/', schemeEnd + 3)
            val origin = if (originEnd < 0) base else base.substring(0, originEnd)
            return when {
                // 协议相对地址，形如 //host/path
                location.startsWith("//") ->
                    base.substring(0, schemeEnd + 1) + location.removePrefix("//")
                // 站内绝对路径，形如 /doc/driver-verify
                location.startsWith("/") -> origin + location
                // 同目录相对路径
                else -> base.substringBeforeLast('/') + "/" + location
            }
        }
    }
}

/** 状态码不是 2xx 时按通用规则转成抓取异常，[siteName] 用于拼出可读的失败原因。 */
internal fun ScraperResponse.requireSuccess(siteName: String): ScraperResponse =
    when (code) {
        in 200..299 -> this
        403, 503 -> throw ScraperException.SiteBlocked("$siteName: $code 禁止访问")
        404 -> throw ScraperException.MovieNotFound("$siteName: 未找到影片")
        else -> throw ScraperException.WebsiteError("$siteName: 非预期状态码 $code")
    }
