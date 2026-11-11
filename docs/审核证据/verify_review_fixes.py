"""复审回归：提取当前协调与下载入口，使用可控挂起点验证生命周期边界。"""
import json
import subprocess
import tempfile
from pathlib import Path
from verify_continuation import ROOT, CACHE, jar, function_source


def verify_review_fixes():
    coordinator = (ROOT / 'app/phone/src/main/java/dev/jdtech/jellyfin/presentation/player/subtitle/SubtitlePlaybackCoordinator.kt').read_text()
    downloader = (ROOT / 'core/src/main/java/dev/jdtech/jellyfin/utils/DownloaderImpl.kt').read_text()
    probe = '''
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import dev.jdtech.jellyfin.presentation.player.subtitle.SubtitleCompareManager
import dev.jdtech.jellyfin.core.subtitle.*
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.utils.DownloadCancelledException

class Repo {
    var restored = false
    var user = UUID(0, 1)
    var userReads = 0
    var sourceReads = 0
    val ready = CompletableDeferred<Unit>()
    suspend fun initialize() { ready.await(); restored = true }
    fun getBaseUrl() = if (restored) "https://example.test" else ""
    fun getUserId(): UUID {
        userReads++
        return checkNotNull(if (restored) user else null)
    }
    suspend fun getMediaSources(item: UUID, ignored: Boolean): List<Source> {
        check(restored)
        sourceReads++
        return listOf(Source("sample"))
    }
}
data class Source(val remoteFilePath: String)
object AvidParser { fun getDvdId(path: String) = path }
object CoreR {
    object string {
        const val subtitle_compare_cache_missing = 1
        const val subtitle_download_failed = 2
    }
}
class Context { fun getString(id: Int, vararg args: Any?) = "message" }
object Toast {
    const val LENGTH_LONG = 1
    fun makeText(context: Any, resource: Int, duration: Int) = this
    fun makeText(context: Any, message: String, duration: Int) = this
    fun show() {}
}
class Adjust {
    data class State(val isAdjustMode: Boolean = false)
    val state = MutableStateFlow(State())
    var realVisible = true
    fun close() {}
    fun setRealSubtitleVisible(visible: Boolean) { realVisible = visible }
}
class CoordinatorProbe(
    val repository: Repo,
    val scope: CoroutineScope,
    val compareManager: SubtitleCompareManager,
    val appPreferences: AppPreferences,
    var item: UUID,
) {
    var session = 1L
    val context = Context()
    val subtitleAdjustManager = Adjust()
    val _keyword = MutableStateFlow("")
    var panelLoadJob: Job? = null
    var keywordJob: Job? = null
    val currentPlaybackSession = { session }
    fun targetItemId() = item
    fun currentPlayerItemId() = item
    fun hidePlayerController() {}
    fun showDownloadDialog() {}
    private data class PlaybackTarget(val server: String, val userId: UUID, val itemId: UUID, val session: Long)
    private fun playbackTarget(itemId: UUID = targetItemId()) = PlaybackTarget(
        repository.getBaseUrl(), repository.getUserId(), itemId, currentPlaybackSession()
    )
    private fun isCurrent(target: PlaybackTarget): Boolean = target == playbackTarget()
    fun startKeyword() { loadDefaultKeyword() }
'''
    for signature in ['private fun loadDefaultKeyword()', 'fun openComparePanel(',
                      'private suspend fun loadCurrentCompare(']:
        probe += function_source(coordinator, signature) + '\n'
    probe += '''
}
class FindroidItem
class FindroidSource(val id: String)
class UiText
class DownloadProbe {
    private val preparationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preparations = ConcurrentHashMap<String, Deferred<Pair<String?, UiText?>>>()
    val started = Channel<String>(Channel.UNLIMITED)
    val permit = Channel<Unit>(Channel.UNLIMITED)
    val cleanupStarted = Channel<Unit>(Channel.UNLIMITED)
    val cleanupPermit = Channel<Unit>(Channel.UNLIMITED)
    val calls = AtomicInteger()
    val completed = AtomicInteger()
    val cleaned = AtomicInteger()
    val deleted = AtomicInteger()
    private suspend fun prepareDownload(item: FindroidItem, sourceId: String, storageIndex: Int): Pair<String?, UiText?> {
        calls.incrementAndGet()
        started.send(sourceId)
        try {
            permit.receive()
            completed.incrementAndGet()
            return "task" to null
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                cleanupStarted.send(Unit)
                cleanupPermit.receive()
                cleaned.incrementAndGet()
            }
            throw e
        }
    }
    private suspend fun deleteItemFiles(item: FindroidItem, source: FindroidSource) {
        check(cleaned.get() == 1)
        deleted.incrementAndGet()
    }
    suspend fun cancelFromReopenedPage(sourceId: String) { cancelPreparation(sourceId) }
    suspend fun retainedResult(sourceId: String) = checkNotNull(preparations[sourceId]).await()
    fun retainedCount() = preparations.size
}
'''
    methods = '\n'.join(function_source(downloader, signature).replace('override suspend', 'suspend', 1)
                        for signature in ['override suspend fun downloadItem(',
                                          'private suspend fun cancelPreparation(',
                                          'override suspend fun deleteItem('])
    probe = probe.replace('    suspend fun cancelFromReopenedPage', methods + '\n    suspend fun cancelFromReopenedPage')
    probe += '''
fun main() = runBlocking {
    withTimeout(15000) {
        val item = UUID(0, 2)
        val preferences = AppPreferences()
        val player = object: androidx.media3.common.Player { override val currentPosition = 1000L }
        fun newCoordinator(cache: SubtitleCompareCache, repo: Repo) = CoordinatorProbe(
            repo, this, SubtitleCompareManager(player, preferences, cache), preferences, item
        )
        run {
            val cache = SubtitleCompareCache()
            val repository = Repo()
            val coordinator = newCoordinator(cache, repository)
            coordinator.startKeyword()
            coordinator.openComparePanel(item)
            yield()
            check(repository.userReads == 0 && repository.sourceReads == 0)
            check(!coordinator.compareManager.state.value.isShowing)
            repository.ready.complete(Unit)
            cache.started.receive()
            cache.permit.send(Unit)
            coordinator.keywordJob?.join()
            coordinator.panelLoadJob?.join()
            check(coordinator._keyword.value == "sample")
            check(coordinator.compareManager.state.value.isShowing)
            check(!coordinator.subtitleAdjustManager.realVisible)
            println("冷启动：关键词和对照入口均等待连接恢复后才读用户及媒体源")
        }
        run {
            val cache = SubtitleCompareCache()
            val repository = Repo().apply { ready.complete(Unit) }
            val coordinator = newCoordinator(cache, repository)
            coordinator.openComparePanel(item)
            cache.started.receive()
            coordinator.session++
            cache.permit.send(Unit)
            cache.started.receive()
            check(!coordinator.compareManager.state.value.isShowing)
            check(coordinator.subtitleAdjustManager.realVisible)
            cache.permit.send(Unit)
            coordinator.panelLoadJob?.join()
            check(coordinator.compareManager.state.value.isShowing)
            check(!coordinator.subtitleAdjustManager.realVisible)
            println("初次同条目换代：旧加载未发布，重读成功后面板与真实字幕互斥")
        }
        for (switchAccount in listOf(false, true)) {
            val cache = SubtitleCompareCache()
            val repository = Repo().apply { ready.complete(Unit) }
            val coordinator = newCoordinator(cache, repository)
            coordinator.openComparePanel(item)
            cache.started.receive()
            if (switchAccount) repository.user = UUID(0, 9) else coordinator.item = UUID(0, 3)
            cache.permit.send(Unit)
            coordinator.panelLoadJob?.join()
            check(!coordinator.compareManager.state.value.isShowing)
            check(coordinator.subtitleAdjustManager.realVisible)
        }
        run {
            val cache = SubtitleCompareCache()
            val manager = SubtitleCompareManager(player, preferences, cache)
            val load = async { manager.load(item) }
            cache.started.receive()
            manager.hide()
            cache.permit.send(Unit)
            check(!load.await())
            check(!manager.state.value.isShowing)
            println("切条目、换用户和加载期间关闭：过期加载不能重新显示面板")
        }
        val media = FindroidItem()
        run {
            val downloader = DownloadProbe()
            val page = launch { downloader.downloadItem(media, "s1", 0) }
            downloader.started.receive()
            page.cancelAndJoin()
            check(downloader.cleaned.get() == 0 && downloader.retainedCount() == 1)
            val result = async(start = CoroutineStart.UNDISPATCHED) { downloader.retainedResult("s1") }
            downloader.permit.send(Unit)
            check(result.await().first == "task")
            check(downloader.completed.get() == 1 && downloader.cleaned.get() == 0)
            println("退出详情页：只取消界面等待，已接受的准备继续完成")
        }
        run {
            val downloader = DownloadProbe()
            val page = launch { downloader.downloadItem(media, "s2", 0) }
            downloader.started.receive()
            page.cancel(DownloadCancelledException())
            downloader.cleanupStarted.receive()
            check(!page.isCompleted)
            downloader.cleanupPermit.send(Unit)
            page.join()
            check(downloader.cleaned.get() == 1 && downloader.completed.get() == 0)
            println("主动取消：停止独立准备，并等待不可取消的清理段完成")
        }
        for (delete in listOf(false, true)) {
            val downloader = DownloadProbe()
            val page = launch { downloader.downloadItem(media, "s3", 0) }
            downloader.started.receive()
            page.cancelAndJoin()
            val cancel = launch {
                if (delete) downloader.deleteItem(media, FindroidSource("s3"))
                else downloader.cancelFromReopenedPage("s3")
            }
            downloader.cleanupStarted.receive()
            check(!cancel.isCompleted)
            downloader.cleanupPermit.send(Unit)
            cancel.join()
            check(downloader.cleaned.get() == 1 && downloader.completed.get() == 0)
            check(downloader.deleted.get() == if (delete) 1 else 0)
        }
        run {
            val downloader = DownloadProbe()
            val first = async { downloader.downloadItem(media, "s4", 0) }
            downloader.started.receive()
            val second = async(start = CoroutineStart.UNDISPATCHED) { downloader.downloadItem(media, "s4", 0) }
            check(downloader.calls.get() == 1)
            downloader.permit.send(Unit)
            check(first.await() == second.await())
            check(downloader.calls.get() == 1)
            println("重进页面取消或删除可停止遗留准备；同来源并发请求只准备一次")
        }
    }
}
'''
    stubs = {
        'Player.kt': 'package androidx.media3.common\ninterface Player { val currentPosition: Long }',
        'Preferences.kt': '''package dev.jdtech.jellyfin.settings.domain
class AppPreferences {
    val offlineMode = false
    fun getValue(value: Boolean) = value
    fun getSubtitleOffset(item: String) = 0L
}''',
        'Cache.kt': '''package dev.jdtech.jellyfin.core.subtitle
import java.io.File
import java.util.UUID
import kotlinx.coroutines.channels.Channel
class SubtitleCompareEntry(val order: Int = 1, val title: String = "sample", val language: String = "en", val format: String = "srt", val durationDiffMs: Long? = 0L, val file: File)
data class SubtitleCue(val startMs: Long = 0L, val endMs: Long = 2000L, val text: String = "sample")
object SubtitleCueParser { fun parse(bytes: ByteArray) = listOf(SubtitleCue()) }
class SubtitleCompareCache {
    val started = Channel<UUID>(Channel.UNLIMITED)
    val permit = Channel<Unit>(Channel.UNLIMITED)
    suspend fun load(item: UUID): List<SubtitleCompareEntry> {
        started.send(item)
        permit.receive()
        val file = File.createTempFile("cue", ".srt").apply { deleteOnExit(); writeText("sample") }
        return listOf(SubtitleCompareEntry(file = file))
    }
}''',
        'Log.kt': '''package dev.jdtech.jellyfin.logging
object AppLog { fun d(vararg args: Any?) {} ; fun w(vararg args: Any?) {} ; fun e(vararg args: Any?) {} }
''',
    }
    compiler = jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.10')
    stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.10')
    coroutines = jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')
    dependencies = [compiler, stdlib, coroutines]
    runtime = [stdlib, coroutines]
    for group, artifact in [('org.jetbrains.kotlin', 'kotlin-script-runtime'),
                            ('org.jetbrains.kotlin', 'kotlin-reflect'),
                            ('org.jetbrains.intellij.deps', 'trove4j'),
                            ('org.jetbrains', 'annotations')]:
        matches = sorted(p for p in (CACHE / group / artifact).glob('*/*/*.jar')
                         if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
        if matches:
            dependencies.append(str(matches[-1]))
            if artifact == 'annotations':
                runtime.append(str(matches[-1]))
    with tempfile.TemporaryDirectory(prefix='jd-review-fixes-') as directory:
        folder = Path(directory)
        for name, body in stubs.items():
            (folder / name).write_text(body)
        (folder / 'Probe.kt').write_text(probe)
        originals = [
            ROOT / 'app/phone/src/main/java/dev/jdtech/jellyfin/presentation/player/subtitle/SubtitleCompareManager.kt',
            ROOT / 'core/src/main/java/dev/jdtech/jellyfin/utils/DownloadCancelledException.kt',
        ]
        result = subprocess.run(
            ['java', '-cp', ':'.join(dependencies), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
             '-no-stdlib', '-no-reflect', '-classpath', ':'.join(runtime), '-d', str(folder / 'classes')]
            + [str(path) for path in originals + list(folder.glob('*.kt'))],
            capture_output=True, text=True, timeout=60)
        if result.returncode:
            raise RuntimeError(result.stderr)
        result = subprocess.run(
            ['java', '-cp', ':'.join([str(folder / 'classes')] + runtime), 'ProbeKt'],
            capture_output=True, text=True, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return {'regression_checks': result.stdout.strip().splitlines(),
                'android_type_check': False, 'network_used': False}


if __name__ == '__main__':
    print(json.dumps(verify_review_fixes(), ensure_ascii=False, indent=2))
