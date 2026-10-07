package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.di.ScraperOkHttpClient
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.IOException
import java.net.Proxy
import java.net.URI
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
    ): ScraperResponse =
        execute(Request.Builder().url(url).post(formBody(form)).withHeaders(headers).build())

    /**
     * POST 并跟随重定向，**只跟同域内的跳转**。
     *
     * jav321 命中影片后会 301 跳到本站详情页，而跳转后要改用 GET 取正文（浏览器与 `requests` 都是这么做的）；
     * 抓取客户端整体关掉了自动重定向，因此这里手动跟。
     * 跳到别的域名多半不是正常的跳转，而是请求被站点或中间层拦下、丢到一个不明域名：跟过去拿不到内容，
     * 还要白等一次读超时，所以遇到跨域跳转就停下，把 3xx 原样返回给调用方去判断原因。
     */
    suspend fun postFollowingRedirects(
        url: String,
        form: Map<String, String>,
        headers: Map<String, String> = emptyMap(),
    ): ScraperResponse {
        var response =
            execute(Request.Builder().url(url).post(formBody(form)).withHeaders(headers).build())
        var hops = 0
        while (response.isRedirect && hops < MAX_REDIRECTS) {
            val nextUrl = resolveUrl(response.url, response.location) ?: break
            if (!isSameHost(response.url, nextUrl)) break
            response = get(nextUrl, headers)
            hops++
        }
        return response
    }

    /** 两个地址是否同域；解析不出主机名时按「不同域」处理。 */
    private fun isSameHost(first: String, second: String): Boolean {
        val firstHost = runCatching { URI(first).host }.getOrNull() ?: return false
        val secondHost = runCatching { URI(second).host }.getOrNull() ?: return false
        return firstHost.equals(secondHost, ignoreCase = true)
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
     * 下载二进制内容（抓取封面用）。
     *
     * 与取 HTML 不同，这里必须跟随重定向：图片地址常被站点 302 到 CDN，
     * 不跟随就会把跳转响应体（空或一段提示 HTML）当成图片返回。
     */
    suspend fun getBytes(url: String, headers: Map<String, String> = emptyMap()): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                clientWithProxy()
                    .newBuilder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                    .newCall(Request.Builder().url(url).get().withHeaders(headers).build())
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) {
                            // 服务端明确回了非 2xx，没有更底层的异常可作 cause
                            throw ScraperException.NetworkError(
                                "下载图片失败：$url（${response.code}）",
                                null,
                            )
                        }
                        response.body?.bytes()?.takeIf { it.isNotEmpty() }
                            ?: throw ScraperException.NetworkError("下载图片为空：$url", null)
                    }
            } catch (e: IOException) {
                throw ScraperException.NetworkError("下载图片失败：$url", e)
            }
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

    private fun formBody(form: Map<String, String>): FormBody =
        FormBody.Builder().apply { form.forEach { (name, value) -> add(name, value) } }.build()

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
