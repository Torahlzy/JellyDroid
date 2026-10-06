package dev.jdtech.jellyfin.models

enum class SortBy(val sortString: String, val isSelectable: Boolean = true) {
    NAME("SortName"),
    IMDB_RATING("CommunityRating"),
    PARENTAL_RATING("CriticRating"),
    DATE_ADDED("DateCreated"),
    DATE_PLAYED("DatePlayed"),
    RELEASE_DATE("PremiereDate"),
    RANDOM("Random"),
    // 剧集库把「播放日期」替换为服务端的 SeriesDatePlayed 时内部使用，不面向用户，故不进排序菜单
    SERIES_DATE_PLAYED("SeriesDatePlayed", isSelectable = false);

    companion object {
        val defaultValue = NAME

        /** 排序菜单中供用户选择的排序项，排除仅内部使用的 SERIES_DATE_PLAYED。 */
        val selectableValues: List<SortBy> by lazy { entries.filter { it.isSelectable } }

        fun fromString(string: String): SortBy {
            return try {
                valueOf(string)
            } catch (_: IllegalArgumentException) {
                defaultValue
            }
        }
    }
}
