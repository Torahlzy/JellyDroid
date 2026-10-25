package dev.jdtech.jellyfin.core.scraper

/**
 * 判断一段文本是不是抓错的垃圾内容（压制 / 转码工具水印、文件名、网址、广告话术）。
 *
 * 站点与影片文件元数据里常混进「SVFI 7.31.6 Professional - Steam」这类水印，抓取器会当成片名
 * 或简介收进来，写进 nfo 后媒体库里就是一条乱码。判定参照 JavSP 的 `javsp/text.py`：
 * 只认明确的特征词，且标题比简介更保守——简介多一条「没有中日文且很短即视为水印」的规则，
 * 标题不能套用（否则 `Tokyo Hot` 这类英文片名会被误删）。
 */
object JunkTextDetector {
    /** 像文件名的文本：媒体服务器识别不出信息时会把文件名当成标题 / 简介写进 nfo。 */
    private val FILENAME_PATTERN =
        Regex("""\.(?:mp4|mkv|avi|wmv|mov|m2ts|rmvb|ts|flv)\b""", RegexOption.IGNORE_CASE)

    /** 明显不属于影片内容的词：压制 / 转码工具名与水印、网址、广告。 */
    private val JUNK_PATTERNS =
        listOf(FILENAME_PATTERN) +
            listOf(
                    """\bsvfi\b""",
                    """\bsteam\b""",
                    """\btopaz\b""",
                    """\bhandbrake\b""",
                    """\bffmpeg\b""",
                    """\bavsynth\b""",
                    """\badobe\b""",
                    """\bx26[45]\b""",
                    """\bnvidia\b""",
                    """\bvegas pro\b""",
                    """\bmegui\b""",
                    """audio copy""",
                    """video copy""",
                    """professional\s*-""",
                    """https?://""",
                    """www\.""",
                    "最新地址",
                    "最新网址",
                    "永久地址",
                    "永久网址",
                    "请访问",
                    "請訪問",
                    "qq群",
                    "微信群",
                    "公众号",
                    "更多精彩",
                    "免费观看",
                    "免費觀看",
                )
                .map { Regex(it, RegexOption.IGNORE_CASE) }

    /** 完全没有中文 / 日文、又短于这个长度的简介，基本都是抓取时混进来的水印。 */
    private const val PURE_ASCII_JUNK_LENGTH = 60

    /** 中日文及全角标点，用来判断一段文本是不是完全没有中文 / 日文内容。 */
    private val CJK_PATTERN =
        Regex("[\\u3000-\\u303f\\u3040-\\u309f\\u30a0-\\u30ff\\u4e00-\\u9fff\\uff00-\\uffef]")

    /** 标题是否为垃圾内容：只按明确特征判定，避免把英文片名误删。 */
    fun isJunkTitle(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.isNotEmpty() && JUNK_PATTERNS.any { it.containsMatchIn(trimmed) }
    }

    /** 简介是否为垃圾内容：明确特征之外，还认为「没有中日文且很短」的文本是水印。 */
    fun isJunkPlot(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        if (JUNK_PATTERNS.any { it.containsMatchIn(trimmed) }) return true
        return trimmed.length < PURE_ASCII_JUNK_LENGTH && !CJK_PATTERN.containsMatchIn(trimmed)
    }
}
