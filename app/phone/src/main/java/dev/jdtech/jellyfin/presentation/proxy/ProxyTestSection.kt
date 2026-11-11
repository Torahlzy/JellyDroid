package dev.jdtech.jellyfin.presentation.proxy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.Motion
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 代理输入框下方的连通性测试。测的是 [address] 当前值（可能尚未保存），输入一变就清掉旧结果，
 * 避免展示针对上一个地址的结论。
 */
@Composable
fun ProxyTestSection(
    address: String,
    viewModel: ProxyTestViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(address) { viewModel.clearResult() }

    Column(modifier = modifier) {
        TextButton(onClick = { viewModel.test(address) }, enabled = !state.isTesting) {
            Text(
                text =
                    stringResource(
                        if (state.isTesting) CoreR.string.proxy_test_running
                        else CoreR.string.proxy_test_action
                    )
            )
        }
        AnimatedVisibility(
            visible = state.isFinished,
            enter = fadeIn(animationSpec = Motion.effectsSpec()),
            exit = fadeOut(animationSpec = Motion.effectsSpec()),
        ) {
            val duration = state.durationMillis
            val error = state.error
            val isFailed = state.isInvalidAddress || error != null
            val message =
                when {
                    state.isInvalidAddress -> stringResource(CoreR.string.proxy_test_invalid)
                    error != null -> stringResource(CoreR.string.proxy_test_failed, error)
                    duration != null ->
                        stringResource(
                            if (address.isBlank()) CoreR.string.proxy_test_success_direct
                            else CoreR.string.proxy_test_success_proxy,
                            duration,
                        )
                    else -> ""
                }
            Text(
                text = message,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacings.small),
                color =
                    if (isFailed) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
