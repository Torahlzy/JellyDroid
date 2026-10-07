package dev.jdtech.jellyfin.models

/**
 * 服务端给出的可用筛选值。
 *
 * 标签、分级、年份这些维度没有独立的实体接口，只能一次性取回全部可选值，因此单独建模。
 */
data class FilterValues(
    /** 标签按名称升序排列。 */
    val tags: List<String> = emptyList(),
    /** 分级，服务端给出的原始顺序。 */
    val officialRatings: List<String> = emptyList(),
    /** 年份按降序排列，最新的排在最前。 */
    val years: List<Int> = emptyList(),
)
