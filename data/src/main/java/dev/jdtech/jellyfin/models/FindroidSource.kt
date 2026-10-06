package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaSourceInfo

data class FindroidSource(
    val id: String,
    val name: String,
    val type: FindroidSourceType,
    val path: String,
    val size: Long,
    val mediaStreams: List<FindroidMediaStream>,
    /** 下载中的任务 id（WorkManager 的 UUID 字符串），未在下载中时为 null。 */
    val downloadTaskId: String? = null,
    /** 服务器上的文件路径，仅用于展示；[path] 在远程模式下是播放地址，不代表文件位置。 */
    val remoteFilePath: String = "",
    /** 已下载到本机的文件路径，仅用于展示；未下载时为空。 */
    val localFilePath: String = "",
)

suspend fun MediaSourceInfo.toFindroidSource(
    jellyfinRepository: JellyfinRepository,
    itemId: UUID,
    includePath: Boolean = false,
): FindroidSource {
    val path =
        when (protocol) {
            MediaProtocol.FILE -> {
                try {
                    if (includePath) jellyfinRepository.getStreamUrl(itemId, id.orEmpty()) else ""
                } catch (_: Exception) {
                    ""
                }
            }
            MediaProtocol.HTTP -> this.path.orEmpty()
            else -> ""
        }
    return FindroidSource(
        id = id.orEmpty(),
        name = name.orEmpty(),
        type = FindroidSourceType.REMOTE,
        path = path,
        size = size ?: 0,
        mediaStreams =
            mediaStreams?.map { it.toFindroidMediaStream(jellyfinRepository) } ?: emptyList(),
        // 服务器返回的原始路径（局域网文件 / 直链地址），仅用于展示
        remoteFilePath = this.path.orEmpty(),
    )
}

suspend fun FindroidSourceDto.toFindroidSource(
    serverDatabaseDao: ServerDatabaseDao
): FindroidSource {
    return FindroidSource(
        id = id,
        name = name,
        type = type,
        path = path,
        size = File(path).length(),
        mediaStreams =
            serverDatabaseDao.getMediaStreamsBySourceId(id).map { it.toFindroidMediaStream() },
        downloadTaskId = downloadTaskId,
        // 已下载条目：path 即本地文件路径
        localFilePath = path,
    )
}

enum class FindroidSourceType {
    REMOTE,
    LOCAL,
}

/**
 * 按播放端的选源规则挑出实际用于播放的来源：优先已下载的本地来源，否则取列表首个，列表为空时返回 null。
 *
 * 详情页展示与播放端共用这一规则，避免两处各写一套导致「界面上展示的路径」与「实际播放的那个源」对不上。
 *
 * 归到 `:data` 而非 `:core`：本规则同时被 `:modes:film` 与 `:player:local` 使用，而后者不依赖 `:core`，
 * 两者的公共依赖只有 `:data`。
 */
fun List<FindroidSource>.pickPlaybackSource(): FindroidSource? =
    firstOrNull { it.type == FindroidSourceType.LOCAL } ?: firstOrNull()
