package dev.jdtech.jellyfin.models

/**
 * 可按其浏览与筛选的元数据维度，对应 nfo 中被 Jellyfin 扫描入库的字段。
 *
 * 各维度的可选值来源不同（分别由 `JellyfinRepository` 的 `getGenres` / `getStudios` / `getPersons` /
 * `getFilterValues` 提供），但「列出全部可选值 → 点击某个值 → 列出该值下的条目」这一流程完全一致，
 * 因此导航与筛选结果页按维度统一处理。
 */
enum class MetadataFacet {
    /** 类别，nfo 的 `<genre>`。 */
    GENRE,

    /** 标签，nfo 的 `<tag>`。 */
    TAG,

    /** 制片公司，nfo 的 `<studio>`。 */
    STUDIO,

    /** 分级，nfo 的 `<mpaa>`。 */
    OFFICIAL_RATING,

    /** 年份，nfo 的 `<year>` / `<premiered>`。 */
    YEAR,

    /** 演员，nfo 的 `<actor>`。 */
    ACTOR,

    /** 导演，nfo 的 `<director>`。 */
    DIRECTOR,

    /** 编剧，nfo 的 `<credits>`。 */
    WRITER,

    ;

    /** 人物维度对应服务端 `/Persons` 的 `personTypes` 取值；非人物维度为 null。 */
    val personType: String?
        get() =
            when (this) {
                ACTOR -> "Actor"
                DIRECTOR -> "Director"
                WRITER -> "Writer"
                else -> null
            }
}
