package dev.jdtech.jellyfin.models

import android.net.Uri
import java.util.UUID
import org.jellyfin.sdk.model.api.ImageInfo
import org.jellyfin.sdk.model.api.ImageType

// 弹窗里只作勾选确认，取缩小图即可，避免为确认一张背景图下载数 MB 的原图
private const val THUMBNAIL_MAX_WIDTH = 300

/**
 * 服务器上条目的单张图片。
 *
 * [imageIndex] 是多图类型（如背景图）内的序号，单图类型为 null。
 */
data class FindroidItemImage(
    val imageType: ImageType,
    val imageIndex: Int?,
    val uri: Uri,
)

fun ImageInfo.toFindroidItemImage(itemId: UUID, baseUrl: String): FindroidItemImage {
    val uriBuilder =
        Uri.parse(baseUrl)
            .buildUpon()
            .appendEncodedPath("items/$itemId/Images/${imageType.serialName}")

    imageIndex?.let { uriBuilder.appendEncodedPath(it.toString()) }
    imageTag?.let { uriBuilder.appendQueryParameter("tag", it) }
    uriBuilder.appendQueryParameter("maxWidth", THUMBNAIL_MAX_WIDTH.toString())

    return FindroidItemImage(
        imageType = imageType,
        imageIndex = imageIndex,
        uri = uriBuilder.build(),
    )
}
