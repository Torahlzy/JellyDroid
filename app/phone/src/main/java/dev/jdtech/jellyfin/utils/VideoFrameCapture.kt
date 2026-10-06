package dev.jdtech.jellyfin.utils

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import dev.jdtech.jellyfin.logging.AppLog
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

// Jellyfin 后端横屏封面按 1920x1080 存放即可，上传原分辨率的 4K 只会白白拖慢传输
private const val BACKDROP_MAX_WIDTH = 1920
private const val BACKDROP_JPEG_QUALITY = 90

/**
 * 截取 [SurfaceView] 当前显示的画面并编码成 JPEG 字节。
 *
 * 播放画面由独立的 SurfaceView 承载，用 PixelCopy 取到的只有视频帧本身，不会带上控制栏。
 * [displayAspectRatio] 为视频的显示宽高比，用于裁掉 fit 缩放留下的黑边；为 null 时不裁剪。
 * 需在主线程调用；[onCaptured] 也在主线程回调，参数为 null 表示截图失败（由调用方提示用户）。
 */
fun SurfaceView.captureFrameAsJpeg(
    displayAspectRatio: Float?,
    onCaptured: (ByteArray?) -> Unit,
) {
    if (width <= 0 || height <= 0) {
        AppLog.w("截图失败：视频画面尺寸为 %dx%d", width, height)
        onCaptured(null)
        return
    }

    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    try {
        PixelCopy.request(
            this,
            bitmap,
            { result ->
                val bytes =
                    if (result == PixelCopy.SUCCESS) {
                        bitmap.toBackdropJpegBytes(displayAspectRatio)
                    } else {
                        null
                    }
                bitmap.recycle()
                if (bytes == null) {
                    AppLog.w("截图失败：PixelCopy result=%d", result)
                }
                onCaptured(bytes)
            },
            Handler(Looper.getMainLooper()),
        )
    } catch (e: IllegalArgumentException) {
        // Surface 已销毁等情况会直接抛异常，走与截图失败相同的分支
        bitmap.recycle()
        AppLog.e(e, "截图失败")
        onCaptured(null)
    }
}

/** 先裁掉黑边，再等比缩小到不超过 [BACKDROP_MAX_WIDTH]，最后编码为 JPEG；中途产生的临时位图自行回收。 */
private fun Bitmap.toBackdropJpegBytes(aspectRatio: Float?): ByteArray {
    val cropped = cropCentrally(aspectRatio)
    val scaled =
        if (cropped.width > BACKDROP_MAX_WIDTH) {
            Bitmap.createScaledBitmap(
                cropped,
                BACKDROP_MAX_WIDTH,
                (cropped.height * BACKDROP_MAX_WIDTH / cropped.width).coerceAtLeast(1),
                true,
            )
        } else {
            cropped
        }

    val bytes =
        ByteArrayOutputStream().use { output ->
            scaled.compress(Bitmap.CompressFormat.JPEG, BACKDROP_JPEG_QUALITY, output)
            output.toByteArray()
        }

    if (scaled !== cropped) {
        scaled.recycle()
    }
    if (cropped !== this) {
        cropped.recycle()
    }
    return bytes
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
