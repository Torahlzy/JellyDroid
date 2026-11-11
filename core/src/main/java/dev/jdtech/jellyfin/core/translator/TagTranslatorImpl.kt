package dev.jdtech.jellyfin.core.translator

import dev.jdtech.jellyfin.core.scraper.MetadataTagNormalizer
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.cache.CacheCategory
import dev.jdtech.jellyfin.cache.CacheManager
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * 词典 + 大模型两段式的分类 / 标签翻译器。
 *
 * 译文缓存在共享磁盘缓存里（`原值 → 译文` 的 json），跨会话复用：同一批领域词在每部影片上都会
 * 出现，缓存命中后不再请求模型。词典命中的结果不落缓存——它是代码里写死的，不必占空间。
 */
class TagTranslatorImpl
@Inject
constructor(
    private val translator: MetadataTranslator,
    private val cacheManager: CacheManager,
) : TagTranslator {

    private val cacheFile get() = cacheManager.directory(CacheCategory.TRANSLATIONS).resolve("tags.json")

    override fun needsTranslation(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || TagGlossary.isSkipValue(trimmed)) return false
        if (TagGlossary.lookup(trimmed) != null) return true
        // 纯英文只认词典：TMDB 的噪声关键词交给模型会译出五花八门的值，保留原值更安全
        return MetadataTagNormalizer.containsKana(trimmed)
    }

    override suspend fun translate(values: Collection<String>): TagTranslationResult =
        // 整段放到 IO：缓存是磁盘中的一串 json，读写与解析都不该占主线程
        withContext(Dispatchers.IO) {
            val result = linkedMapOf<String, String>()
            val pending = linkedSetOf<String>()
            var cacheError: Exception? = null
            val cached =
                try {
                    loadCache()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.w(e, "读取分类/标签翻译缓存失败，继续翻译")
                    cacheError = e
                    emptyMap()
                }
            for (raw in values) {
                val value = raw.trim()
                if (value.isEmpty() || value in result) continue
                // 调用方传的是整个分类 + 标签列表，技术特征与缩写（`VR専用`、`ハイクオリティVR`）
                // 必须在这里再过滤一次：它们有的含假名，漏过去就会被引擎改写成别的词
                if (TagGlossary.isSkipValue(value)) continue
                val glossary = TagGlossary.lookup(value)
                if (glossary != null) {
                    result[value] = glossary
                    continue
                }
                cached[value]?.let {
                    result[value] = it
                    continue
                }
                // 词典与缓存都没命中：只剩含假名的日文值值得交给模型
                if (MetadataTagNormalizer.containsKana(value)) pending += value
            }
            if (pending.isEmpty()) return@withContext TagTranslationResult(result, cacheError)

            // 一次请求翻完整批：键用下标，值里带引号 / 换行也破坏不了 json 结构
            val requested =
                pending.withIndex().associate { (index, value) -> index.toString() to value }
            AppLog.i("翻译分类/标签：%d 个值", requested.size)
            val translated =
                try {
                    translator.translate(requested)
                } catch (e: CancellationException) {
                    // 协程取消不是翻译失败，照常向上抛
                    throw e
                } catch (e: Exception) {
                    // 引擎失败只影响没命中的未知词：已命中的词典 / 缓存译文照常返回，
                    // 失败本身交给调用方去计数与提示
                    AppLog.w("分类/标签引擎翻译失败，保留已命中的译文：%s", e.message.orEmpty())
                    return@withContext TagTranslationResult(result, e)
                }
            val newEntries = mutableMapOf<String, String>()
            for ((key, value) in requested) {
                val text =
                    translated[key]?.trim()?.takeIf { it.isNotEmpty() && it != value } ?: continue
                // 译文再过一遍繁→简：模型偶尔会给出繁体
                val simplified = MetadataTagNormalizer.toSimplified(text)
                result[value] = simplified
                newEntries[value] = simplified
            }
            if (newEntries.isNotEmpty()) {
                try {
                    saveCache(newEntries)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 缓存可重建，落盘失败不能丢掉已经取得的译文。
                    AppLog.w(e, "保存分类/标签翻译缓存失败，保留本次译文")
                    cacheError = cacheError ?: e
                }
            }
            TagTranslationResult(result, cacheError)
        }

    private fun loadCache(): Map<String, String> = cacheManager.access {
        val raw = if (cacheFile.isFile) cacheFile.readText() else ""
        val parsed = mutableMapOf<String, String>()
        if (raw.isNotBlank()) {
            try {
                val json = JSONObject(raw)
                json.keys().forEach { key ->
                    val value = json.optString(key).trim()
                    if (value.isNotEmpty()) parsed[key] = value
                }
            } catch (e: JSONException) {
                // 缓存坏了只影响复用率，重新积累即可，不必打扰用户
                AppLog.w("分类/标签翻译缓存无法解析，已重置：%s", e.message.orEmpty())
            }
        }
        cacheManager.touch(cacheFile)
        parsed
    }

    private fun saveCache(entries: Map<String, String>) = cacheManager.access {
        val merged = loadCache().toMutableMap().apply { putAll(entries) }
        val json = JSONObject()
        merged.forEach { (key, value) -> json.put(key, value) }
        check(cacheFile.parentFile?.mkdirs() == true || cacheFile.parentFile?.isDirectory == true)
        val staging = cacheFile.resolveSibling("tags.tmp")
        staging.writeText(json.toString())
        check(staging.renameTo(cacheFile))
        cacheManager.trim()
    }

}
