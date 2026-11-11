package dev.jdtech.jellyfin.settings.presentation.settings

data class SettingsFileEditState(
    val initialText: String = "",
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false,
    val isSaving: Boolean = false,
    val error: Exception? = null,
)
