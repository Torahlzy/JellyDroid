package dev.jdtech.jellyfin.core.subtitle

import android.app.Application
import dev.jdtech.jellyfin.logging.AppLog
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

/**
 * 对照面板的候选快照落盘。
 *
 * 为什么要有它：一期把下载到的字节只放在 ViewModel 内存里，选中即传；二期要「进播放页边看边比」，
 * 字节必须跨页面存活，所以写进 `cacheDir/subtitle_compare/<itemId>/`（`cacheDir` 由系统回收，
 * 不做全局定时清理）。
 *
 * 文件名用候选 `pageUrl` 的短 hash，**不用序号**：序号随搜索顺序 / 取消重下变化，
 * 拿序号当文件名很容易把 A 的字节当成 B 的。另写一份 `manifest.json` 记录语言、格式、标题、
 * 时长差与序号，读取时按它还原。
 *
 * 是 `@Singleton`：面板打开期间剩余候选还在下载，每下完一条都要补进同一份快照，
 * 详情页与播放页必须看到同一个实例的写入与 [updates] 通知。
 */
@Singleton
class SubtitleCompareCache @Inject constructor(private val application: Application) {

    /**
     * 清理专用作用域，不挂在任何页面生命周期上。
     *
     * [clearAsync] 的典型调用点是播放页 `onDestroy`：那一刻页面自己的 `lifecycleScope` 已经不可用，
     * 若还用它启动清理，协程会被直接取消、磁盘上的快照留到下次被错误合并。
     */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _updates = MutableSharedFlow<UUID>(extraBufferCapacity = UPDATES_BUFFER)

    /**
     * 每个条目的批次令牌：[clear] 一次就递增一次。
     *
     * 详情页与播放页各有一份 `SubtitleDownloadController`，却写同一个单例缓存；没有这个令牌，
     * 「新一轮对比已 clear、旧 controller 还在边下边补」时，旧候选会按行号合并进新快照，
     * 把用户这轮没选过的字幕顶进面板。调用方在 clear 之后取一次 [batchToken]，
     * 之后每次 [upsert] 携带它校验，不匹配即拒绝写入。
     *
     * 线程安全：upsert 跑在 IO，clear / clearAsync 可能来自任意线程。
     */
    private val generations = ConcurrentHashMap<UUID, Long>()

    /** 当前批次令牌；从没 clear 过的条目为 0。调用方必须在 clear 之后读取才是本批的令牌。 */
    fun batchToken(itemId: UUID): Long = generations[itemId] ?: 0L

    /**
     * 快照新增条目后发出对应条目 id。
     *
     * 播放页据此增量刷新已打开的面板：候选边下边比时，先到的几条已经在面板上，
     * 后下的要能自己「长」出来，不该让用户重开面板。
     */
    val updates: SharedFlow<UUID> = _updates.asSharedFlow()

    /**
     * 写入（合并）[subtitles] 到 [itemId] 的快照，键是行号（候选在搜索结果里的位置 + 1）。
     *
     * **写入过程不清空目录、不重排已有行**：面板可能正开着，已经在屏幕上的行必须原地保持序号与内容；
     * 同序号再次写入时才覆盖（并删掉被顶替的旧文件）。
     *
     * [batchToken] 必须是本批开始前 [clear] 之后取到的 [batchToken]：中途任何一次 [clear]
     * （包括退出播放页的 [clearAsync]）都会让旧令牌失效、本次写入被拒绝——这正是要防的
     * 「旧 controller 把候选补回新快照」。被拒绝时抛 [IllegalStateException]，由调用方按
     * 「本批已作废」处理，不会在磁盘上留下半个目录。
     *
     * 「新一轮对比」必须先 [clear] 再取令牌再 [upsert]：否则上一轮剩下的行号会跟本次合并到同一份
     * 清单里，面板上出现本次并没有下过的字幕。这一步由调用方（[SubtitleDownloadController.prepareCompare]）
     * 负责，因为「边下边比」的追加写入必须走同一个合并语义。
     *
     * 写文件用「先写 `.tmp` 再 rename」：进程中途被杀时留下的是 `.tmp`，
     * 不会被下一次读取当成有效缓存。返回合并后按 [SubtitleCompareEntry.order] 排好的结果。
     */
    suspend fun upsert(
        itemId: UUID,
        batchToken: Long,
        subtitles: Map<Int, DownloadedSubtitle>,
    ): List<SubtitleCompareEntry> {
        // 令牌校验放在进 IO 之前：过期写入直接拒绝，避免为它重新建目录
        val currentToken = generations[itemId] ?: 0L
        if (currentToken != batchToken) {
            throw IllegalStateException(
                "对照快照批次已过期，丢弃本次写入：$itemId（令牌 $batchToken ≠ 当前 $currentToken）"
            )
        }
        return withContext(Dispatchers.IO) {
            val dir = itemDir(itemId)
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw IllegalStateException("无法创建字幕对照缓存目录：$dir")
            }
            val merged = readEntries(dir).associateBy { it.order }.toMutableMap()
            subtitles.forEach { (order, downloaded) ->
                val file = File(dir, fileName(downloaded))
                // 同一候选的文件名由 pageUrl 决定，已存在就说明内容一样，不必重写
                if (!file.isFile) {
                    writeAtomically(file, downloaded.bytes)
                }
                removeReplacedFile(merged, order, file)
                merged[order] =
                    SubtitleCompareEntry(
                        order = order,
                        title = downloaded.candidate.title,
                        language = downloaded.subtitle.language,
                        format = downloaded.subtitle.format,
                        durationDiffMs = downloaded.durationDiffMs,
                        isRecommended = downloaded.isRecommended,
                        file = file,
                    )
            }
            val entries = merged.values.sortedBy { it.order }
            writeAtomically(File(dir, MANIFEST_FILE), manifestOf(entries).toByteArray())
            AppLog.d(
                "字幕对照快照已写入：%s 本次 %d 条，合计 %d 条（行号 %s）",
                itemId,
                subtitles.size,
                entries.size,
                entries.joinToString(",") { it.order.toString() },
            )
            _updates.tryEmit(itemId)
            entries
        }
    }

    /**
     * 读出 [itemId] 的快照；目录不存在、清单损坏或文件缺失时返回空表。
     *
     * 空表由调用方按「本地字幕已失效」处理（提示用户重新下载，不静默重下）。
     */
    suspend fun load(itemId: UUID): List<SubtitleCompareEntry> =
        withContext(Dispatchers.IO) {
            try {
                readEntries(itemDir(itemId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w(e, "读取字幕对照快照失败：%s", itemId)
                emptyList()
            }
        }

    /** 读清单并还原条目，丢掉文件已不存在的项（多半是系统清了 cacheDir）。 */
    private fun readEntries(dir: File): List<SubtitleCompareEntry> {
        val manifestFile = File(dir, MANIFEST_FILE)
        if (!manifestFile.isFile) return emptyList()
        val manifest = JSONObject(manifestFile.readText())
        val array = manifest.optJSONArray(ENTRIES) ?: return emptyList()
        val entries =
            (0 until array.length())
                .mapNotNull { index -> array.optJSONObject(index)?.toEntry(dir) }
        // 清单里有、磁盘上却没有：日志能一眼看出是这种情况
        entries.filterNot { it.file.isFile }.forEach { AppLog.w("对照快照文件缺失：%s", it.file) }
        return entries.filter { it.file.isFile }.sortedBy { it.order }
    }

    /**
     * 把一份字幕写成「仅供本次播放侧载」的文件，返回它。
     *
     * 与 [upsert] 分开：**不写进 manifest**，所以不会出现在对照面板里，只是给播放器一个
     * 可直接读取的本地文件（上传成功后立即切轨用，比等服务器重新索引确定得多）。
     * 文件名带 `manual-` 前缀，和快照条目的 hash 名区分开。
     */
    suspend fun stageForPlayback(itemId: UUID, downloaded: DownloadedSubtitle): File =
        withContext(Dispatchers.IO) {
            val dir = itemDir(itemId)
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw IllegalStateException("无法创建字幕缓存目录：$dir")
            }
            val hash = Integer.toHexString(downloaded.candidate.pageUrl.hashCode())
            val file = File(dir, "manual-$hash.${downloaded.subtitle.format}")
            writeAtomically(file, downloaded.bytes)
            AppLog.d("侧载字幕文件已就绪：%s", file)
            file
        }

    /** 删掉 [itemId] 的整份快照（切轨完成或退出对照后调用）；同时递增批次令牌，作废在途的追加写入。 */
    suspend fun clear(itemId: UUID) {
        withContext(Dispatchers.IO) {
            generations[itemId] = (generations[itemId] ?: 0L) + 1
            val dir = itemDir(itemId)
            if (dir.exists() && !dir.deleteRecursively()) {
                AppLog.w("清理字幕对照缓存失败：%s", dir)
            }
        }
    }

    /**
     * 不依赖调用方协程作用域的清理入口，用于播放页 `onDestroy` 这类「马上就要销毁」的时机。
     *
     * 快照只对当前这次播放有意义，页面销毁后留着只会在下一轮对比时被当成本批候选合并进来，
     * 因此宁可交给内部作用域在后台删掉，也不跟着临死的 `lifecycleScope` 一起被取消。
     */
    fun clearAsync(itemId: UUID) {
        AppLog.d("后台清理字幕对照快照：%s", itemId)
        cleanupScope.launch { clear(itemId) }
    }

    /**
     * 同一个行号换了候选时，把被顶替的旧文件删掉，避免缓存目录里堆积没人引用的字幕。
     *
     * 只删「旧条目自己的文件」且确认没有别的行还在引用它：同一份候选可能同时占多行。
     */
    private fun removeReplacedFile(
        merged: Map<Int, SubtitleCompareEntry>,
        order: Int,
        newFile: File,
    ) {
        val replaced = merged[order]?.file ?: return
        if (replaced.name == newFile.name) return
        if (merged.values.any { it.order != order && it.file.name == replaced.name }) return
        if (replaced.isFile && !replaced.delete()) {
            AppLog.w("删除被顶替的对照字幕失败：%s", replaced)
        }
    }

    private fun itemDir(itemId: UUID): File =
        File(File(application.cacheDir, ROOT_DIR), itemId.toString())

    private fun fileName(downloaded: DownloadedSubtitle): String {
        val hash = Integer.toHexString(downloaded.candidate.pageUrl.hashCode())
        return "$hash.${downloaded.subtitle.format}"
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            // 个别文件系统上目标已存在时 rename 会失败，删掉再来一次
            target.delete()
            check(temp.renameTo(target)) { "写入字幕对照缓存失败：$target" }
        }
    }

    private fun manifestOf(entries: List<SubtitleCompareEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put(ORDER, entry.order)
                    put(TITLE, entry.title)
                    put(LANGUAGE, entry.language.name)
                    put(FORMAT, entry.format)
                    put(DURATION_DIFF_MS, entry.durationDiffMs ?: JSONObject.NULL)
                    put(IS_RECOMMENDED, entry.isRecommended)
                    put(FILE, entry.file.name)
                }
            )
        }
        return JSONObject().put(ENTRIES, array).toString()
    }

    private fun JSONObject.toEntry(dir: File): SubtitleCompareEntry? {
        val language =
            SubtitleLanguage.entries.firstOrNull { it.name == optString(LANGUAGE) } ?: return null
        val fileName = optString(FILE).takeIf { it.isNotBlank() } ?: return null
        return SubtitleCompareEntry(
            order = optInt(ORDER),
            title = optString(TITLE),
            language = language,
            format = optString(FORMAT),
            durationDiffMs = if (isNull(DURATION_DIFF_MS)) null else optLong(DURATION_DIFF_MS),
            isRecommended = optBoolean(IS_RECOMMENDED),
            file = File(dir, fileName),
        )
    }

    private companion object {
        const val ROOT_DIR = "subtitle_compare"
        const val MANIFEST_FILE = "manifest.json"

        /** 快照更新事件的缓冲：面板关闭 / 未订阅时不阻塞写入方，漏了就漏了（下次 load 会补上）。 */
        const val UPDATES_BUFFER = 8
        const val ENTRIES = "entries"
        const val ORDER = "order"
        const val TITLE = "title"
        const val LANGUAGE = "language"
        const val FORMAT = "format"
        const val DURATION_DIFF_MS = "durationDiffMs"
        const val IS_RECOMMENDED = "isRecommended"
        const val FILE = "file"
    }
}
