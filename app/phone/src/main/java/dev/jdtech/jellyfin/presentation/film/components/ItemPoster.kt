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
import dev.jdtech.jellyfin.models.FindroidMovie

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
    var imageUri = item.images.primary

    when (direction) {
        Direction.HORIZONTAL -> {
            if (item is FindroidMovie) imageUri = item.images.backdrop
        }
        Direction.VERTICAL -> {
            when (item) {
                is FindroidEpisode -> imageUri = item.images.showPrimary
            }
        }
    }

    // Ugly workaround to append the files directory when loading local images
    if (imageUri?.scheme == null) {
        imageUri =
            Uri.Builder()
                .appendEncodedPath("${context.filesDir}")
                .appendEncodedPath(imageUri?.path)
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
