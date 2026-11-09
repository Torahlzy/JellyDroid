package dev.jdtech.jellyfin.core.subtitle

import dev.jdtech.jellyfin.logging.AppLog

/**
 * 把 SRT / VTT 字幕正文解析成按时间排序的 [SubtitleCue] 列表，供播放页的对照面板逐句取用。
 *
 * 只做「取句」这一件事，不参与上传与时长判断（时长判断见 [SubtitleTiming]）：
 * 一边播放一边按播放位置查当前是哪一句，解析结果整段缓存在内存里。
 * 编码兜底复用 [SubtitleTextDecoder]，与时间戳判断、时间行规范化保持同一份文本。
 */
object SubtitleCueParser {

    private const val BOM = '\uFEFF'

    /** 块之间用空行分隔（SRS 规范与 VTT 一致），这里把连续空行也视为一个分隔。 */
    private val BLOCK_SEPARATOR = Regex("""\n[ \t]*\n+""")

    /**
     * 行内标记：VTT 的 `<i>` / `<v 某人>`，ASS 的 `{\an8}` 之类。
     *
     * **花括号必须写成 `[{]` / `[}]`，不能用 `\{` / `\}`**：Android 用的是 ICU 正则实现，
     * 结尾裸 `}` 会被当成量词起始并在 `Regex` 初始化时抛 `PatternSyntaxException`（桌面 JVM 不报错，
     * 于是这类问题只在真机上炸，且会连累整个解析器类初始化失败）。字符类是两边都合法的写法。
     */
    private val INLINE_TAG = Regex("""<[^>]*>|[{][^}]*[}]""")

    /** ASS 的强制换行标记，解析到面板里换成空格（面板每行最多显示两行）。 */
    private const val ASS_LINE_BREAK = "\\N"

    /** 「解析出 0 条 cue」时打印的文件开头长度，够看出是 SRT / ASS / HTML。 */
    private const val SNIPPET_LENGTH = 120

    /** 解析 [bytes]，编码全都解不出文本时返回空表（调用方按「无文本」处理）。 */
    fun parse(bytes: ByteArray): List<SubtitleCue> {
        val decoded = SubtitleTextDecoder.decode(bytes)
        if (decoded == null) {
            AppLog.w("字幕解析失败：%d 字节，UTF-8 / GBK / Big5 均无法解码", bytes.size)
            return emptyList()
        }
        val cues = parse(decoded.text)
        // 解析结果为空是最难从界面上看出来的故障（面板只是文本区全空），这里把能定位的信息一次打全
        if (cues.isEmpty()) {
            AppLog.w(
                "字幕解析出 0 条 cue：编码 %s，%d 字节，开头为「%s」",
                decoded.charset.name(),
                bytes.size,
                decoded.text.take(SNIPPET_LENGTH).replace("\n", "\\n"),
            )
            return cues
        }
        AppLog.d(
            "字幕解析成功：编码 %s，%d 字节 → %d 条 cue（%d ~ %d ms），首条「%s」",
            decoded.charset.name(),
            bytes.size,
            cues.size,
            cues.first().startMs,
            cues.last().endMs,
            cues.first().text.take(20),
        )
        return cues
    }

    /** 解析已经解码好的正文：按空行切块，只认带箭头时间行的块（自动跳过 VTT 的头部 / NOTE / STYLE）。 */
    fun parse(text: String): List<SubtitleCue> =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .removePrefix(BOM.toString())
            .split(BLOCK_SEPARATOR)
            .mapNotNull(::parseBlock)
            .sortedBy { it.startMs }

    private fun parseBlock(block: String): SubtitleCue? {
        val lines = block.lines().map { it.trimEnd().removePrefix(BOM.toString()) }
        // 站点变体会把箭头写成 `->`；台词里也可能有 `->`，所以还要求同一行至少有两个时间戳，
        // 与时间行规范化（SubtitleTiming.normalizeTimingLines）保持同一判据
        val timingIndex =
            lines.indexOfFirst { line ->
                line.contains("->") && SubtitleTiming.TIMESTAMP.findAll(line).count() >= 2
            }
        if (timingIndex < 0) return null
        val timestamps = SubtitleTiming.TIMESTAMP.findAll(lines[timingIndex]).toList()
        if (timestamps.size < 2) return null
        val text =
            lines.drop(timingIndex + 1)
                .joinToString(separator = " ") { line -> cleanText(line) }
                .trim()
        if (text.isEmpty()) return null
        return SubtitleCue(
            startMs = SubtitleTiming.timestampMillis(timestamps[0]),
            endMs = SubtitleTiming.timestampMillis(timestamps[1]),
            text = text,
        )
    }

    private fun cleanText(line: String): String =
        INLINE_TAG.replace(line, "").replace(ASS_LINE_BREAK, " ").trim()
}
