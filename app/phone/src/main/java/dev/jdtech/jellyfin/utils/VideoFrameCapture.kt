package dev.jdtech.jellyfin.utils

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import dev.jdtech.jellyfin.logging.AppLog
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Jellyfin 后端横屏封面按 1920x1080 存放即可，上传原分辨率的 4K 只会白白拖慢传输
private const val BACKDROP_MAX_WIDTH = 1920
private const val BACKDROP_JPEG_QUALITY = 90

/**
 * 截取 [SurfaceView] 当前显示的画面并编码成 JPEG 字节。
 *
 * 播放画面由独立的 SurfaceView 承载，用 PixelCopy 取到的只有视频帧本身，不会带上控制栏。
 * [displayAspectRatio] 为视频的显示宽高比，用于裁掉 fit 缩放留下的黑边；为 null 时不裁剪。
 * 视图读取与 PixelCopy 在主线程，裁剪和编码在后台；回调结束前不回收正在复制的位图。
 */
suspend fun SurfaceView.captureFrameAsJpeg(displayAspectRatio: Float?): ByteArray? =
    withContext(Dispatchers.Main.immediate) {
        if (width <= 0 || height <= 0) return@withContext null
        val targetWidth = width.coerceAtMost(BACKDROP_MAX_WIDTH)
        val targetHeight = (height.toLong() * targetWidth / width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        try {
            // PixelCopy 不可取消，等待其回调后再响应协程取消，防止复制期间回收目标位图。
            val result = suspendCoroutine<Int> { continuation ->
                try {
                    PixelCopy.request(this@captureFrameAsJpeg, bitmap,
                        { continuation.resume(it) }, Handler(Looper.getMainLooper()))
                } catch (e: IllegalArgumentException) {
                    AppLog.e(e, "截图失败")
                    continuation.resume(PixelCopy.ERROR_SOURCE_INVALID)
                }
            }
            if (result != PixelCopy.SUCCESS) {
                AppLog.w("截图失败：PixelCopy result=%d", result)
                return@withContext null
            }
            withContext(Dispatchers.Default) { bitmap.toBackdropJpegBytes(displayAspectRatio) }
        } finally {
            bitmap.recycle()
        }
    }

/** 先裁掉黑边，再等比缩小到不超过 [BACKDROP_MAX_WIDTH]，最后编码为 JPEG；中途产生的临时位图自行回收。 */
private fun Bitmap.toBackdropJpegBytes(aspectRatio: Float?): ByteArray {
    val cropped = cropCentrally(aspectRatio)
    var scaled = cropped
    try {
        if (cropped.width > BACKDROP_MAX_WIDTH) {
            scaled = Bitmap.createScaledBitmap(
                cropped, BACKDROP_MAX_WIDTH,
                (cropped.height * BACKDROP_MAX_WIDTH / cropped.width).coerceAtLeast(1), true,
            )
        }
        return ByteArrayOutputStream().use { output ->
            check(scaled.compress(Bitmap.CompressFormat.JPEG, BACKDROP_JPEG_QUALITY, output)) { "截图编码失败" }
            output.toByteArray()
        }
    } finally {
        if (scaled !== cropped) scaled.recycle()
        if (cropped !== this) cropped.recycle()
    }
}

/** 居中裁剪到 [aspectRatio]；比例未知或已经一致时返回自身（调用方按引用是否相同决定回收）。 */
private fun Bitmap.cropCentrally(aspectRatio: Float?): Bitmap {
    if (aspectRatio == null || aspectRatio <= 0f) {
        return this
    }

    val currentAspectRatio = width.toFloat() / height
    if (currentAspectRatio > aspectRatio) {
        // 画面比视频宽：左右是 fit 撑出来的黑边，居中裁掉
        val targetWidth = (height * aspectRatio).roundToInt().coerceIn(1, width)
        return Bitmap.createBitmap(this, (width - targetWidth) / 2, 0, targetWidth, height)
    }
    if (currentAspectRatio < aspectRatio) {
        // 画面比视频高：上下是黑边
        val targetHeight = (width / aspectRatio).roundToInt().coerceIn(1, height)
        return Bitmap.createBitmap(this, 0, (height - targetHeight) / 2, width, targetHeight)
    }
    return this
}
