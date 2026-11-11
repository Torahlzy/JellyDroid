package dev.jdtech.jellyfin.presentation.proxy

/** 代理连通性测试的界面状态；[error] 是可直接展示的技术原因。 */
data class ProxyTestState(
    val isTesting: Boolean = false,
    val durationMillis: Long? = null,
    val isInvalidAddress: Boolean = false,
    val error: String? = null,
) {
    val isFinished: Boolean
        get() = durationMillis != null || isInvalidAddress || error != null
}
