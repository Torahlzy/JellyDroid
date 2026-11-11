package dev.jdtech.jellyfin.presentation.settings.cache

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.cache.CacheCategory
import dev.jdtech.jellyfin.cache.CacheUsage
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.core.presentation.cache.CacheAction
import dev.jdtech.jellyfin.core.presentation.cache.CacheState
import dev.jdtech.jellyfin.core.presentation.cache.CacheViewModel
import dev.jdtech.jellyfin.presentation.settings.components.settingsInteraction
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun CacheScreen(navigateBack: () -> Unit, viewModel: CacheViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(true) { viewModel.onAction(CacheAction.Refresh) }
    CacheScreenLayout(state, viewModel::onAction, navigateBack)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CacheScreenLayout(state: CacheState, onAction: (CacheAction) -> Unit, navigateBack: () -> Unit) {
    var selectedCategory by rememberSaveable { mutableStateOf<CacheCategory?>(null) }
    var showQuota by rememberSaveable { mutableStateOf(false) }
    var showImageQuota by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    fun size(bytes: Long) = Formatter.formatFileSize(context, bytes)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cache_title)) },
                navigationIcon = { IconButton(onClick = navigateBack) {
                    Icon(painterResource(R.drawable.ic_arrow_left), contentDescription = stringResource(R.string.navigate_back))
                } },
                actions = { TextButton(enabled = !state.isLoading, onClick = { onAction(CacheAction.Refresh) }) {
                    Text(stringResource(R.string.cache_refresh))
                } },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(MaterialTheme.spacings.default),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            if (state.isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (state.error != null) item {
                Text(stringResource(R.string.cache_operation_failed), color = MaterialTheme.colorScheme.error)
            }
            state.usage?.let { usage ->
                item {
                    CacheQuotaCard(
                        usage = usage,
                        isImage = true,
                        enabled = !state.isLoading,
                        onClick = { showImageQuota = true },
                    )
                }
                item {
                    CacheQuotaCard(
                        usage = usage,
                        isImage = false,
                        enabled = !state.isLoading,
                        onClick = { showQuota = true },
                    )
                }
                item {
                    CacheUsageCard(stringResource(R.string.cache_local_videos), size(usage.downloadedBytes), stringResource(R.string.cache_videos_protected))
                }
                items(CacheCategory.entries, key = { it.name }) { category ->
                    CacheUsageCard(
                        stringResource(category.titleResource()), size(usage.categories[category] ?: 0),
                        if (category == CacheCategory.IMAGES) stringResource(R.string.cache_images_hint) else null,
                        onClear = { selectedCategory = category },
                        enabled = !state.isLoading && (usage.categories[category] ?: 0) > 0,
                    )
                }
                if (usage.protectedBytes > 0) item {
                    Text(stringResource(R.string.cache_protected_summary, size(usage.protectedBytes)), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    selectedCategory?.let { category ->
        AlertDialog(
            onDismissRequest = { selectedCategory = null },
            title = { Text(stringResource(R.string.cache_clear_title, stringResource(category.titleResource()))) },
            text = { Text(stringResource(R.string.cache_clear_message)) },
            confirmButton = { TextButton(onClick = { selectedCategory = null; onAction(CacheAction.Clear(category)) }) {
                Text(stringResource(R.string.cache_clear), color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { selectedCategory = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (showImageQuota) CacheQuotaDialog(
        title = stringResource(R.string.cache_image_quota),
        hint = stringResource(R.string.cache_image_quota_restart_hint),
        current = state.usage?.imageQuotaBytes?.div(1024 * 1024)?.toInt() ?: 1024,
        onDismiss = { showImageQuota = false },
        onSave = { showImageQuota = false; onAction(CacheAction.SetImageQuota(it)) },
    )
    if (showQuota) CacheQuotaDialog(
        title = stringResource(R.string.cache_shared_quota),
        hint = null,
        current = state.usage?.quotaBytes?.div(1024 * 1024)?.toInt() ?: 1024,
        onDismiss = { showQuota = false },
        onSave = { showQuota = false; onAction(CacheAction.SetQuota(it)) },
    )
}

@Composable
private fun CacheQuotaCard(
    usage: CacheUsage,
    isImage: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val usedBytes = if (isImage) usage.imageCacheBytes else usage.usedBytes
    val quotaBytes = if (isImage) usage.imageQuotaBytes else usage.quotaBytes
    val interaction = settingsInteraction(enabled)
    Surface(
        onClick = onClick,
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
        Column(
            modifier = Modifier.padding(MaterialTheme.spacings.default),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            Text(
                stringResource(if (isImage) R.string.cache_image_quota else R.string.cache_shared_quota),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(
                R.string.cache_usage_summary,
                Formatter.formatFileSize(context, usedBytes),
                Formatter.formatFileSize(context, quotaBytes),
            ))
            LinearProgressIndicator(
                progress = { (usedBytes.toFloat() / quotaBytes).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(if (isImage) R.string.cache_image_quota_hint else R.string.cache_quota_hint),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!isImage && usedBytes > quotaBytes) {
                Text(stringResource(R.string.cache_in_use_hint), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CacheUsageCard(
    title: String,
    value: String,
    description: String? = null,
    onClear: (() -> Unit)? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.widthIn(max = MaterialTheme.spacings.settingsMaxWidth).fillMaxWidth()) {
        Column(Modifier.padding(MaterialTheme.spacings.default)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (onClear != null) TextButton(onClick = onClear, enabled = enabled) { Text(stringResource(R.string.cache_clear), color = MaterialTheme.colorScheme.error) }
            }
            Text(value, style = MaterialTheme.typography.bodyLarge)
            if (description != null) Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CacheQuotaDialog(
    title: String,
    hint: String?,
    current: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(current.toString()) }
    val number = value.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column {
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall)
            androidx.compose.foundation.layout.FlowRow { listOf(512, 1024, 2048, 4096, 8192).forEach { preset ->
                TextButton(onClick = { value = preset.toString() }) { Text(if (preset < 1024) stringResource(R.string.cache_preset_mb, preset) else stringResource(R.string.cache_preset_gb, preset / 1024)) }
            } }
            OutlinedTextField(value = value, onValueChange = { value = it }, singleLine = true,
                label = { Text(stringResource(R.string.cache_quota_mb)) }, isError = number == null || number <= 0,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        } },
        confirmButton = { TextButton(enabled = number != null && number > 0, onClick = { number?.let(onSave) }) { Text(stringResource(R.string.confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun CacheCategory.titleResource(): Int = when (this) {
    CacheCategory.IMAGES -> R.string.cache_images
    CacheCategory.COMMENTS -> R.string.cache_comments
    CacheCategory.SUBTITLES -> R.string.cache_subtitles
    CacheCategory.PLAYER -> R.string.cache_player
    CacheCategory.TRANSLATIONS -> R.string.cache_translations
    CacheCategory.WEB -> R.string.cache_web
}

@PreviewScreenSizes
@Composable
private fun CacheScreenPreview() {
    FindroidTheme {
        CacheScreenLayout(
            CacheState(
                usage = CacheUsage(
                    quotaBytes = 1073741824,
                    imageQuotaBytes = 1073741824,
                    imageCacheBytes = 268435456,
                    downloadedBytes = 0,
                    categories = emptyMap(),
                    protectedBytes = 0,
                ),
            ),
            {},
            {},
        )
    }
}
