package dev.jdtech.jellyfin.core.subtitle

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.jdtech.jellyfin.logging.AppLog
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 字幕下载的临时目录：`cacheDir/subtitle_temp`。
 *
 * 放 `cacheDir` 而不是 `filesDir`：这里只是「这次选中的是哪份字幕」的落盘留痕，
 * 下载完就上传到服务器，本机不需要长期保存，交给系统在空间不足时回收。
 * 每次使用前清掉 3 天前的文件，避免反复下载把缓存撑大。
 */
@Singleton
class SubtitleCache
@Inject
constructor(@ApplicationContext private val context: Context) {

    private val directory: File
        get() = File(context.cacheDir, DIR_NAME)

    /** 删除 [EXPIRE_MILLIS] 之前的临时字幕；失败只记日志，不影响本次下载。 */
    suspend fun deleteExpired() =
        withContext(Dispatchers.IO) {
            val files = directory.listFiles() ?: return@withContext
            val deadline = System.currentTimeMillis() - EXPIRE_MILLIS
            files
                .filter { it.isFile && it.lastModified() < deadline }
                .forEach { file ->
                    if (file.delete()) {
                        AppLog.d("清理过期字幕临时文件：%s", file.name)
                    }
                }
        }

    /** 把候选字幕写进临时目录，便于事后核对「选中的是哪个候选」；写失败不影响上传。 */
    suspend fun save(subtitle: SubtitleDownload, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            val language = subtitle.language.subtitlecatKey
            val name = "${System.currentTimeMillis()}-$language.${subtitle.format}"
            val file = File(directory, name)
            runCatching {
                    file.parentFile?.mkdirs()
                    file.writeBytes(bytes)
                }
                .onSuccess { AppLog.d("字幕已保存到临时目录：%s", file.name) }
                .onFailure { e -> AppLog.w(e, "写入字幕临时文件失败：%s", file.name) }
        }

    private companion object {
        const val DIR_NAME = "subtitle_temp"

        /** 3 天。 */
        const val EXPIRE_MILLIS = 3L * 24 * 60 * 60 * 1000
    }
}
