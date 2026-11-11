package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.di.ScraperOkHttpClient
import dev.jdtech.jellyfin.logging.AppLog
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 测试设置里填写的代理能否连通外网。
 *
 * 只测「代理本身 + 出站连通性」，不测抓取站点是否被盾：站点被 Cloudflare 拦截时
 * 代理依然可用，混在一起测会把两类问题纠缠成同一个失败提示。
 */
@Singleton
class ProxyConnectionTester
@Inject
constructor(
    @ScraperOkHttpClient private val client: OkHttpClient,
) {
    /**
     * 成功返回请求耗时（毫秒）；失败返回 [ProxyAddressInvalidException] 或网络异常。
     * [address] 留空表示按直连测试。
     */
    suspend fun test(address: String): Result<Long> =
        withContext(Dispatchers.IO) {
            val trimmed = address.trim()
            val proxy =
                if (trimmed.isEmpty()) {
                    null
                } else {
                    ScraperProxy.parse(trimmed)
                        ?: return@withContext Result.failure(ProxyAddressInvalidException())
                }
            try {
                val request = Request.Builder().url(PROBE_URL).get().build()
                val clientBuilder =
                    client.newBuilder().callTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (proxy != null) clientBuilder.proxy(proxy)
                val testClient = clientBuilder.build()
                val startedAt = System.currentTimeMillis()
                testClient.newCall(request).execute().use { response ->
                    check(response.isSuccessful) { "HTTP ${response.code}" }
                }
                Result.success(System.currentTimeMillis() - startedAt)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("代理连通性测试失败：%s", e.message ?: "未知原因")
                Result.failure(e)
            }
        }

    private companion object {
        // Android 系统同款连通性检测端点：成功只返回 204，不落下任何内容，适合做最小探测
        const val PROBE_URL = "https://connectivitycheck.gstatic.com/generate_204"
        const val PROBE_TIMEOUT_SECONDS = 8L
    }
}

/** 地址格式不合法：与真正连不上区分开，界面才能给出针对性提示。 */
class ProxyAddressInvalidException : IllegalArgumentException()
