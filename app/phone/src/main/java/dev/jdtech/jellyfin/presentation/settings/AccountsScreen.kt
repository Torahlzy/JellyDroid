package dev.jdtech.jellyfin.presentation.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.presentation.settings.components.settingsInteraction
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.setup.presentation.accounts.AccountsAction
import dev.jdtech.jellyfin.setup.presentation.accounts.AccountsEvent
import dev.jdtech.jellyfin.setup.presentation.accounts.AccountsState
import dev.jdtech.jellyfin.setup.presentation.accounts.AccountsViewModel
import dev.jdtech.jellyfin.utils.ObserveAsEvents
import dev.jdtech.jellyfin.utils.restart

@Composable
fun AccountsScreen(
    navigateBack: () -> Unit,
    navigateToAddServer: () -> Unit,
    navigateToAddresses: (String) -> Unit,
    navigateToLogin: (String?) -> Unit,
    viewModel: AccountsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val restart = { (context as? Activity)?.restart(); Unit }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResume() }
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            AccountsEvent.Back -> navigateBack()
            AccountsEvent.Restart -> restart()
        }
    }
    BackHandler { viewModel.onAction(AccountsAction.Back) }
    AccountsScreenLayout(
        state = state,
        onAction = viewModel::onAction,
        onAddServer = navigateToAddServer,
        onAddresses = navigateToAddresses,
        onLogin = navigateToLogin,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountsScreenLayout(
    state: AccountsState,
    onAction: (AccountsAction) -> Unit,
    onAddServer: () -> Unit,
    onAddresses: (String) -> Unit,
    onLogin: (String?) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<AccountsAction?>(null) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = { onAction(AccountsAction.Back) }) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_left),
                            contentDescription = stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OfflineModeCard(
                offlineMode = state.offlineMode,
                onToggle = { onAction(AccountsAction.ToggleOfflineMode) },
                modifier =
                    Modifier.padding(
                        start = MaterialTheme.spacings.default,
                        end = MaterialTheme.spacings.default,
                        top = MaterialTheme.spacings.default,
                    ),
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(MaterialTheme.spacings.default),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (state.error != null) item {
                    Column {
                        Text(
                            stringResource(R.string.accounts_operation_failed),
                            color = MaterialTheme.colorScheme.error,
                        )
                        TextButton(
                            enabled = !state.isLoading,
                            onClick = { onAction(AccountsAction.Refresh) },
                        ) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
                item {
                    AccountsSectionTitle(
                        stringResource(R.string.accounts_servers),
                        stringResource(R.string.accounts_add_server),
                        !state.isLoading,
                        onAddServer,
                    )
                }
                items(state.servers, key = { it.server.id }) { server ->
                    ServerCard(
                        name = server.server.name,
                        address =
                            server.addresses.firstOrNull {
                                it.id == server.server.currentServerAddressId
                            }?.address.orEmpty(),
                        selected = server.server.id == state.currentServerId,
                        enabled = !state.isLoading,
                        onSelect = { onAction(AccountsAction.SelectServer(server.server.id)) },
                        onManageAddresses = { onAddresses(server.server.id) },
                        onDelete = { pendingDelete = AccountsAction.DeleteServer(server.server.id) },
                    )
                }
                item {
                    AccountsSectionTitle(
                        title = stringResource(R.string.accounts_users),
                        action = stringResource(R.string.accounts_add_user),
                        enabled = !state.isLoading && state.currentServerId != null,
                        onClick = { onLogin(null) },
                    )
                }
                items(state.users, key = { it.id.toString() }) { user ->
                    AccountCard(
                        title = user.name,
                        subtitle = null,
                        selected = user.id == state.currentUserId,
                        enabled = !state.isLoading,
                        onSelect = { onAction(AccountsAction.SelectUser(user.id)) },
                        onDelete = { pendingDelete = AccountsAction.DeleteUser(user.id) },
                    )
                }
                items(state.publicUsers, key = { "public_${it.id}" }) { user ->
                    AccountCard(
                        title = user.name,
                        subtitle = stringResource(R.string.accounts_login_required),
                        selected = false,
                        enabled = !state.isLoading,
                        onSelect = { onLogin(user.name) },
                    )
                }
            }
        }
    }
    if (pendingDelete != null) AlertDialog(
        onDismissRequest = { pendingDelete = null },
        title = { Text(stringResource(R.string.accounts_remove_title)) },
        text = { Text(stringResource(R.string.accounts_remove_message)) },
        confirmButton = { TextButton(onClick = { pendingDelete?.let(onAction); pendingDelete = null }) { Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** 离线模式常驻头部：开关值进场即可用，不随列表滚动或异步加载出现。 */
@Composable
private fun OfflineModeCard(
    offlineMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = settingsInteraction(true)
    Surface(
        onClick = onToggle,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = interaction.tonalElevation,
        interactionSource = interaction.source,
        modifier = modifier
            .widthIn(max = MaterialTheme.spacings.settingsMaxWidth)
            .fillMaxWidth()
            .then(interaction.modifier),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacings.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(SettingsR.string.offline_mode),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(SettingsR.string.offline_mode_summary),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(MaterialTheme.spacings.default))
            Switch(checked = offlineMode, onCheckedChange = null)
        }
    }
}

@Composable
private fun AccountsSectionTitle(
    title: String,
    action: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.widthIn(max = MaterialTheme.spacings.settingsMaxWidth).fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        TextButton(enabled = enabled, onClick = onClick) { Text(action) }
    }
}

/** 服务器卡片按三行排布：名称、当前地址、管理地址与移除。 */
@Composable
private fun ServerCard(
    name: String,
    address: String,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onManageAddresses: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = settingsInteraction(enabled)
    Surface(
        onClick = onSelect,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = interaction.tonalElevation,
        interactionSource = interaction.source,
        modifier = modifier
            .widthIn(max = MaterialTheme.spacings.settingsMaxWidth)
            .fillMaxWidth()
            .then(interaction.modifier),
    ) {
        Row(
            modifier = Modifier.padding(MaterialTheme.spacings.medium),
            verticalAlignment = Alignment.Top,
        ) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(Modifier.weight(1f).padding(start = MaterialTheme.spacings.medium)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(address, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                    TextButton(enabled = enabled, onClick = onManageAddresses) {
                        Text(stringResource(R.string.accounts_addresses))
                    }
                    TextButton(enabled = enabled, onClick = onDelete) {
                        Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountCard(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interaction = settingsInteraction(enabled)
    Surface(
        onClick = onSelect,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = interaction.tonalElevation,
        interactionSource = interaction.source,
        modifier = modifier
            .widthIn(max = MaterialTheme.spacings.settingsMaxWidth)
            .fillMaxWidth()
            .then(interaction.modifier),
    ) {
        Row(Modifier.padding(MaterialTheme.spacings.medium), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
            Column(Modifier.weight(1f).padding(start = MaterialTheme.spacings.medium)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            if (onDelete != null) {
                TextButton(enabled = enabled, onClick = onDelete) {
                    Text(stringResource(R.string.remove), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@PreviewScreenSizes
@Composable
private fun AccountsScreenPreview() {
    FindroidTheme { AccountsScreenLayout(AccountsState(), {}, {}, {}, {}) }
}
