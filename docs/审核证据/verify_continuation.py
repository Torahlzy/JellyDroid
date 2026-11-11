"""续审隔离探针：编译纯逻辑源码及最小平台替身，不构建或运行 Android 应用。"""
import json
from pathlib import Path
import re
import subprocess
import tempfile
import threading

ROOT = Path(__file__).resolve().parents[2]
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'


def jar(group, artifact, version):
    matches = [p for p in (CACHE / group / artifact / version).glob('*/*.jar')
               if p.name == f'{artifact}-{version}.jar']
    if not matches:
        raise RuntimeError(f'缺少离线依赖：{group}:{artifact}:{version}')
    return str(matches[0])


def function_source(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def verify_kotlin_sources():
    compiler = jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.4.10')
    stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.4.10')
    coroutines = jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0')
    dependencies = [compiler, stdlib, coroutines]
    # 编译器的运行依赖从本机已有缓存选取，不下载依赖。
    for group, artifact in [('org.jetbrains.kotlin', 'kotlin-script-runtime'),
                            ('org.jetbrains.kotlin', 'kotlin-reflect'),
                            ('org.jetbrains.intellij.deps', 'trove4j'),
                            ('org.jetbrains', 'annotations')]:
        found = sorted(p for p in (CACHE / group / artifact).glob('*/*/*.jar')
                       if not p.name.endswith(('-sources.jar', '-javadoc.jar')))
        if found:
            dependencies.append(str(found[-1]))
    main = r'''
package dev.jdtech.jellyfin.core.subtitle
import android.content.Context
import android.os.Handler
import dev.jdtech.jellyfin.presentation.player.subtitle.DelayedSubtitleView
import androidx.media3.common.text.Cue
import kotlinx.coroutines.flow.MutableStateFlow

data class DiscoveryState(val servers: List<String> = emptyList())
fun main() {
    val shortVtt = "WEBVTT\n\n00:01.000 --> 00:02.000\nhello\n"
    val fullVtt = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nhello\n"
    check(SubtitleCueParser.parse(shortVtt).isEmpty())
    check(SubtitleTiming.lastTimestampMillis(shortVtt) == null)
    check(SubtitleCueParser.parse(fullVtt).single().endMs == 2000L)
    check(ProbeHttp.resolveUrl("https://example.test/a", "//cdn.example.test/b") == "https:cdn.example.test/b")
    check(ProbeHttp.resolveUrl("https://example.test/a", "/b") == "https://example.test/b")
    val sameHostRedirect = ProbeHttp.resolveUrl("https://example.test/a", "//example.test/b")!!
    check(!ProbeHttp.follows("https://example.test/a", sameHostRedirect))
    val view = DelayedSubtitleView(Context())
    view.delayMs = 5000L
    view.setCues(listOf(Cue("A")))
    Handler.now = 1000L
    view.setCues(emptyList())
    Handler.advanceTo(5000L)
    check(view.subtitleView.history.none { it.any { cue -> cue.text == "A" } })
    Handler.advanceTo(6000L)
    check(view.subtitleView.history.last().isEmpty())
    val state = MutableStateFlow(DiscoveryState())
    val mutable = mutableListOf("server-a")
    state.value = state.value.copy(servers = mutable)
    val firstState = state.value
    mutable.add("server-b")
    state.value = state.value.copy(servers = mutable)
    check(state.value === firstState)
    println("短格式 VTT=0 cue；标准长格式=1 cue；协议相对 URL 缺少 //；延迟短句丢失；发现列表第二次赋值被 StateFlow 合并")
}
'''
    stubs = {
        'Context.kt': 'package android.content\nopen class Context',
        'Attrs.kt': 'package android.util\ninterface AttributeSet',
        'Views.kt': '''package android.widget
import android.content.Context
import android.util.AttributeSet
open class FrameLayout(c: Context, a: AttributeSet?, s: Int) {
 class LayoutParams(w: Int, h: Int) { companion object { const val MATCH_PARENT = -1 } }
 fun addView(v: Any, p: LayoutParams) {}
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
object AppLog { fun w(s: String, vararg a: Any?) {} ; fun d(s: String, vararg a: Any?) {} }''',
    }
    scraper = (ROOT / 'core/src/main/java/dev/jdtech/jellyfin/core/scraper/ScraperHttp.kt').read_text()
    resolve = function_source(scraper, 'fun resolveUrl(')
    same_host = function_source(scraper, 'private fun isSameHost(')
    with tempfile.TemporaryDirectory(prefix='jd-review-kotlin-') as directory:
        folder = Path(directory)
        for name, source in stubs.items():
            (folder / name).write_text(source)
        (folder / 'Probe.kt').write_text(main)
        (folder / 'ProbeHttp.kt').write_text('package dev.jdtech.jellyfin.core.subtitle\nimport java.net.URI\nobject ProbeHttp {\n' + resolve + '\n' + same_host + '\nfun follows(a: String, b: String) = isSameHost(a, b)\n}')
        original = [ROOT / f'core/src/main/java/dev/jdtech/jellyfin/core/subtitle/{name}.kt'
                    for name in ['SubtitleTiming', 'SubtitleCueParser', 'SubtitleTextDecoder', 'SubtitleCue']]
        original.append(ROOT / 'app/phone/src/main/java/dev/jdtech/jellyfin/presentation/player/subtitle/DelayedSubtitleView.kt')
        output = folder / 'classes'
        command = ['java', '-cp', ':'.join(dependencies), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                   '-no-stdlib', '-no-reflect', '-classpath', ':'.join([stdlib, coroutines] + dependencies[-1:]),
                   '-d', str(output)] + [str(p) for p in original + list(folder.glob('*.kt'))]
        result = subprocess.run(command, capture_output=True, text=True)
        if result.returncode:
            raise RuntimeError(result.stderr[-8000:])
        result = subprocess.run(['java', '-cp', ':'.join([str(output), stdlib, coroutines]),
                                 'dev.jdtech.jellyfin.core.subtitle.ProbeKt'],
                                capture_output=True, text=True, check=True)
        return result.stdout.strip()


def verify_cache_interleaving():
    # 精确控制两个阶段的文件时序，验证「IO 前校验」不足以保护 clear；不是 Android 协程运行测试。
    source = (ROOT / 'core/src/main/java/dev/jdtech/jellyfin/core/subtitle/SubtitleCompareCache.kt').read_text()
    upsert = function_source(source, 'suspend fun upsert(')
    assert upsert.index('currentToken != batchToken') < upsert.index('withContext(Dispatchers.IO)')
    checked = threading.Event()
    proceed = threading.Event()
    generation = [0]
    with tempfile.TemporaryDirectory(prefix='jd-review-cache-') as directory:
        target = Path(directory) / 'item'
        def old_writer():
            token = generation[0]
            assert token == 0
            checked.set()
            assert proceed.wait(5)
            target.mkdir(exist_ok=True)
            (target / 'manifest.json').write_text('old-batch')
        thread = threading.Thread(target=old_writer)
        thread.start()
        assert checked.wait(5)
        generation[0] += 1
        assert not target.exists()
        proceed.set()
        thread.join(5)
        assert not thread.is_alive()
        assert (target / 'manifest.json').read_text() == 'old-batch'
    return '等价时序探针：clear 完成后旧批次仍可重新建目录并发布清单'


if __name__ == '__main__':
    print(json.dumps({'actual_kotlin_with_platform_stubs': verify_kotlin_sources(),
                      'equivalent_cache_interleaving': verify_cache_interleaving()}, ensure_ascii=False, indent=2))
