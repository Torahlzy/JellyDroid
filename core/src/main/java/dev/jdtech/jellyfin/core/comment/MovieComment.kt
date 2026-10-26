package dev.jdtech.jellyfin.core.comment

/**
 * 影片的一条短评（来自抓取站点的短评列表）。
 *
 * [userName] 是站点展示的用户名，站点本身已做脱敏（形如 `mo***a`），这里原样保留，
 * 不做任何还原；[content] 为用户发言正文。
 */
data class MovieComment(
    /** 站点上的短评 id，供列表稳定复用与去重。 */
    val id: String,
    val userName: String,
    val content: String,
    /** 发表时间，站点给的是 `yyyy-MM-dd` 形式的纯文本，可能为空。 */
    val date: String? = null,
    /** 点赞数，站点没给时为 null。 */
    val likes: Int? = null,
)
