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
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.google.android.material.color.DynamicColors
import dagger.hilt.android.HiltAndroidApp
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.logging.TorahDebugTree
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.work.MpvCleanupWorker
import dev.jdtech.jellyfin.work.SyncWorker
import javax.inject.Inject
import kotlin.time.ExperimentalTime
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import timber.log.Timber

@HiltAndroidApp
class BaseApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var appPreferences: AppPreferences

    @Inject lateinit var workerFactory: HiltWorkerFactory

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

        if (appPreferences.getValue(appPreferences.dynamicColors)) {
            DynamicColors.applyToActivitiesIfAvailable(this)
        }

        val workManager = WorkManager.getInstance(applicationContext)

        scheduleUserDataSync(workManager)

        if (!appPreferences.getValue(appPreferences.mpvMigrated)) {
            scheduleMpvCleanup(workManager)
        }
    }

    @OptIn(ExperimentalCoilApi::class, ExperimentalTime::class)
    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // 图片加载专用 OkHttp：非正常响应时打印状态码与 URL，配合 eventListener 定位加载失败原因
        val imageHttpClient =
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val response = chain.proceed(chain.request())
                    // 只报 4xx/5xx：3xx 是重定向的中间响应（含 304 缓存再验证），OkHttp/Coil 会自动处理，不算失败
                    if (response.code >= 400) {
                        AppLog.w(
                            "图片请求返回 %d：%s",
                            response.code,
                            response.request.url,
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
            .diskCachePolicy(
                if (appPreferences.getValue(appPreferences.imageCache)) CachePolicy.ENABLED
                else CachePolicy.DISABLED
            )
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(
                        appPreferences.getValue(appPreferences.imageCacheSize) * 1024L * 1024
                    )
                    .build()
            }
            .crossfade(true)
            // 排查图片加载失败用：任何请求失败都打印 URL 与异常，logcat 过滤 torah 即可看到
            .eventListener(
                object : EventListener() {
                    override fun onError(request: ImageRequest, result: ErrorResult) {
                        AppLog.e(result.throwable, "图片加载失败：%s", request.data)
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
}
