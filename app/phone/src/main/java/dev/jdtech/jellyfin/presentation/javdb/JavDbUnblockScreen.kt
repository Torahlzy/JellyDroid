package dev.jdtech.jellyfin.presentation.javdb

import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

/**
 * JavDB 过盾页：内置 WebView 打开站点，用户手动完成 Cloudflare 人机验证。
 *
 * 验证通过（页面正常打开）后点右上角「完成」返回，返回后到详情页点「重试」重新加载短评；
 * WebView 侧的 Cookie 与 UA 在每次页面加载完成时自动同步，无需用户做别的操作。
 */
@Composable
fun JavDbUnblockScreen(
    navigateBack: () -> Unit,
    viewModel: JavDbUnblockViewModel = hiltViewModel(),
) {
    JavDbUnblockScreenLayout(
        startUrl = viewModel.startUrl,
        proxyAddress = viewModel.proxyAddress,
        onPageFinished = viewModel::onPageFinished,
        onDone = navigateBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun JavDbUnblockScreenLayout(
    startUrl: String,
    proxyAddress: String,
    onPageFinished: (url: String, cookieHeader: String?, userAgent: String) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current

    // WebView 句柄：网页内可后退时返回键先退网页，而不是直接退出本页
    var webView: WebView? by remember { mutableStateOf(null) }
    // canGoBack 不是 Compose 状态，网页内跳转不会触发重组，得在导航回调里主动记录
    var canGoBack by remember { mutableStateOf(false) }
    BackHandler(enabled = canGoBack) { webView?.goBack() }

    // 代理覆盖是否已由本页设置（退出时据此还原；App 内只有本页用 WebView，全局覆盖无外溢）
    var proxyApplied by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (proxyApplied && WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance()
                    .clearProxyOverride(ContextCompat.getMainExecutor(context)) {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.javdb_unblock_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_arrow_left),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onDone) {
                        Text(text = stringResource(CoreR.string.javdb_unblock_done))
                    }
                },
            )
        },
    ) { innerPadding ->
        when {
            // 没配站点网址时给提示，而不是留一块永远空白的 WebView
            startUrl.isBlank() ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    Text(
                        text = stringResource(CoreR.string.javdb_unblock_no_url),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            else ->
                AndroidView(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    factory = { ctx ->
                        WebView(ctx)
                            .apply {
                                // Cloudflare 的质询脚本是 JS，验证状态存在 DOM storage 里，两者都必须开
                                settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                                clearCache(true)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                // 刻意不改 UA：cf_clearance 要和过盾时的 UA 绑定，后续抓取请求复用的是
                                // WebView 自己的 UA，动它反而对不上
                                webViewClient =
                                    object : WebViewClient() {
                                        override fun doUpdateVisitedHistory(
                                            view: WebView,
                                            url: String?,
                                            isReload: Boolean,
                                        ) {
                                            super.doUpdateVisitedHistory(view, url, isReload)
                                            canGoBack = view.canGoBack()
                                        }

                                        override fun onPageStarted(
                                            view: WebView,
                                            url: String?,
                                            favicon: Bitmap?,
                                        ) {
                                            AppLog.d("JavDB 过盾页开始加载：%s", url)
                                        }

                                        override fun onPageFinished(view: WebView, url: String?) {
                                            url ?: return
                                            val cookies = CookieManager.getInstance().getCookie(url)
                                            onPageFinished(
                                                url,
                                                cookies,
                                                view.settings.userAgentString,
                                            )
                                            // 同步出的 Cookie 已由 ScraperCookieJar 落盘，这里只管 WebView 自己的
                                            CookieManager.getInstance().flush()
                                        }

                                        override fun onReceivedError(
                                            view: WebView,
                                            request: WebResourceRequest,
                                            error: WebResourceError,
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            // 子资源失败很常见（部分 CDN 被墙），只关心主文档加载不出来
                                            if (request.isForMainFrame) {
                                                AppLog.w(
                                                    "JavDB 过盾页加载失败：%s（%s）",
                                                    request.url,
                                                    error.description,
                                                )
                                            }
                                        }

                                        override fun onReceivedHttpError(
                                            view: WebView,
                                            request: WebResourceRequest,
                                            errorResponse: WebResourceResponse,
                                        ) {
                                            super.onReceivedHttpError(
                                                view,
                                                request,
                                                errorResponse,
                                            )
                                            if (request.isForMainFrame) {
                                                AppLog.w(
                                                    "JavDB 过盾页 HTTP 错误：%s（%d）",
                                                    request.url,
                                                    errorResponse.statusCode,
                                                )
                                            }
                                        }
                                    }
                            }
                            .also { webView = it }
                    },
                    onRelease = {
                        webView = null
                        it.destroy()
                    },
                )
        }
    }

    // 关键顺序：先给 WebView 配好代理、代理生效后再发起加载。
    // 若先加载后配代理，首个文档请求会直连，在 DNS 被污染的网络里连到无关 IP 上长时间卡住，
    // 表现就是页面一直空白、也没有任何回调。
    LaunchedEffect(webView, proxyAddress) {
        val view = webView ?: return@LaunchedEffect
        // effect 会因 key 变化重跑，页面已开始加载时不重复发起
        if (view.url != null) return@LaunchedEffect
        val rule = proxyRule(proxyAddress)
        val supported = WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)
        AppLog.i(
            "JavDB 过盾页准备加载：%s（代理：%s，WebView 代理支持：%s）",
            startUrl,
            proxyAddress.ifBlank { "直连" },
            supported,
        )
        if (!rule.isNullOrBlank() && supported) {
            proxyApplied = true
            ProxyController.getInstance()
                .setProxyOverride(
                    ProxyConfig.Builder().addProxyRule(rule).build(),
                    ContextCompat.getMainExecutor(context),
                ) { view.loadUrl(startUrl) }
        } else {
            view.loadUrl(startUrl)
        }
    }
}

/**
 * 把抓取代理地址转成 WebView 的代理规则。
 *
 * 规则里带 `http(s)://` 前缀时只对对应协议生效，https 站点会绕开代理直连，
 * 因此 http 代理要剥掉前缀；socks5:// 是全局代理、原样保留，裸 host:port 交给 WebView 自行解析。
 */
private fun proxyRule(address: String): String? {
    val trimmed = address.trim()
    if (trimmed.isEmpty()) return null
    val schemeEnd = trimmed.indexOf("://")
    return if (trimmed.startsWith("socks", ignoreCase = true)) {
        trimmed
    } else if (schemeEnd > 0) {
        trimmed.substring(schemeEnd + 3).takeIf { it.isNotEmpty() }
    } else {
        trimmed
    }
}

@PreviewScreenSizes
@Composable
private fun JavDbUnblockScreenPreview() {
    FindroidTheme {
        JavDbUnblockScreenLayout(
            startUrl = "https://javdb.example",
            proxyAddress = "",
            onPageFinished = { _, _, _ -> },
            onDone = {},
        )
    }
}
