package dev.jdtech.jellyfin.models

import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * 只带名称与图片的元数据实体，服务于「类别」「制片公司」这类浏览入口。
 *
 * 它们在服务端同样是条目，但不具备播放、播放进度等语义，因此不复用 [FindroidItem] 层级。
 */
data class FindroidNamedItem(
    val id: UUID,
    val name: String,
    val images: FindroidImages,
)

fun BaseItemDto.toFindroidNamedItem(repository: JellyfinRepository): FindroidNamedItem {
    return FindroidNamedItem(
        id = id,
        name = name.orEmpty(),
        images = toFindroidImages(repository),
    )
}
