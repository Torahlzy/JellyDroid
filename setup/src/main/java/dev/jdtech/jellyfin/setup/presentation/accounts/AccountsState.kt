package dev.jdtech.jellyfin.setup.presentation.accounts

import dev.jdtech.jellyfin.models.ServerWithAddresses
import dev.jdtech.jellyfin.models.User
import java.util.UUID

data class AccountsState(
    val servers: List<ServerWithAddresses> = emptyList(),
    val users: List<User> = emptyList(),
    val publicUsers: List<User> = emptyList(),
    val currentServerId: String? = null,
    val currentUserId: UUID? = null,
    /** 离线模式开关值进场即读，保证头部首帧可见。 */
    val offlineMode: Boolean = false,
    val isLoading: Boolean = false,
    val hasChangedServer: Boolean = false,
    val error: Exception? = null,
)
