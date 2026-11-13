package dev.jdtech.jellyfin

import android.app.Application
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil3.EventListener
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.disk.DiskCache
import coil3.network.cachecontrol.CacheControlCacheStrategy
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.logging.TorahDebugTree
import dev.jdtech.jellyfin.cache.CacheManager
import dev.jdtech.jellyfin.cache.CacheCategory
import dev.jdtech.jellyfin.privacy.RecentsPrivacyLifecycleCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.MpvCleanupWorker
import dev.jdtech.jellyfin.work.SyncWorker
import dev.jdtech.jellyfin.work.UserDataSyncScheduler
import java.io.File
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import timber.log.Timber

@HiltAndroidApp
class BaseApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var appPreferences: AppPreferences

    @Inject lateinit var cacheManager: CacheManager

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var userDataSyncScheduler: UserDataSyncScheduler

    @Inject lateinit var recentsPrivacyLifecycleCallbacks: RecentsPrivacyLifecycleCallbacks

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            // 使用自定义 Tree，使所有日志的 tag 统一带上 torah 前缀
            Timber.plant(TorahDebugTree())
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val mode =
                when (appPreferences.getValue(appPreferences.theme)) {
                    "system" -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    "light" -> AppCompatDelegate.MODE_NIGHT_NO
                    "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        DynamicColors.applyToActivitiesIfAvailable(this)
        registerActivityLifecycleCallbacks(recentsPrivacyLifecycleCallbacks)

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                cacheManager.access {
                    // 旧版自建图片缓存的目录格式与框架磁盘缓存不兼容，升级后一次性删除，避免留下不计入统计的孤儿文件
                    val legacyImages = File(cacheDir, LEGACY_IMAGE_CACHE_DIR)
                    if (legacyImages.exists() && !legacyImages.deleteRecursively()) {
                        AppLog.w("旧版图片缓存目录清理失败")
                    }
                    val legacy = appPreferences.getValue(appPreferences.tagTranslateCache)
                    val target = cacheManager.directory(CacheCategory.TRANSLATIONS).resolve("tags.json")
                    if (legacy.isNotBlank() && !target.exists()) {
                        check(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
                        target.writeText(legacy)
                    }
                    appPreferences.sharedPreferences.edit().remove(appPreferences.tagTranslateCache.backendName).apply()
                    cacheManager.trim()
                }
            } catch (e: Exception) {
                AppLog.e(e, "初始化共享缓存失败")
            }
        }

        val workManager = WorkManager.getInstance(applicationContext)

        scheduleUserDataSync(workManager)
        userDataSyncScheduler.start()

        if (!appPreferences.getValue(appPreferences.mpvMigrated)) {
            scheduleMpvCleanup(workManager)
        }
    }

    @OptIn(ExperimentalCoilApi::class, ExperimentalTime::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // 图片加载只记录状态与异常类型，避免请求地址和凭据进入日志
        val imageHttpClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    // 只报 4xx/5xx：3xx 是重定向的中间响应（含 304 缓存再验证），OkHttp/Coil 会自动处理，不算失败
                    if (response.code >= 400) {
                        AppLog.w(
                            "图片请求返回 HTTP %d",
                            response.code,
                        )
                    }
                    response
                }
                .build()

        return ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        // callFactory 是工厂 lambda，每次取图时新建 Call
                        callFactory = { imageHttpClient },
                        cacheStrategy = { CacheControlCacheStrategy() },
                    )
                )
                add(SvgDecoder.Factory())
            }
            // 上限在创建时固定，设置页改动后由下次启动读取新值
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, CacheManager.IMAGE_CACHE_DIR).toOkioPath())
                    .maxSizeBytes(
                        appPreferences.getValue(appPreferences.imageCacheQuotaMb)
                            .coerceAtLeast(1) * BYTES_PER_MB,
                    )
                    .build()
            }
            .crossfade(true)
            // 异常消息与堆栈可能包含请求 URL，仅保留类型用于诊断
            .eventListener(
                object : EventListener() {
                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        AppLog.e("图片加载失败，错误类型：%s", result.throwable.javaClass.simpleName)
                    }
                }
            )
            .build()
    }

    private fun scheduleUserDataSync(workManager: WorkManager) {
        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val syncWorkRequest =
            OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints).build()

        workManager.enqueueUniqueWork(
            uniqueWorkName = "syncUserData",
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
            request = syncWorkRequest,
        )
    }

    private fun scheduleMpvCleanup(workManager: WorkManager) {
        val cleanupRequest = OneTimeWorkRequestBuilder<MpvCleanupWorker>().build()

        workManager.enqueueUniqueWork(
            uniqueWorkName = "mpv_cleanup",
            existingWorkPolicy = ExistingWorkPolicy.KEEP,
            request = cleanupRequest,
        )
    }

    private companion object {
        const val BYTES_PER_MB = 1024L * 1024
        const val LEGACY_IMAGE_CACHE_DIR = "shared_images"
    }
}
