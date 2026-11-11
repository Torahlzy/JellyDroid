package dev.jdtech.jellyfin.setup.presentation.accounts

import java.util.UUID

sealed interface AccountsAction {
    data object Refresh : AccountsAction
    data object Back : AccountsAction

    /** 切换离线模式并重启界面以切换全局依赖。 */
    data object ToggleOfflineMode : AccountsAction
    data class SelectServer(val serverId: String) : AccountsAction
    data class SelectUser(val userId: UUID) : AccountsAction
    data class DeleteServer(val serverId: String) : AccountsAction
    data class DeleteUser(val userId: UUID) : AccountsAction
}
