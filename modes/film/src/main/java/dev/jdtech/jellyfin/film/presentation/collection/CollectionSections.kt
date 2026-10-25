package dev.jdtech.jellyfin.film.presentation.collection

import androidx.annotation.StringRes
import dev.jdtech.jellyfin.core.Constants
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.CollectionSection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.UiText

/**
 * 把类型混杂的条目按电影 / 剧集 / 单集拆成固定顺序的分组，空分组不展示。
 *
 * 合集详情、收藏与下载页的数据来源不同（合集内条目 / 收藏条目 / 本地下载），但展示结构一致，故共用。
 *
 * 内部要遍历整批条目，调用方应放在后台线程执行。
 */
internal fun buildCollectionSections(items: List<FindroidItem>): List<CollectionSection> =
    listOfNotNull(
        buildSection(
            Constants.FAVORITE_TYPE_MOVIES,
            CoreR.string.movies_label,
            items.filterIsInstance<FindroidMovie>(),
        ),
        buildSection(
            Constants.FAVORITE_TYPE_SHOWS,
            CoreR.string.shows_label,
            items.filterIsInstance<FindroidShow>(),
        ),
        buildSection(
            Constants.FAVORITE_TYPE_EPISODES,
            CoreR.string.episodes_label,
            items.filterIsInstance<FindroidEpisode>(),
        ),
    )

private fun buildSection(
    id: Int,
    @StringRes nameRes: Int,
    items: List<FindroidItem>,
): CollectionSection? {
    if (items.isEmpty()) return null
    return CollectionSection(id, UiText.StringResource(nameRes), items)
}
