package dev.jdtech.jellyfin.core.subtitle

/**
 * 字幕时间戳的解析与规范化，用来判断这份字幕是不是配这部片子。
 *
 * 站点给的时间行未必标准（实测 aisubs.app 写作「00：00：01.710-> 00：00：02.710」：
 * 全角冒号 + 单短横箭头），播放器与解析器都认不出，因此 [TIMESTAMP] 兼容全角冒号，
 * 下载时再由 [normalizeTimingLines] 统一写成标准 SRT / VTT 字节。
 */
internal object SubtitleTiming {

    /**
     * 兼容 SRT / VTT 的 `HH:MM:SS,mmm` / `HH:MM:SS.mmm` 与 ASS 的 `H:MM:SS.cc`，
     * 冒号同时接受 ASCII 与全角（站点字幕常见的中文输入法产物）。
     *
     * 对 [SubtitleCueParser] 也可见：解析每条 cue 的起止时间要用的就是这一份正则，
     * 不要再复制第二套（两处不一致时会出现「时长判断用一套、取句用另一套」的错位）。
     */
    internal val TIMESTAMP = Regex("""(\d{1,2})[:：](\d{2})[:：](\d{2})[,.](\d{1,3})""")

    /**
     * 正文里不超过 [maxMs] 的最晚时间点（毫秒）；一个时间戳都没有时返回 null。
     *
     * [maxMs] 用来挡掉「假时间码」：有些字幕工具会在末尾塞一条签名 cue，时间是 `23:59:58`
     * 甚至 `99:59:59`（实测 WhisperJAV 生成的繁中字幕就带这么一条），按它算会得到
     * 「字幕比视频长 20 小时」这种荒唐结论。调用方按视频时长传上界，签名行就自然被忽略。
     */
    fun lastTimestampMillis(text: String, maxMs: Long = Long.MAX_VALUE): Long? =
        TIMESTAMP.findAll(text).map(::timestampMillis).filter { it <= maxMs }.maxOrNull()

    /** 把 [TIMESTAMP] 的一次匹配换算成毫秒。 */
    internal fun timestampMillis(match: MatchResult): Long {
        val (hours, minutes, seconds, fraction) = match.destructured
        return hours.toLong() * 3_600_000 +
            minutes.toLong() * 60_000 +
            seconds.toLong() * 1_000 +
            fractionToMillis(fraction)
    }

    /** 小数位可能是 1~3 位：1 位是十分之一秒，2 位（ASS 的厘秒）是百分之一秒。 */
    private fun fractionToMillis(fraction: String): Long =
        when (fraction.length) {
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }

    /**
     * 把非标准时间行写成标准 SRT / VTT 字节，供上传、侧载、时长判断与解析共用同一份数据。
     *
     * 只改「含箭头且至少两个时间戳」的行：台词里的全角冒号必须原样保留，不能全文替换。
     * SRT 的小数分隔符统一成逗号（VTT 保留点号），箭头统一成 ` --> `。
     * 解码走 [SubtitleTextDecoder]，改写后按**原编码**写回，GBK / Big5 字幕同样能被规范化
     * 且不会把台词重新编码弄乱；三种编码都解不出时保持原字节，由 [TIMESTAMP] 兼容兜底。
     */
    fun normalizeTimingLines(bytes: ByteArray, format: String): ByteArray {
        val decoded = SubtitleTextDecoder.decode(bytes) ?: return bytes
        val text = decoded.text
        if (!TIMING_LINE.containsMatchIn(text)) return bytes
        val normalized =
            TIMING_LINE.replace(text) { match ->
                if (TIMESTAMP.findAll(match.value).count() < 2) return@replace match.value
                val line = ARROW.replace(match.value.replace('：', ':'), " --> ")
                if (format.equals(SRT_FORMAT, ignoreCase = true)) {
                    TIMESTAMP.replace(line) { timestamp -> timestamp.value.replace('.', ',') }
                } else {
                    line
                }
            }
        return if (normalized == text) bytes else normalized.toByteArray(decoded.charset)
    }

    /** 含箭头的一整行（不含换行符）：只在这个范围内改写，行分隔符原样保留。 */
    private val TIMING_LINE = Regex("""[^\r\n]*->[^\r\n]*""")

    /**
     * 单短横 / 双短横箭头连同两侧空白一起归一成 ` --> `。
     *
     * 箭头前的空白也必须吃掉：只匹配箭头及右侧时，标准时间行会被替换成双空格，
     * 于是每份合规字幕都被判为「有改动」而整份重新编码。空白只认空格与制表符，
     * 不用 `\s`，否则箭头后紧跟换行时会把行分隔符一起吃掉。
     */
    private val ARROW = Regex("""[ \t]*-{1,2}>[ \t]*""")

    private const val SRT_FORMAT = "srt"
}
