package dev.jdtech.jellyfin.core.scraper

import dev.jdtech.jellyfin.logging.AppLog
import java.net.InetSocketAddress
import java.net.Proxy

/**
 * 把设置里填写的代理地址解析成 OkHttp 能用的 [Proxy]。
 *
 * 支持 `http://127.0.0.1:7890` 与 `socks5://127.0.0.1:1080`，协议可以省略（按 http 处理）。
 * 留空表示不使用代理（跟随系统）；格式不合法时同样回退到不使用代理，只记一条日志，
 * 避免因为一个手误把抓取功能整个卡死。
 */
internal object ScraperProxy {
    private val ADDRESS_PATTERN = Regex("^(?:(\\w+)://)?([^:/\\s]+):(\\d{1,5})$")

    private const val MIN_PORT = 1
    private const val MAX_PORT = 65535

    fun parse(address: String): Proxy? {
        val trimmed = address.trim()
        if (trimmed.isEmpty()) return null

        val match = ADDRESS_PATTERN.matchEntire(trimmed)
        if (match == null) {
            AppLog.w("代理地址格式不正确，本次按直连处理：%s", trimmed)
            return null
        }
        val port = match.groupValues[3].toIntOrNull()
        if (port == null || port !in MIN_PORT..MAX_PORT) {
            AppLog.w("代理端口超出范围，本次按直连处理：%s", trimmed)
            return null
        }
        val type =
            if (match.groupValues[1].lowercase().startsWith("socks")) Proxy.Type.SOCKS
            else Proxy.Type.HTTP
        // 用未解析地址：SOCKS 代理可以自己解析域名（等价于 socks5h），HTTP 代理也只需要主机名
        return Proxy(type, InetSocketAddress.createUnresolved(match.groupValues[2], port))
    }
}
