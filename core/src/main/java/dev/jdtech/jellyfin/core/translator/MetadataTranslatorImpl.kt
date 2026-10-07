package dev.jdtech.jellyfin.core.translator

import dev.jdtech.jellyfin.di.ScraperOkHttpClient
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * 用 OpenAI 兼容的对话接口翻译文本。
 *
 * 一次请求翻完所有字段：调用方要翻几段就发几个键，字段之间不再各发一次、也不用等两轮往返；
 * 模型漏答哪一段，那一段就不出现在结果里。
 *
 * 借用抓取用的 OkHttp 客户端（只覆盖读超时与重定向，见 [translateClient]），**不套用设置里的抓取代理**：
 * 抓取代理是为境外站点准备的白名单入口，而大模型接口（默认 DeepSeek）通常直连更快，套上去只会把请求绕远。
 * 因此这里跟随系统网络设置。
 *
 * 目标是 DeepSeek 官方接口时额外优化两项，见 [isDeepSeekEndpoint]：关掉默认开启的思考模式
 * （翻译用不上推理，却要白等十几秒）、多字段时要求 JSON 输出（省得再从「以下是翻译：」里抠正文）。
 *
 * 读超时单独放宽到 [READ_TIMEOUT_SECONDS]：抓取用的 15 秒对整段简介的推理来说不够，
 * 但这只影响派生出来的客户端，不会改变抓取本身的超时。
 */
class MetadataTranslatorImpl
@Inject
constructor(
    @ScraperOkHttpClient private val baseClient: OkHttpClient,
    private val appPreferences: AppPreferences,
) : MetadataTranslator {

    override suspend fun translate(texts: Map<String, String>): Map<String, String> =
        withContext(Dispatchers.IO) {
            val settings = appPreferences.getTranslateSettings()
            val url = settings.url.trim()
            if (!settings.isConfigured) {
                throw TranslateException("翻译未配置：接口地址、密钥、模型都要填写")
            }
            // 空文本没有翻译的意义，直接不发，省得模型给它编一段出来
            val requested = texts.filterValues { it.isNotBlank() }
            if (requested.isEmpty()) return@withContext emptyMap()

            val request = buildRequest(url, requested, settings.apiKey.trim(), settings.model.trim())
            try {
                translateClient().newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        throw TranslateException("翻译接口返回 ${response.code}${describeError(body)}")
                    }
                    parseTranslations(responseContent(body), requested)
                }
            } catch (e: IOException) {
                throw TranslateException("连接翻译接口失败（$url）", e)
            }
        }

    /** 地址写错时 OkHttp 抛的是 IllegalArgumentException，转成能给用户看的说明。 */
    private fun buildRequest(
        url: String,
        texts: Map<String, String>,
        apiKey: String,
        model: String,
    ): Request {
        val deepSeek = isDeepSeekEndpoint(url)
        return try {
            Request.Builder()
                .url(url)
                .post(requestBody(texts, model, deepSeek).toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $apiKey")
                .build()
        } catch (e: IllegalArgumentException) {
            throw TranslateException("翻译接口地址不合法：$url", e)
        }
    }

    private fun requestBody(texts: Map<String, String>, model: String, deepSeek: Boolean): String {
        // 多字段才需要 JSON 结构把各段对回各自的键；只有一个字段时正文就是译文，套一层反而多一次解析
        val jsonOutput = texts.size > 1
        val body =
            JSONObject()
                .put("model", model)
                // DeepSeek 声明不支持 temperature（传了不报错也不生效），别家靠它保证结果可复现
                .put("temperature", 0)
                .put("max_tokens", MAX_TOKENS)
                .put("messages", messages(texts, jsonOutput))
        if (deepSeek) {
            // 思考模式默认开启、强度 high，翻译这种活会白等十几秒
            body.put("thinking", JSONObject().put("type", "disabled"))
            // JSON 输出：让正文只剩译文本身；prompt 里必须出现 json 字样并给出样例，否则接口报错
            if (jsonOutput) body.put("response_format", JSONObject().put("type", "json_object"))
        }
        return body.toString()
    }

    private fun messages(texts: Map<String, String>, jsonOutput: Boolean): JSONArray {
        val userContent =
            if (jsonOutput) {
                JSONObject().apply { texts.forEach { (key, value) -> put(key, value) } }.toString()
            } else {
                texts.values.first()
            }
        val systemPrompt = if (jsonOutput) JSON_SYSTEM_PROMPT else PLAIN_SYSTEM_PROMPT
        return JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt))
            .put(JSONObject().put("role", "user").put("content", userContent))
    }

    /** 取第一段回复的正文；接口用 `error` 字段报错时把它转成可读原因。 */
    private fun responseContent(body: String): String {
        val json =
            try {
                JSONObject(body)
            } catch (e: JSONException) {
                throw TranslateException("翻译接口返回了无法解析的内容", e)
            }
        if (json.has("error")) throw TranslateException("翻译接口报错${describeError(body)}")
        val content =
            json.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.trim()
        if (content.isNullOrEmpty()) throw TranslateException("翻译接口没有返回译文")
        return content
    }

    /**
     * 解析译文：多字段时正文应是一个 json 对象，键与请求一致。
     *
     * 模型漏答的字段不会出现在结果里，调用方据此保留原文；一段都拿不到才算这次翻译失败。
     */
    private fun parseTranslations(
        content: String,
        requested: Map<String, String>,
    ): Map<String, String> {
        val cleaned = content.removeSurrounding("```json", "```").removeSurrounding("```").trim()
        val json = runCatching { JSONObject(cleaned) }.getOrNull()
        val translated =
            requested.keys
                .mapNotNull { key ->
                    json?.optString(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { key to it }
                }
                .toMap()
        if (translated.isNotEmpty()) return translated

        // 兜底：只有一个字段时正文本身就是译文（单字段不要求 JSON 输出）；模型仍然套了一层 json 的话
        // 取它唯一的值，免得把 {"title": "..."} 原样填进标题
        if (requested.size != 1) throw TranslateException("翻译接口没有按 json 结构返回译文")
        val value = json?.let(::firstJsonText) ?: cleaned
        return if (value.isBlank()) emptyMap() else mapOf(requested.keys.first() to value)
    }

    /** 取 json 对象里第一个非空的文本值。 */
    private fun firstJsonText(json: JSONObject): String? {
        val keys = json.keys()
        while (keys.hasNext()) {
            val value = json.optString(keys.next())
            if (value.isNotBlank()) return value
        }
        return null
    }

    /**
     * 从响应体里挖出 `error.message`；挖不到就截一段原文。
     *
     * 各家 OpenAI 兼容实现的错误结构不完全一致（有的在 `error` 对象里，有的直接给一行文字），
     * 因此兜底给原文比只报状态码更有用。
     */
    private fun describeError(body: String): String {
        val message =
            runCatching { JSONObject(body).optJSONObject("error")?.optString("message") }
                .getOrNull()
                .orEmpty()
        if (message.isNotBlank()) return "：$message"
        val snippet = body.trim().replace('\n', ' ').take(ERROR_SNIPPET_LENGTH)
        return if (snippet.isEmpty()) "" else "：$snippet"
    }

    /**
     * 派生一个只改了读超时与重定向的客户端；代理沿用客户端自身的系统默认设置，不读抓取代理。
     *
     * 抓取客户端是 `followRedirects(false)`（JavBus 的数据在重定向之前），但大模型接口走标准 HTTP 语义：
     * 用户填的地址若被网关跳转（如 http → https、少了补斜杠），跟随跳转才拿得到结果，
     * 否则只能报一句「翻译接口返回 301」。OkHttp 跨站跳转时会自动去掉 Authorization 头，密钥不会跟着走。
     */
    private fun translateClient(): OkHttpClient =
        baseClient
            .newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    private companion object {
        /** 整段简介的推理可能跑十几秒，抓取用的 15 秒读超时不够。 */
        private const val READ_TIMEOUT_SECONDS = 60L
        private const val ERROR_SNIPPET_LENGTH = 200

        /** JSON 输出被截断就等于这次翻译失败，给足余量；正常一段简介的译文只有几百 token。 */
        private const val MAX_TOKENS = 2048

        private const val DEEPSEEK_HOST = "deepseek.com"

        private const val PLAIN_SYSTEM_PROMPT =
            "Translate the following Japanese text into Simplified Chinese. " +
                "Leave non-Japanese text, names and numbers unchanged. " +
                "Reply with the translated text only, without any extra explanation."

        /** JSON 模式要求 prompt 里出现 "json" 字样并给出输出样例。 */
        private const val JSON_SYSTEM_PROMPT =
            "Translate each value of the given json object into Simplified Chinese, while leaving " +
                "non-Japanese text, names and numbers unchanged. Keep the keys unchanged. " +
                "Reply with a json object only, in the same shape, " +
                "e.g. {\"<key>\": \"<translation>\"}."

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /**
         * 是否为 DeepSeek 官方接口。
         *
         * `thinking` 与 `response_format` 是 DeepSeek 的扩展字段，OpenAI、Groq 这类兼容服务收到
         * 不认识的字段会直接返回 400，所以只在域名对得上时才发，别家维持原样。
         */
        private fun isDeepSeekEndpoint(url: String): Boolean {
            val host = runCatching { URI(url).host }.getOrNull()?.lowercase() ?: return false
            return host == DEEPSEEK_HOST || host.endsWith(".$DEEPSEEK_HOST")
        }
    }
}
