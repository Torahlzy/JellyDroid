package dev.jdtech.jellyfin.settings.domain

/**
 * 提供各抓取站点当前实际生效的地址（站点 key → 地址），供设置页与电影详情页的「抓取网址」弹窗回填。
 *
 * 站点清单与回退规则的唯一来源在实现里：包含 `ScraperSite` 枚举中的全部站点，字幕站附在末尾；
 * 两个页面都从这里取，避免各自维护一份清单。实现在 `:core`：除了用户运行时填写的地址，
 * 还要把打包时配置的地址算进来。`:settings` 不能反向依赖 `:core`，因此接口定义在这里、由上层注入实现。
 */
interface ScrapeSiteUrlsProvider {
    /** 未配置的站点值为空串；顺序由实现按站点枚举给出，字幕站附在末尾。 */
    fun effectiveUrls(): Map<String, String>
}
