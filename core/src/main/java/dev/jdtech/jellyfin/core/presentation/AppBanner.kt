package dev.jdtech.jellyfin.core.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.core.presentation.theme.Spacings

/**
 * 应用横幅：logo + 应用名文字。
 *
 * 应用名改用真实文本（取 app_name 资源）绘制，避免图内文字轮廓无法随改名联动。
 * logo 取 [R.drawable.ic_logo]，各 app 模块可用同名资源覆盖，手机版已覆盖为新版 JAV 图标；
 * [R.drawable.ic_banner] 是旧版水母 logo，留给 TV 端横幅使用。
 */
@Composable
fun AppBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.width(Spacings.small))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineLarge,
        )
    }
}
