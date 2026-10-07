package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.spacings
import org.jellyfin.sdk.model.api.ImageType

// 缩略图统一按横图比例裁框：竖图用 ContentScale.Fit 完整显示，不会被裁掉上下
private const val THUMBNAIL_ASPECT_RATIO = 3f / 2f

/**
 * 可全屏查看的图片缩略图。
 *
 * [model] 同时接受服务器图片的地址与本地抓取图片的字节，交给 Coil 自行判断；点击图片打开全屏查看。
 * [label] 非空时在图片下方显示一行说明文字（如「海报」「背景」），宽度不足时省略。
 * [onUpload] 交给全屏查看界面，用于单独上传当前查看的这一张；服务器上已有的图片留空。
 */
@Composable
fun PreviewableImageThumbnail(
    model: Any,
    label: String?,
    modifier: Modifier = Modifier,
    onUpload: (() -> Unit)? = null,
    isUploading: Boolean = false,
) {
    var isViewing by remember { mutableStateOf(false) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = model,
            contentDescription = label,
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(THUMBNAIL_ASPECT_RATIO)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { isViewing = true },
            contentScale = ContentScale.Fit,
        )
        if (label != null) {
            Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }

    if (isViewing) {
        ImageViewerDialog(
            model = model,
            contentDescription = label,
            onDismiss = { isViewing = false },
            onUpload = onUpload,
            isUploading = isUploading,
        )
    }
}

/** 图片类型名称：常见类型走本地化文案，其余类型退回服务端的英文枚举名。 */
@Composable
fun imageTypeLabel(imageType: ImageType): String =
    when (imageType) {
        ImageType.PRIMARY -> stringResource(CoreR.string.image_type_primary)
        ImageType.BACKDROP -> stringResource(CoreR.string.image_type_backdrop)
        ImageType.LOGO -> stringResource(CoreR.string.image_type_logo)
        ImageType.THUMB -> stringResource(CoreR.string.image_type_thumb)
        ImageType.BANNER -> stringResource(CoreR.string.image_type_banner)
        ImageType.ART -> stringResource(CoreR.string.image_type_art)
        else -> imageType.serialName
    }
