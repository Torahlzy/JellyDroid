package dev.jdtech.jellyfin.setup.presentation.accounts

sealed interface AccountsEvent {
    data object Back : AccountsEvent
    data object Restart : AccountsEvent
}
