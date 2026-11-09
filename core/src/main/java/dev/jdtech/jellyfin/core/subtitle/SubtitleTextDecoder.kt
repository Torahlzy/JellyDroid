package dev.jdtech.jellyfin.core.subtitle

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * 字幕正文的共享解码：UTF-8 → GBK → Big5 严格解码，返回文本与实际采用的编码。
 *
 * 字幕站的编码不统一，UTF-8 遇到非法字节要退到 GBK / Big5，否则中文台词全是乱码。
 * 时间戳解析、cue 解析与时间行规范化必须基于同一份文本：各写一套解码时，
 * 同一份字幕会在「时长差」「面板文本」「上传字节」上得出互相矛盾的结论。
 */
internal object SubtitleTextDecoder {

    /** [charset] 供时间行规范化后按原编码写回字节。 */
    data class DecodedText(val text: String, val charset: Charset)

    /** 站点给的字幕多为 UTF-8，日系片源常见 GBK（简中）与 Big5（繁中）。 */
    private val CHARSETS: List<Charset> =
        listOfNotNull(
            Charsets.UTF_8,
            runCatching { Charset.forName("GBK") }.getOrNull(),
            runCatching { Charset.forName("Big5") }.getOrNull(),
        )

    /** 严格解码：任一种编码能无错解出整段字节就采用它；全失败返回 null。 */
    fun decode(bytes: ByteArray): DecodedText? {
        CHARSETS.forEach { charset ->
            val decoder =
                charset
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            try {
                return DecodedText(
                    text = decoder.decode(ByteBuffer.wrap(bytes)).toString(),
                    charset = charset,
                )
            } catch (_: CharacterCodingException) {
                // 换下一种编码继续试
            }
        }
        return null
    }
}
