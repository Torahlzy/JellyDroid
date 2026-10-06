package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidItemImage
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import org.jellyfin.sdk.model.api.ImageType

// 图片列表最高高度：条目图片数量不定，超出时内部滚动，避免弹窗撑满屏幕
private val IMAGE_LIST_MAX_HEIGHT = 220.dp

private val IMAGE_THUMBNAIL_SIZE = 48.dp

/**
 * 删除服务器封面的确认弹窗。
 *
 * 打开时列出服务器上的全部图片并默认全选，用户可逐张取消；列表加载失败时用 [errorText] 说明原因，
 * 而不是让用户以为服务器上没有图片。
 */
@Composable
fun DeleteItemImagesDialog(
    itemImages: List<FindroidItemImage>,
    isLoadingImages: Boolean,
    errorText: String?,
    onConfirm: (images: List<FindroidItemImage>) -> Unit,
    onDismiss: () -> Unit,
) {
    // 图片列表加载完成后重建默认选中项（全选）
    var selectedImages by remember(itemImages) { mutableStateOf(itemImages.toSet()) }

    // 同类图片多于一张时才显示序号，避免出现「背景」「背景 2」这种缺号
    val imageCountByType = remember(itemImages) { itemImages.groupingBy { it.imageType }.eachCount() }

    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.delete_item_images)) },
        text = {
            Column {
                Text(text = stringResource(CoreR.string.delete_server_info_message))
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                Text(
                    text = stringResource(CoreR.string.item_images_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                when {
                    // 加载失败时优先说明原因，避免让用户误以为服务器上真的没有图片
                    errorText != null ->
                        Text(
                            text = errorText,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    isLoadingImages ->
                        CircularProgressIndicator(
                            modifier =
                                Modifier.padding(MaterialTheme.spacings.small)
                                    .size(MaterialTheme.spacings.default),
                            strokeWidth = 2.dp,
                        )
                    itemImages.isEmpty() ->
                        Text(
                            text = stringResource(CoreR.string.item_images_empty),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    else ->
                        Column(
                            modifier =
                                Modifier.heightIn(max = IMAGE_LIST_MAX_HEIGHT)
                                    .verticalScroll(rememberScrollState())
                        ) {
                            itemImages.forEach { image ->
                                ImageOption(
                                    image = image,
                                    showIndex = (imageCountByType[image.imageType] ?: 0) > 1,
                                    checked = image in selectedImages,
                                    onCheckedChange = { checked ->
                                        selectedImages =
                                            if (checked) selectedImages + image
                                            else selectedImages - image
                                    },
                                )
                            }
                        }
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onConfirm(itemImages.filter { it in selectedImages }) },
                enabled = !isLoadingImages && selectedImages.isNotEmpty(),
            ) {
                Text(text = stringResource(CoreR.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
    )
}

@Composable
private fun ImageOption(
    image: FindroidItemImage,
    showIndex: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        AsyncImage(
            model = image.uri,
            contentDescription = null,
            modifier =
                Modifier.size(IMAGE_THUMBNAIL_SIZE)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(MaterialTheme.spacings.small))
        Text(
            text = imageTypeLabel(image = image, showIndex = showIndex),
            style = MaterialTheme.typography.bodyMedium,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
        )
    }
}

/** 图片名称：常见类型走本地化文案，其余类型退回服务端的英文枚举名。 */
@Composable
private fun imageTypeLabel(image: FindroidItemImage, showIndex: Boolean): String {
    val typeName =
        when (image.imageType) {
            ImageType.PRIMARY -> stringResource(CoreR.string.image_type_primary)
            ImageType.BACKDROP -> stringResource(CoreR.string.image_type_backdrop)
            ImageType.LOGO -> stringResource(CoreR.string.image_type_logo)
            ImageType.THUMB -> stringResource(CoreR.string.image_type_thumb)
            ImageType.BANNER -> stringResource(CoreR.string.image_type_banner)
            ImageType.ART -> stringResource(CoreR.string.image_type_art)
            else -> image.imageType.serialName
        }
    val imageIndex = image.imageIndex
    return if (showIndex && imageIndex != null) "$typeName ${imageIndex + 1}" else typeName
}

@Preview
@Composable
private fun DeleteItemImagesDialogPreview() {
    FindroidTheme {
        DeleteItemImagesDialog(
            itemImages =
                listOf(
                    FindroidItemImage(ImageType.PRIMARY, null, Uri.EMPTY),
                    FindroidItemImage(ImageType.BACKDROP, 0, Uri.EMPTY),
                    FindroidItemImage(ImageType.BACKDROP, 1, Uri.EMPTY),
                    FindroidItemImage(ImageType.LOGO, null, Uri.EMPTY),
                ),
            isLoadingImages = false,
            errorText = null,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
