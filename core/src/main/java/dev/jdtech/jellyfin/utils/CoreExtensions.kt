package dev.jdtech.jellyfin.utils

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.res.Resources
import android.util.Base64
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.View
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto

fun BaseItemDto.toView(items: List<FindroidItem>): View {
    return View(
        id = id,
        name = name ?: "",
        items = items,
        type = CollectionType.fromString(collectionType?.serialName),
    )
}

fun Resources.dip(px: Int) = (px * displayMetrics.density).toInt()

fun Activity.restart() {
    val intent = Intent(this, this::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    startActivity(intent)
}

fun String.base64ToByteArray(): ByteArray {
    return Base64.decode(toByteArray(StandardCharsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
}

fun ByteArray.toBase64Str(): String {
    return Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP)
}

fun DateTime.format(): String {
    val instant = this.toInstant(ZoneOffset.UTC)
    val date = Date.from(instant)
    return DateFormat.getDateInstance(DateFormat.SHORT).format(date)
}

/** 带时间的短格式（如「2025/3/8 16:12」），用于文件入库这类时间点信息。 */
fun DateTime.formatWithTime(): String {
    // DateTime 是无时区的 LocalDateTime（服务器本地时间），必须按原值格式化；
    // 经 UTC Instant 转换再用设备时区格式化会偏移时区差，时刻会错位几个小时
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).format(this)
}

fun BroadcastReceiver.launchAsync(
    coroutineScope: CoroutineScope,
    block: suspend CoroutineScope.() -> Unit,
) {
    val pendingResult = goAsync()
    coroutineScope.launch(block = block).invokeOnCompletion { pendingResult.finish() }
}
