package dev.jdtech.jellyfin.core.scraper

/**
 * 番号（DVD ID）识别，移植自 JavSP 的 `javsp/avid.py`。
 *
 * 用途只有一个：进入抓取前猜一个默认关键词填进输入框，用户可以随意改，
 * 因此这里只覆盖常见写法，不做 JavSP 那样为极端文件名兜底的处理。
 */
object AvidParser {
    /** 文件名里会干扰番号识别的噪声，取自 JavSP 的 `scanner.ignored_id_pattern`。 */
    private val IGNORE_PATTERN =
        Regex(
            listOf(
                    "(144|240|360|480|720|1080)[Pp]",
                    "[24][Kk]",
                    "\\w+2048\\.com",
                    "Carib(beancom)?",
                    "[^a-z\\d](f?hd|lt)[^a-z\\d]",
                )
                .joinToString("|"),
            RegexOption.IGNORE_CASE,
        )

    /** 同番号的分片 / 特殊后缀，形如 `ABC-123-cd1`。 */
    private val CD_POSTFIX = Regex("([-_]\\w|cd\\d)$", RegexOption.IGNORE_CASE)

    /**
     * 从文件路径或任意字符串中识别番号。
     *
     * 识别不到时返回空串：先用文件名匹配，仍失败再退回上一级目录名（JavSP 的做法）。
     */
    fun getDvdId(source: String): String {
        val segments = source.split('/', '\\').filter { it.isNotBlank() }
        val fileName = segments.lastOrNull()?.let { CD_POSTFIX.replace(it.substringBeforeLast('.'), "") }
            ?: return ""
        return matchDvdId(fileName).ifEmpty { matchDvdId(segments.dropLast(1).lastOrNull().orEmpty()) }
    }

    /** 按 JavSP 的正则顺序逐个尝试，命中即返回。 */
    private fun matchDvdId(stem: String): String {
        if (stem.isEmpty()) return ""
        val norm = IGNORE_PATTERN.replace(stem, "").uppercase()

        when {
            "FC2" in norm ->
                // FC2 的编号是 5-7 位数字，且可能夹着 PPV 之类的字样
                Regex("FC2[^A-Z\\d]{0,5}(PPV[^A-Z\\d]{0,5})?(\\d{5,7})", RegexOption.IGNORE_CASE)
                    .find(norm)
                    ?.let { return "FC2-" + it.groupValues[2] }
            "HEYDOUGA" in norm ->
                Regex("(HEYDOUGA)[-_]*(\\d{4})[-_]0?(\\d{3,5})", RegexOption.IGNORE_CASE)
                    .find(norm)
                    ?.let { return it.groupValues.drop(1).take(3).joinToString("-") }
            "GETCHU" in norm ->
                Regex("GETCHU[-_]*(\\d+)", RegexOption.IGNORE_CASE)
                    .find(norm)
                    ?.let { return "GETCHU-" + it.groupValues[1] }
            "GYUTTO" in norm ->
                Regex("GYUTTO-(\\d+)", RegexOption.IGNORE_CASE)
                    .find(norm)
                    ?.let { return "GYUTTO-" + it.groupValues[1] }
            else -> {
                // 文件名里带域名时，先去掉域名再匹配，往往才能露出番号
                val noDomain = Regex("\\w{3,10}\\.(COM|NET|APP|XYZ)", RegexOption.IGNORE_CASE).replace(norm, "")
                if (noDomain != norm) {
                    matchDvdId(noDomain).takeIf { it.isNotEmpty() }?.let { return it }
                }
                // 缩写成 hey 的 heydouga 番号由三段组成，必须先于两段式番号匹配
                Regex("(?:HEY)[-_]*(\\d{4})[-_]0?(\\d{3,5})", RegexOption.IGNORE_CASE)
                    .find(norm)
                    ?.let { return "heydouga-" + it.groupValues.drop(1).take(2).joinToString("-") }
                // MUGEN 的番号很乱，且形如 MK3D2DBD 会干扰普通番号，需提前匹配
                Regex("(MKB?D)[-_]*(S\\d{2,3})|(MK3D2DBD|S2M|S2MBD)[-_]*(\\d{2,3})", RegexOption.IGNORE_CASE)
                    .find(norm)
                    ?.let { match ->
                        return if (match.groupValues[1].isNotEmpty()) {
                            match.groupValues[1] + "-" + match.groupValues[2]
                        } else {
                            match.groupValues[3] + "-" + match.groupValues[4]
                        }
                    }
                // 带 z 后缀的番号（如 IBW-123z）
                Regex("(IBW)[-_](\\d{2,5}z)", RegexOption.IGNORE_CASE)
                    .find(norm)
                    ?.let { return it.groupValues[1] + "-" + it.groupValues[2] }
                // 普通番号，优先匹配带分隔符的（如 ABC-123）
                Regex("([A-Z]{2,10})[-_](\\d{2,5})", RegexOption.IGNORE_CASE)
                    .find(norm)
                    ?.let { return it.groupValues[1] + "-" + it.groupValues[2] }
                // 东热的 red / sky / ex 三个系列不带分隔符，数字范围收紧以降低误匹配
                Regex("(RED[01]\\d\\d|SKY[0-3]\\d\\d|EX00[01]\\d)", RegexOption.IGNORE_CASE)
                    .find(norm)
                    ?.let { return it.groupValues[1] }
                // 再按「丢了分隔符」的写法试一次（如 ABC123）
                Regex("([A-Z]{2,})(\\d{2,5})", RegexOption.IGNORE_CASE)
                    .find(norm)
                    ?.let { return it.groupValues[1] + "-" + it.groupValues[2] }
            }
        }

        // TMA 的番号（如 T28-557）
        Regex("(T[23]8[-_]\\d{3})").find(norm)?.let { return it.groupValues[1] }
        // 东热的 n / k 系列
        Regex("(N\\d{4}|K\\d{4})", RegexOption.IGNORE_CASE).find(norm)?.let { return it.groupValues[1] }
        // R18-XXX
        Regex("R18-?\\d{3}", RegexOption.IGNORE_CASE).find(norm)?.let { return it.value }
        // 纯数字番号（无码影片）
        Regex("(\\d{6}[-_]\\d{2,3})").find(norm)?.let { return it.groupValues[1] }
        // 少数番号由 ')(' 分隔
        if (")(" in norm) {
            matchDvdId(norm.replace(")(", "-")).takeIf { it.isNotEmpty() }?.let { return it }
        }
        return ""
    }
}
