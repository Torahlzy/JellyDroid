package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.VideoMetadata

data class MovieState(
    val movie: FindroidMovie? = null,
    val videoMetadata: VideoMetadata? = null,
    val actors: List<FindroidItemPerson> = emptyList(),
    val director: FindroidItemPerson? = null,
    val writers: List<FindroidItemPerson> = emptyList(),
    val displayExtraInfo: Boolean = false,
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
