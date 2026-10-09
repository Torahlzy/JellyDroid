package dev.jdtech.jellyfin.models

import java.time.LocalDateTime

/**
 * 一条媒体条目上可编辑的 nfo 元数据。
 *
 * [name] 是唯一必填项；其余字段留空即表示清空服务器上的对应字段。
 * [actresses] / [directors] 是演职员里可编辑的部分，写回时按「所见即所得」整体替换，
 * 服务器上原有但表单里没有的演职员（如编剧）由仓库层保留。
 * 外部刮削 ID、锁定状态等不在这里的字段，由仓库层在写回时从服务器原样带回，编辑不会让它们丢失。
 */
data class ItemMetadataEdit(
    val name: String = "",
    val originalTitle: String = "",
    val overview: String = "",
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val actresses: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val studios: List<String> = emptyList(),
    val productionLocations: List<String> = emptyList(),
    /** 标语：Jellyfin 上单条目只会保留一条。 */
    val tagline: String = "",
    val officialRating: String = "",
    val productionYear: Int? = null,
    val premiereDate: LocalDateTime? = null,
    val communityRating: Float? = null,
)
