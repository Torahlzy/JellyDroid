package dev.jdtech.jellyfin.core.scraper

/**
 * 站点标题的清理工具，移植自 JavSP 的 `func.remove_trail_actor_in_title` 与各站点对 `MovieInfo.title` 的处理。
 *
 * 三个站点都会把番号、女优名混进标题，且拼法各不相同（JavBus 番号在前、名字在后，JavDB 番号单独成元素，
 * Jav321 还把番号与女优名在 `<small>` 里重复一遍），而 nfo 标题只需要「番号 + 片名」，因此统一在这里剥掉。
 */

/** 女优名前允许出现的分隔符白名单，取自 JavSP；只列常见符号，避免按 Unicode 范围匹配误伤片名。 */
private const val ACTOR_DELIMITERS = "-xX &·,;　＆・，；"

/** 去掉拼在片名前面的番号（JavBus 的 `h3` 形如 `SSIS-001 片名 女优名`）。匹配不到就原样返回。 */
internal fun stripNumberPrefix(title: String, number: String?): String {
    val prefix = number?.trim().orEmpty()
    if (prefix.isEmpty() || !title.startsWith(prefix, ignoreCase = true)) return title
    return title.substring(prefix.length).trimStart()
}

/**
 * 去掉标题尾部的女优名，规则与 JavSP 完全一致：只有「1~3 个分隔符 + 女优名」重复到标题结尾时才截断。
 *
 * 站点把女优名拼在片名后面时用得到；匹配不上就原样返回——宁可留着多余名字，也不能误删片名本身。
 */
internal fun stripTrailingActors(title: String, actors: List<String>): String {
    val names = actors.mapNotNull { it.trim().takeIf { text -> text.isNotEmpty() } }
    if (names.isEmpty() || title.isBlank()) return title
    val alternatives = names.joinToString("|") { Regex.escape(it) }
    // matchEntire 已保证整串匹配，因此不再写尾部的 `$` 锚点（也省去在字符串里转义 `$`）
    val pattern = Regex("^(.*?)([$ACTOR_DELIMITERS]{1,3}($alternatives))+")
    val stripped = pattern.matchEntire(title)?.groupValues?.get(1)?.trim()
    return stripped?.takeIf { it.isNotEmpty() } ?: title
}
