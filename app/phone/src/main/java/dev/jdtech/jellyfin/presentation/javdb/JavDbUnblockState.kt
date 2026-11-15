package dev.jdtech.jellyfin.presentation.javdb

/**
 * 过盾页状态。
 *
 * [isSyncing] 表示点「完成」后正在把网页里的登录会话搬回 App（期间禁用按钮、显示进度）；
 * [message] 是同步失败时给用户的提示：只有从「站点账号登录」进来（网页版登录）且网页里
 * 还没登录才会出现，过盾入口不会打扰用户。
 */
data class JavDbUnblockState(
    val isSyncing: Boolean = false,
    val message: String? = null,
    /** 同步结束、可以返回上一页；导航由界面观察它触发，ViewModel 不持有导航。 */
    val isFinished: Boolean = false,
)
