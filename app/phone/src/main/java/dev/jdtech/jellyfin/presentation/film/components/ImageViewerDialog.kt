package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.spacings
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// 缩放上下限：下限 1 即原始大小（不允许缩到比屏幕还小），上限避免放大到糊成一片
private const val MIN_VIEWER_SCALE = 1f
private const val MAX_VIEWER_SCALE = 5f

/**
 * 全屏查看单张图片。
 *
 * 图片按原始比例完整铺满屏幕；点击任意处关闭，双指可缩放、放大后可拖动。
 * [model] 同时接受服务器图片的地址与本地抓取图片的字节，交给 Coil 自行判断。
 * [onUpload] 非空时在底部显示「上传」按钮，用于单独上传当前查看的这一张（如抓取到的图片）；
 * 服务器上已有的图片传 null，传上去对自己没有意义。
 */
@Composable
fun ImageViewerDialog(
    model: Any?,
    contentDescription: String?,
    onDismiss: () -> Unit,
    onUpload: (() -> Unit)? = null,
    isUploading: Boolean = false,
) {
    var scale by remember { mutableFloatStateOf(MIN_VIEWER_SCALE) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        // 手势识别器都是死循环，两个并排放进同一个 pointerInput 里各跑各的协程；
                        // 拆成两个 pointerInput 会互相抢事件，点击在缩放识别器那里消费不掉就传不到下面
                        coroutineScope {
                            launch {
                                detectTapGestures(onTap = { onDismiss() })
                            }
                            launch {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(MIN_VIEWER_SCALE, MAX_VIEWER_SCALE)
                                    // 缩回原始大小时归位，避免留下偏移量让下一次打开是歪的
                                    offset = if (scale > MIN_VIEWER_SCALE) offset + pan else Offset.Zero
                                }
                            }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                modifier =
                    Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit,
            )

            if (onUpload != null) {
                // 按钮自己会消费点击事件（父级的点击关闭走 requireUnconsumed），因此点它不会顺手关掉大图
                Box(
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .padding(MaterialTheme.spacings.default),
                    contentAlignment = Alignment.Center,
                ) {
                    // 上传期间换成转圈并移走按钮：同一张图重复点会重复写服务器
                    if (isUploading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(MaterialTheme.spacings.large),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(onClick = onUpload) {
                            Text(text = stringResource(CoreR.string.upload))
                        }
                    }
                }
            }
        }
    }
}
