package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem

enum class Direction {
    HORIZONTAL,
    VERTICAL,
}

@Composable
fun ItemPoster(
    item: FindroidItem,
    direction: Direction,
    modifier: Modifier = Modifier,
    // 可选宽高比覆盖；不传时按方向使用默认值（横向 16:9，纵向 2:3）
    aspectRatio: Float? = null,
) {
    val context = LocalContext.current
    var imageUri = item.coverUri(direction)

    // 离线（本地）图片没有 scheme，需要手动补上 filesDir 前缀
    if (imageUri != null && imageUri.scheme == null) {
        imageUri =
            Uri.Builder()
                .appendEncodedPath("${context.filesDir}")
                .appendEncodedPath(imageUri.path)
                .build()
    }

    val imageAspectRatio = aspectRatio ?: if (direction == Direction.HORIZONTAL) 1.77f else 0.66f

    AsyncImage(
        model = imageUri,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier =
            modifier
                .aspectRatio(imageAspectRatio)
                .background(MaterialTheme.colorScheme.surfaceContainer),
    )
}

/**
 * 按方向挑选封面地址：竖图优先 primary，横图优先 backdrop。
 *
 * 服务器上缺失首选图片时，用另一种图片临时补位，避免出现空白封面。
 */
private fun FindroidItem.coverUri(direction: Direction): Uri? =
    when (direction) {
        // 横图：电影 / 剧集 / 合集 / 媒体库等优先自身宽幅 backdrop，缺失时退回所属剧集宽幅图，
        // 再不行才用竖图；分集自带的 primary 通常就是 16:9 剧照，比剧集 backdrop 更贴合卡片，
        // 故单独优先 primary
        Direction.HORIZONTAL ->
            if (this is FindroidEpisode) {
                images.primary ?: images.showBackdrop ?: images.backdrop
            } else {
                images.backdrop ?: images.showBackdrop ?: images.primary ?: images.showPrimary
            }
        // 竖图：分集优先所属剧集海报，其余用自身海报，缺失时退回宽幅图兜底
        Direction.VERTICAL ->
            if (this is FindroidEpisode) {
                images.showPrimary ?: images.primary ?: images.backdrop
            } else {
                images.primary ?: images.backdrop
            }
    }
