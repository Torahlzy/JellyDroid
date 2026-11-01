package dev.jdtech.jellyfin.core.subtitle

/**
 * 从字幕正文里读时间戳，用来判断这份字幕是不是配这部片子。
 *
 * 只解析时间戳（ASCII 部分），因此不关心文件编码：站点给的 UTF-8 直接读，
 * 即使实际是 GBK / Big5，时间戳那几行也不会被破坏。
 */
internal object SubtitleTiming {

    /** 兼容 SRT / VTT 的 `HH:MM:SS,mmm` / `HH:MM:SS.mmm` 与 ASS 的 `H:MM:SS.cc`。 */
    private val TIMESTAMP = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")

    /** 字幕里最晚的一个时间点（毫秒）；正文里一个时间戳都没有时返回 null。 */
    fun lastTimestampMillis(text: String): Long? {
        val match = TIMESTAMP.findAll(text).lastOrNull() ?: return null
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
}
