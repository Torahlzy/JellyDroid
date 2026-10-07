package dev.jdtech.jellyfin.film.presentation.movie

import dev.jdtech.jellyfin.core.scraper.ScrapeProgress
import dev.jdtech.jellyfin.models.FindroidItemImage
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSource
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
    /** 抓取弹窗里预填的关键词，由文件名 / 标题识别出的番号，识别不到时为空串。 */
    val defaultScrapeKeyword: String = "",
    /** 是否正在抓取。 */
    val isScraping: Boolean = false,
    /** 抓取的执行步骤，逐条追加，供抓取弹窗展示进度。 */
    val scrapeSteps: List<ScrapeProgress> = emptyList(),
    /** 所有站点都没抓到，为 true 时抓取弹窗保留在界面上等用户确认。 */
    val scrapeFailed: Boolean = false,
    /** 抓到的元数据，非空表示表单已被抓取结果覆盖且尚未保存。 */
    val scrapedMetadata: ItemMetadataEdit? = null,
    /** 抓取使用的本地代理地址，留空表示直连；入口在抓取关键词弹窗里，不影响其它网络请求。 */
    val scrapeProxy: String = "",
    val error: Exception? = null,
    /**
     * 可选播放来源：服务器上的多个版本 + 本地已下载。
     *
     * 由 ViewModel 经与播放端相同的接口（`getMediaSources`）获取，保证两边顺序一致，
     * 用户选中的索引传回播放器时不会落到别的版本上。
     */
    val playbackSources: List<FindroidSource> = emptyList(),
) {
    /** 已下载到本机的文件路径：来源于本地来源，未下载时为 null。 */
    val localFilePath: String? =
        playbackSources
            .filter { it.type == FindroidSourceType.LOCAL }
            .mapNotNull { it.localFilePath.trim().takeIf { path -> path.isNotEmpty() } }
            .distinct()
            .joinToString(separator = "\n")
            .takeIf { it.isNotEmpty() }

    /** 服务器上的文件路径：来源于远程来源，无可用路径时为 null。 */
    val remoteFilePath: String? =
        playbackSources
            .filter { it.type == FindroidSourceType.REMOTE }
            .mapNotNull { it.remoteFilePath.trim().takeIf { path -> path.isNotEmpty() } }
            .distinct()
            .joinToString(separator = "\n")
            .takeIf { it.isNotEmpty() }

    /**
     * 影片文件的名字（不含扩展名），供「编辑 nfo」弹窗一键把标题填成文件名。
     *
     * 优先用已下载的本地文件，其次用服务器路径；两者都拿不到时为 null。
     */
    val fileName: String? =
        (localFilePath ?: remoteFilePath)
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.substringAfterLast('/')
            ?.substringAfterLast('\\')
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotEmpty() }
}
