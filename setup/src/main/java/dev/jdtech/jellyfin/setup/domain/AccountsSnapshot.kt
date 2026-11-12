package dev.jdtech.jellyfin.setup.domain

import dev.jdtech.jellyfin.models.ServerWithAddresses
import dev.jdtech.jellyfin.models.User
import java.util.UUID

/** 已保存账号的本地快照，用于首屏显示与后台刷新结果的整体替换。 */
data class AccountsSnapshot(
    val servers: List<ServerWithAddresses>,
    val users: List<User>,
    val currentServerId: String?,
    val currentUserId: UUID?,
)
