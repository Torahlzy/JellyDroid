package dev.jdtech.jellyfin.setup.presentation.accounts

import java.util.UUID

sealed interface AccountsAction {
    data object Refresh : AccountsAction
    data object Back : AccountsAction
    data class SelectServer(val serverId: String) : AccountsAction
    data class SelectUser(val userId: UUID) : AccountsAction
    data class DeleteServer(val serverId: String) : AccountsAction
    data class DeleteUser(val userId: UUID) : AccountsAction
}
