package dev.jdtech.jellyfin.core.subtitle

import dev.jdtech.jellyfin.logging.AppLog
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 把 SRT / VTT 字幕正文解析成按时间排序的 [SubtitleCue] 列表，供播放页的对照面板逐句取用。
 *
 * 只做「取句」这一件事，不参与上传与时长判断（时长判断见 [SubtitleTiming]）：
 * 一边播放一边按播放位置查当前是哪一句，解析结果整段缓存在内存里。
 */
object SubtitleCueParser {

    /**
     * 编码兜底顺序：站点给的字幕多为 UTF-8，但日系片源常见 GBK（简中）与 Big5（繁中）。
     *
     * 用「严格解码」逐个试：UTF-8 遇到非法字节会报错，才轮到 GBK / Big5。
     * 全部失败时返回 null（由调用方按「解码失败」处理：行文本留空但仍可上传）。
     */
    private val CHARSETS: List<Charset> =
        listOfNotNull(
            Charsets.UTF_8,
            runCatching { Charset.forName("GBK") }.getOrNull(),
            runCatching { Charset.forName("Big5") }.getOrNull(),
        )

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
        val decoded = decode(bytes)
        if (decoded == null) {
            AppLog.w("字幕解析失败：%d 字节，UTF-8 / GBK / Big5 均无法解码", bytes.size)
            return emptyList()
        }
        val cues = parse(decoded.text)
        // 解析结果为空是最难从界面上看出来的故障（面板只是文本区全空），这里把能定位的信息一次打全
        if (cues.isEmpty()) {
            AppLog.w(
                "字幕解析出 0 条 cue：编码 %s，%d 字节，开头为「%s」",
                decoded.charsetName,
                bytes.size,
                decoded.text.take(SNIPPET_LENGTH).replace("\n", "\\n"),
            )
            return cues
        }
        AppLog.d(
            "字幕解析成功：编码 %s，%d 字节 → %d 条 cue（%d ~ %d ms），首条「%s」",
            decoded.charsetName,
            bytes.size,
            cues.size,
            cues.first().startMs,
            cues.last().endMs,
            cues.first().text.take(20),
        )
        return cues
    }

    /** 解析已经解码好的正文：按空行切块，只认带 `-->` 时间行的块（自动跳过 VTT 的头部 / NOTE / STYLE）。 */
    fun parse(text: String): List<SubtitleCue> =
        text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .removePrefix(BOM.toString())
            .split(BLOCK_SEPARATOR)
            .mapNotNull(::parseBlock)
            .sortedBy { it.startMs }

    /** 带编码名的解码结果，解析日志要能看出最终用的是哪种编码。 */
    private class Decoded(val charsetName: String, val text: String)

    /** 严格解码：任一种编码能无错解出整段字节就采用它。 */
    private fun decode(bytes: ByteArray): Decoded? {
        CHARSETS.forEach { charset ->
            val decoder =
                charset
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            try {
                return Decoded(
                    charsetName = charset.name(),
                    text = decoder.decode(ByteBuffer.wrap(bytes)).toString(),
                )
            } catch (_: CharacterCodingException) {
                // 换下一种编码继续试
            }
        }
        return null
    }

    private fun parseBlock(block: String): SubtitleCue? {
        val lines = block.lines().map { it.trimEnd().removePrefix(BOM.toString()) }
        val timingIndex = lines.indexOfFirst { it.contains("-->") }
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
