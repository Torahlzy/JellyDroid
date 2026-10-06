package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.utils.parallaxLayoutModifier

// 背景图加载完成前用于占位、以及避免异常宽高比导致布局失真的取值范围
private const val DEFAULT_BACKDROP_ASPECT_RATIO = 16f / 9f
private const val MIN_BACKDROP_ASPECT_RATIO = 1.2f
private const val MAX_BACKDROP_ASPECT_RATIO = 2.6f

@Composable
fun ItemHeader(
    item: FindroidItem,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    showLogo: Boolean = false,
    fadeToBackground: Boolean = true,
    fitBackdrop: Boolean = false,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val context = LocalContext.current
    var backdropAspectRatio by remember(item.id) { mutableStateOf<Float?>(null) }
    // 首选宽幅背景图：分集用自身剧照，其余用自身 backdrop
    val preferredBackdrop =
        when (item) {
            is FindroidEpisode -> item.images.primary
            else -> item.images.backdrop
        }
    // 首选图缺失时用任意可用图片补位，避免头部整块空白
    var backdropUri = preferredBackdrop ?: item.coverUri(Direction.HORIZONTAL)
    // 只有展示自身宽幅背景图时才按图片实际宽高自适应头部高度；
    // 兜底图（如竖版海报）沿用默认比例，避免头部高度随任意图片尺寸跳变
    val fitImageBackdrop = fitBackdrop && preferredBackdrop != null

    // 本地图片没有 scheme，需要手动补上 filesDir 前缀；无图时保持为空
    if (backdropUri != null && backdropUri.scheme == null) {
        backdropUri =
            Uri.Builder()
                .appendEncodedPath("${context.filesDir}")
                .appendEncodedPath(backdropUri.path)
                .build()
    }

    ItemHeaderBase(
        item = item,
        modifier = modifier,
        showLogo = showLogo,
        fadeToBackground = fadeToBackground,
        // 需要完整显示背景图时按图片实际宽高比计算高度，加载完成前先用默认比例占位
        backdropAspectRatio =
            if (fitBackdrop) backdropAspectRatio ?: DEFAULT_BACKDROP_ASPECT_RATIO else null,
        backdropImage = {
            AsyncImage(
                model = backdropUri,
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize()
                        .parallaxLayoutModifier(scrollState = scrollState, rate = 2),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                contentScale = ContentScale.Crop,
                // 加载成功后按图片实际宽高比调整容器高度，保证图片完整显示
                onSuccess = { success ->
                    if (fitImageBackdrop) {
                        val image = success.result.image
                        if (image.height > 0) {
                            backdropAspectRatio =
                                (image.width.toFloat() / image.height.toFloat())
                                    .coerceIn(
                                        MIN_BACKDROP_ASPECT_RATIO,
                                        MAX_BACKDROP_ASPECT_RATIO,
                                    )
                        }
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
fun ItemHeader(
    item: FindroidItem,
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    showLogo: Boolean = false,
    fadeToBackground: Boolean = true,
    fitBackdrop: Boolean = false,
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val context = LocalContext.current
    var backdropAspectRatio by remember(item.id) { mutableStateOf<Float?>(null) }
    // 首选宽幅背景图：分集用自身剧照，季用所属剧集宽幅图，其余用自身 backdrop
    val preferredBackdrop =
        when (item) {
            is FindroidEpisode -> item.images.primary
            is FindroidSeason -> item.images.showBackdrop
            else -> item.images.backdrop
        }
    // 首选图缺失时用任意可用图片补位，避免头部整块空白
    var backdropUri = preferredBackdrop ?: item.coverUri(Direction.HORIZONTAL)
    // 只有展示自身宽幅背景图时才按图片实际宽高自适应头部高度；
    // 兜底图（如竖版海报）沿用默认比例，避免头部高度随任意图片尺寸跳变
    val fitImageBackdrop = fitBackdrop && preferredBackdrop != null

    // 本地图片没有 scheme，需要手动补上 filesDir 前缀；无图时保持为空
    if (backdropUri != null && backdropUri.scheme == null) {
        backdropUri =
            Uri.Builder()
                .appendEncodedPath("${context.filesDir}")
                .appendEncodedPath(backdropUri.path)
                .build()
    }

    ItemHeaderBase(
        item = item,
        modifier = modifier,
        showLogo = showLogo,
        fadeToBackground = fadeToBackground,
        // 需要完整显示背景图时按图片实际宽高比计算高度，加载完成前先用默认比例占位
        backdropAspectRatio =
            if (fitBackdrop) backdropAspectRatio ?: DEFAULT_BACKDROP_ASPECT_RATIO else null,
        backdropImage = {
            AsyncImage(
                model = backdropUri,
                contentDescription = null,
                modifier =
                    Modifier.fillMaxSize()
                        .parallaxLayoutModifier(lazyListState = lazyListState, rate = 2),
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceContainer),
                contentScale = ContentScale.Crop,
                // 加载成功后按图片实际宽高比调整容器高度，保证图片完整显示
                onSuccess = { success ->
                    if (fitImageBackdrop) {
                        val image = success.result.image
                        if (image.height > 0) {
                            backdropAspectRatio =
                                (image.width.toFloat() / image.height.toFloat())
                                    .coerceIn(
                                        MIN_BACKDROP_ASPECT_RATIO,
                                        MAX_BACKDROP_ASPECT_RATIO,
                                    )
                        }
                    }
                },
            )
        },
        content = content,
    )
}

@Composable
private fun ItemHeaderBase(
    item: FindroidItem,
    modifier: Modifier = Modifier,
    showLogo: Boolean = false,
    fadeToBackground: Boolean = true,
    backdropAspectRatio: Float? = null,
    backdropImage: @Composable (() -> Unit),
    content: @Composable (BoxScope.() -> Unit) = {},
) {
    val backgroundColor = MaterialTheme.colorScheme.background

    val logoUri =
        when (item) {
            is FindroidEpisode -> item.images.showLogo
            else -> item.images.logo
        }

    Box(
        modifier =
            modifier
                .then(
                    if (backdropAspectRatio != null) {
                        // 有宽高比时按比例算高度，图片完整显示、不裁切
                        Modifier.aspectRatio(backdropAspectRatio)
                    } else {
                        // 不按宽高比展示时保持原有行为：高度不小于 288dp，内容多时自动增高
                        Modifier.heightIn(min = 288.dp)
                    }
                )
                .clipToBounds()
    ) {
        backdropImage()
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Color.Black.copy(alpha = 0.1f))
            // 底部渐隐用于给叠加在图片上的内容做背景；图片下方直接接内容时可关闭
            if (fadeToBackground) {
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, backgroundColor),
                            startY = 0f,
                        )
                )
            }
        }
        content()
        if (showLogo) {
            AsyncImage(
                model = logoUri,
                contentDescription = null,
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(MaterialTheme.spacings.default)
                        .height(100.dp)
                        .fillMaxWidth(),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
