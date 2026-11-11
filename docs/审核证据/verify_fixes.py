"""修复回归：编译当前纯逻辑与少量平台替身，只操作隔离文件和内存 SQLite。"""
import json
import re
import sqlite3
import subprocess
import tempfile
from pathlib import Path
from verify_continuation import ROOT, CACHE, jar, function_source
from verify_first_batch import create_database


def verify_sync():
    source = (ROOT / 'data/src/main/java/dev/jdtech/jellyfin/database/ServerDatabaseDao.kt').read_text()
    block = source[source.index('"UPDATE userdata SET toBeSynced = 0'):source.index('suspend fun acknowledgeUserData')]
    query = ''.join(re.findall(r'"([^"\n]+)"', block))
    db = create_database(9)
    db.execute('INSERT INTO userdata VALUES (?, ?, ?, ?, ?, ?)', ('u', 'i', 0, 0, 100, 1))
    params = dict(userId='u', itemId='i', positionTicks=100, favorite=0, played=0)
    for column, value in [('playbackPositionTicks', 200), ('favorite', 1), ('played', 1)]:
        db.execute('UPDATE userdata SET played=0, favorite=0, playbackPositionTicks=100, toBeSynced=1')
        db.execute(f'UPDATE userdata SET {column}=?', (value,))
        assert db.execute(query, params).rowcount == 0
        assert db.execute('SELECT toBeSynced FROM userdata').fetchone()[0] == 1
    db.execute('UPDATE userdata SET played=0, favorite=0, playbackPositionTicks=100')
    assert db.execute(query, params).rowcount == 1
    assert db.execute('SELECT toBeSynced FROM userdata').fetchone()[0] == 0
    db.close()
    return '进度、收藏、已播放的新修改均保留；完全匹配的快照才清位'


def verify_download_ownership():
    source = (ROOT / 'data/src/main/java/dev/jdtech/jellyfin/database/ServerDatabaseDao.kt').read_text()
    db = create_database(9)
    db.execute('INSERT INTO sources VALUES (?, ?, ?, ?, ?, ?)',
               ('s', 'i', '视频', 'LOCAL', 'video.download', 'new-task'))
    db.execute('INSERT INTO mediastreams (id, sourceId, title, language, type, codec, isExternal, path, downloadTaskId) '
               'VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)',
               ('m', 's', '字幕', 'zh', 'SUBTITLE', 'srt', 1, 'subtitle.download', 'new-task'))
    for table, record_id in [('sources', 's'), ('mediastreams', 'm')]:
        query = re.search(r'"(UPDATE ' + table + r' SET path[^"\n]+)"', source).group(1)
        params = dict(id=record_id, path='final', taskId='old-task')
        assert db.execute(query, params).rowcount == 0
        params['taskId'] = 'new-task'
        assert db.execute(query, params).rowcount == 1
        assert db.execute(f'SELECT path, downloadTaskId FROM {table}').fetchone() == ('final', None)
        db.execute(f'DELETE FROM {table}')
        assert db.execute(query, params).rowcount == 0
    db.close()
    return '旧任务和已删除记录拒绝收尾回写；仅匹配任务可更新路径并清除任务号'


def verify_kotlin():
    compiler = jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.10')
    stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.10')
    coroutines = jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')
    okhttp = jar('com.squareup.okhttp3', 'okhttp', '4.12.0')
    okio = jar('com.squareup.okio', 'okio-jvm', '3.6.0')
    json_jar = jar('org.json', 'json', '20240303')
    runtime = [stdlib, coroutines, okhttp, okio, json_jar]
    dependencies = [compiler] + runtime
    for group, artifact in [('org.jetbrains.kotlin', 'kotlin-script-runtime'),
                            ('org.jetbrains.kotlin', 'kotlin-reflect'),
                            ('org.jetbrains.intellij.deps', 'trove4j'),
                            ('org.jetbrains', 'annotations')]:
        found = sorted(p for p in (CACHE / group / artifact).glob('*/*/*.jar')
                       if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
        if found:
            dependencies.append(str(found[-1]))
            if artifact == 'annotations':
                runtime.append(str(found[-1]))
    stubs = {
        'Context.kt': 'package android.content\nopen class Context',
        'Application.kt': 'package android.app\nclass Application(val cacheDir: java.io.File)',
        'Inject.kt': 'package javax.inject\nannotation class Inject\nannotation class Singleton',
        'Attrs.kt': 'package android.util\ninterface AttributeSet',
        'Views.kt': '''package android.widget
import android.content.Context
import android.util.AttributeSet
open class FrameLayout(c: Context, a: AttributeSet?, s: Int) {
 class LayoutParams(w: Int, h: Int) { companion object { const val MATCH_PARENT = -1 } }
 fun addView(v: Any, p: LayoutParams) {}
 open fun onDetachedFromWindow() {}
}''',
        'Cue.kt': 'package androidx.media3.common.text\ndata class Cue(val text: String)',
        'SubtitleView.kt': '''package androidx.media3.ui
import android.content.Context
import androidx.media3.common.text.Cue
class SubtitleView(c: Context) {
 val history = mutableListOf<List<Cue>>()
 fun setCues(cues: List<Cue>) { history.add(cues) }
}''',
        'Handler.kt': '''package android.os
class Looper { companion object { fun getMainLooper() = Looper() } }
class Handler(l: Looper) {
 companion object {
  var now = 0L
  private val queue = mutableListOf<Pair<Long, Runnable>>()
  fun advanceTo(time: Long) {
   now = time
   val ready = queue.filter { it.first <= time }.sortedBy { it.first }
   queue.removeAll(ready.toSet())
   ready.forEach { it.second.run() }
  }
 }
 fun postDelayed(r: Runnable, d: Long) { queue.add(now + d to r) }
 fun removeCallbacks(r: Runnable) { queue.removeAll { it.second === r } }
}''',
        'AppLog.kt': '''package dev.jdtech.jellyfin.logging
object AppLog {
 fun w(vararg a: Any?) {} ; fun d(vararg a: Any?) {} ; fun e(vararg a: Any?) {}
 fun tag(s: String) = this
}''',
        'Repository.kt': '''package dev.jdtech.jellyfin.repository
import java.util.UUID
interface JellyfinRepository {
 fun getBaseUrl(): String
 fun getUserId(): UUID
 suspend fun uploadSubtitle(itemId: UUID, subtitleBytes: ByteArray, language: String, format: String)
}''',
    }
    main = r'''
package dev.jdtech.jellyfin.core.subtitle
import android.app.Application
import android.content.Context
import android.os.Handler
import androidx.media3.common.text.Cue
import dev.jdtech.jellyfin.presentation.player.subtitle.DelayedSubtitleView
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun subtitle(order: Int): DownloadedSubtitle {
 val candidate = SubtitleCandidate("s$order", "https://example.test/$order")
 return DownloadedSubtitle(candidate, SubtitleDownload("s$order", SubtitleLanguage.ENGLISH, "vtt", "https://example.test/$order.vtt"),
  "WEBVTT\n\n00:01.000 --> 00:02.000\nhello\n".toByteArray(), 0, true)
}
fun main(args: Array<String>) = runBlocking {
 val short = "WEBVTT\n\n00:01.000 --> 00:02.000 align:start\nhello\n"
 check(SubtitleCueParser.parse(short).single().endMs == 2000L)
 check(SubtitleTiming.lastTimestampMillis(short) == 2000L)
 check(SubtitleCueParser.parse("00:00:01,000 --> 00:00:02,000\n你好").single().text == "你好")
 check(SubtitleCueParser.parse("01:00:01.000 --> 01:00:02.000\nlong").single().startMs == 3601000L)
 val normalized = SubtitleTiming.normalizeTimingLines("00：00：01.000->00：00：02.000\n台词：保留".toByteArray(), "srt").decodeToString()
 check(normalized.contains("00:00:01,000 --> 00:00:02,000"))
 check(normalized.contains("台词：保留"))
 check(ProbeHttp.resolveUrl("https://example.test/a", "//example.test/b") == "https://example.test/b")
 check(ProbeHttp.follows("https://example.test/a", ProbeHttp.resolveUrl("https://example.test/a", "//example.test/b")!!))
 check(!ProbeHttp.follows("https://example.test/a", "https://other.test/b"))
 check(ProbeHttp.resolveUrl("https://example.test/a/b", "../c?q=1") == "https://example.test/c?q=1")
 check(ProbeHttp.run { "small".toResponseBody().readLimitedBytes(8) }.decodeToString() == "small")
 check(runCatching { ProbeHttp.run { ByteArray(40).toResponseBody().readLimitedBytes(32) } }.isFailure)
 val view = DelayedSubtitleView(Context())
 view.delayMs = 5000L
 view.setCues(listOf(Cue("A")))
 Handler.advanceTo(1000L)
 view.setCues(emptyList())
 Handler.advanceTo(5000L)
 check(view.subtitleView.history.last().single().text == "A")
 Handler.advanceTo(6000L)
 check(view.subtitleView.history.last().isEmpty())
 view.setCues(listOf(Cue("丢弃")))
 view.clear()
 Handler.advanceTo(12000L)
 check(view.subtitleView.history.last().isEmpty())
 val root = File(args[0])
 val destination = File(root, "downloads").apply { mkdirs() }
 val pool = Executors.newFixedThreadPool(8)
 val gate = CountDownLatch(1)
 val tasks = (1..20).map { number -> pool.submit<File> {
  gate.await()
  val temp = File(destination, "$number.download").apply { writeText("payload$number") }
  val reserved = ProbeDownload.resolveFinalFile(temp, "video.mkv")
  check(temp.renameTo(reserved))
  reserved
 } }
 gate.countDown()
 val files = tasks.map { it.get() }
 pool.shutdown()
 check(files.map { it.name }.toSet().size == 20)
 check(files.map { it.readText() }.toSet().size == 20)
 (2..99).forEach { File(destination, "video ($it).mkv").apply { if (!exists()) writeText("keep") } }
 val before = destination.listFiles()!!.associate { it.name to it.readText() }
 check(runCatching { ProbeDownload.resolveFinalFile(File(destination, "another.download"), "video.mkv") }.isFailure)
 check(before == destination.listFiles()!!.associate { it.name to it.readText() })
 val cache = SubtitleCompareCache(Application(root))
 val item = UUID.randomUUID()
 val token = cache.clear(item)
 coroutineScope { (1..30).map { order -> async { cache.upsert(item, token, mapOf(order to subtitle(order))) } }.awaitAll() }
 check(cache.load(item).map { it.order } == (1..30).toList())
 repeat(30) {
  val oldToken = cache.batchToken(item)
  coroutineScope {
   val write = async(Dispatchers.Default) { runCatching { cache.upsert(item, oldToken, mapOf(1 to subtitle(1))) } }
   val clear = async(Dispatchers.Default) { cache.clear(item) }
   write.await(); clear.await()
  }
  check(cache.load(item).isEmpty())
 }
 val candidateResults = MutableSharedFlow<SubtitleCandidateResult>(extraBufferCapacity = 8)
 val downloader = object : SubtitleDownloader {
  override fun download(keyword: String, videoDurationMs: Long): Flow<SubtitleCandidateResult> = candidateResults
 }
 var uploadedItem: UUID? = null
 val uploadGate = CompletableDeferred<Unit>()
 val repository = object : JellyfinRepository {
  override fun getBaseUrl() = "https://server.test"
  override fun getUserId() = UUID(0, 1)
  override suspend fun uploadSubtitle(itemId: UUID, subtitleBytes: ByteArray, language: String, format: String) {
   uploadedItem = itemId
   uploadGate.await()
  }
 }
 val controller = SubtitleDownloadController(downloader, repository, cache)
 val controllerScope = CoroutineScope(coroutineContext + SupervisorJob())
 val events = mutableListOf<SubtitleDownloadEvent>()
 val collector = controllerScope.launch { controller.events.collect { events.add(it) } }
 controller.download(controllerScope, "q", 1000, item)
 yield()
 candidateResults.emit(SubtitleCandidateResult.Found(listOf(subtitle(1).candidate, subtitle(2).candidate)))
 candidateResults.emit(SubtitleCandidateResult.Available(subtitle(1)))
 yield()
 val prepare = launch { controller.prepareCompare(item) }
 yield()
 candidateResults.emit(SubtitleCandidateResult.Available(subtitle(2)))
 prepare.join()
 withTimeout(3000) { while (cache.load(item).size != 2) delay(5) }
 check(cache.load(item).map { it.order } == listOf(1, 2))
 controller.upload(controllerScope, UUID.randomUUID(), subtitle(1).candidate)
 yield()
 check(uploadedItem == null)
 controller.upload(controllerScope, item, subtitle(1).candidate)
 yield()
 check(uploadedItem == item)
 controller.clear()
 uploadGate.complete(Unit)
 yield()
 check(events.none { it is SubtitleDownloadEvent.Uploaded })
 collector.cancelAndJoin()
 controller.download(controllerScope, "q", 1000, item)
 yield()
 candidateResults.emit(SubtitleCandidateResult.Found(listOf(subtitle(1).candidate, subtitle(2).candidate)))
 candidateResults.emit(SubtitleCandidateResult.Available(subtitle(1)))
 yield()
 val pendingEvent = launch { controller.prepareCompare(item) }
 withTimeout(3000) { while (cache.load(item).size != 1) delay(5) }
 candidateResults.emit(SubtitleCandidateResult.Available(subtitle(2)))
 withTimeout(3000) { while (cache.load(item).size != 2) delay(5) }
 check(pendingEvent.isActive)
 pendingEvent.cancelAndJoin()
 controllerScope.cancel()
 println("通过：SRT/VTT、字幕延迟/清理、重定向与正文限额、20 路同名下载与编号耗尽、缓存并发/失效、准备对照与事件等待期间追加、切集上传拒绝")
}
'''
    worker = (ROOT / 'core/src/main/java/dev/jdtech/jellyfin/work/DownloadWorker.kt').read_text()
    http = (ROOT / 'core/src/main/java/dev/jdtech/jellyfin/core/scraper/ScraperHttp.kt').read_text()
    with tempfile.TemporaryDirectory(prefix='jd-fixes-') as directory:
        folder = Path(directory)
        for name, text in stubs.items():
            (folder / name).write_text(text)
        (folder / 'Probe.kt').write_text(main)
        (folder / 'ProbeDownload.kt').write_text('package dev.jdtech.jellyfin.core.subtitle\nimport java.io.File\nimport java.io.IOException\nobject ProbeDownload {\nconst val TEMP_SUFFIX=".download"\nconst val FIRST_DUPLICATE_INDEX=2\nconst val MAX_DUPLICATE_INDEX=99\n' + function_source(worker, 'private fun resolveFinalFile(').replace('private fun', 'fun', 1) + '\n' + function_source(worker, 'private fun appendIndex(') + '\n}')
        (folder / 'ProbeHttp.kt').write_text('package dev.jdtech.jellyfin.core.subtitle\nimport java.net.URI\nimport java.io.IOException\nimport okhttp3.ResponseBody\nimport okhttp3.HttpUrl.Companion.toHttpUrlOrNull\nobject ProbeHttp {\n' + function_source(http, 'fun resolveUrl(') + '\n' + function_source(http, 'private fun isSameHost(') + '\n' + function_source(http, 'private fun ResponseBody.readLimitedBytes(').replace('private fun', 'fun', 1) + '\nfun follows(a: String, b: String) = isSameHost(a,b)\n}')
        names = ['SubtitleTiming', 'SubtitleCueParser', 'SubtitleTextDecoder', 'SubtitleCue',
                 'SubtitleCompareCache', 'SubtitleCompareEntry', 'SubtitleDownloadController',
                 'SubtitleDownloadEvent', 'SubtitleDownloadState', 'SubtitleDownloadStage',
                 'SubtitleCandidate', 'SubtitleCandidateState', 'SubtitleCandidateResult',
                 'DownloadedSubtitle', 'SubtitleDownload', 'SubtitleDownloader', 'SubtitleLanguage']
        original = [ROOT / f'core/src/main/java/dev/jdtech/jellyfin/core/subtitle/{name}.kt' for name in names]
        original.append(ROOT / 'app/phone/src/main/java/dev/jdtech/jellyfin/presentation/player/subtitle/DelayedSubtitleView.kt')
        output = folder / 'classes'
        command = ['java', '-cp', ':'.join(dependencies), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                   '-no-stdlib', '-no-reflect', '-classpath', ':'.join(runtime), '-d', str(output)]
        result = subprocess.run(command + [str(p) for p in original + list(folder.glob('*.kt'))], capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError(result.stderr[-10000:])
        result = subprocess.run(['java', '-cp', ':'.join([str(output)] + runtime),
                                 'dev.jdtech.jellyfin.core.subtitle.ProbeKt', str(folder / 'files')],
                                capture_output=True, text=True, timeout=30)
        if result.returncode:
            raise RuntimeError(result.stdout + result.stderr)
        return result.stdout.strip()


if __name__ == '__main__':
    print(json.dumps({'sync_snapshot': verify_sync(), 'download_ownership': verify_download_ownership(),
                      'current_kotlin': verify_kotlin()}, ensure_ascii=False, indent=2))
