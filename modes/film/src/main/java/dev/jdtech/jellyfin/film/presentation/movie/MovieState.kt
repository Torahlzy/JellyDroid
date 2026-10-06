package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.models.FindroidItemImage
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.ItemMetadataEdit
import dev.jdtech.jellyfin.models.VideoMetadata

data class MovieState(
    val movie: FindroidMovie? = null,
    val videoMetadata: VideoMetadata? = null,
    val actors: List<FindroidItemPerson> = emptyList(),
    val director: FindroidItemPerson? = null,
    val writers: List<FindroidItemPerson> = emptyList(),
    val displayExtraInfo: Boolean = false,
    /** 服务器上该条目的图片，供「删除服务器信息」弹窗列出确认。 */
    val itemImages: List<FindroidItemImage> = emptyList(),
    val isLoadingItemImages: Boolean = false,
    /** 图片列表加载失败的原因，非空时弹窗提示失败而不是「服务器上没有图片」。 */
    val itemImagesError: Exception? = null,
    /** 服务器上该条目的可编辑元数据，为 null 表示还没加载完成，供「编辑 nfo」弹窗回填。 */
    val itemMetadata: ItemMetadataEdit? = null,
    val isLoadingItemMetadata: Boolean = false,
    /** 元数据加载失败的原因，非空时弹窗提示失败而不是显示空表单。 */
    val itemMetadataError: Exception? = null,
    val error: Exception? = null,
) {
    /** 已下载到本机的文件路径：来源于本地来源，未下载时为 null。 */
    val localFilePath: String? =
        movie?.sources
            ?.filter { it.type == FindroidSourceType.LOCAL }
            ?.mapNotNull { it.localFilePath.trim().takeIf { path -> path.isNotEmpty() } }
            ?.distinct()
            ?.joinToString(separator = "\n")
            ?.takeIf { it.isNotEmpty() }

    /** 服务器上的文件路径：来源于远程来源，无可用路径时为 null。 */
    val remoteFilePath: String? =
        movie?.sources
            ?.filter { it.type == FindroidSourceType.REMOTE }
            ?.mapNotNull { it.remoteFilePath.trim().takeIf { path -> path.isNotEmpty() } }
            ?.distinct()
            ?.joinToString(separator = "\n")
            ?.takeIf { it.isNotEmpty() }
}
