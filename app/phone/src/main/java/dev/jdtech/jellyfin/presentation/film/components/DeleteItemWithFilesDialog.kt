package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

/**
 * 「删除全部」的确认弹窗。
 *
 * 这一项会连服务器上的视频文件一起删，属于不可撤销的操作，所以单独再确认一次。
 */
@Composable
fun DeleteItemWithFilesDialog(
    itemName: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        title = { Text(text = stringResource(CoreR.string.delete_item_with_files)) },
        text = {
            Column {
                Text(text = deleteAllMessage())
                itemName?.let { name ->
                    Spacer(Modifier.height(MaterialTheme.spacings.small))
                    Text(
                        text = stringResource(CoreR.string.delete_all_item_name, name),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
            ) {
                Text(text = stringResource(CoreR.string.remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(CoreR.string.cancel)) }
        },
    )
}

/**
 * 删除提示整句，其中「视频文件」用错误色强调：被删掉的不只是服务器上的记录，还有实体文件。
 *
 * 强调词单独取一份资源、再回整句里定位，这样整句仍是一段完整译文，翻译时不必把句子拆成碎片；
 * 若某种语言的译文里没有这段文字，就退化成不着色的整句，不影响阅读。
 */
@Composable
private fun deleteAllMessage(): AnnotatedString {
    val message = stringResource(CoreR.string.delete_all_message)
    val highlighted = stringResource(CoreR.string.delete_all_message_video_file)
    // 与确认按钮同色（error），保持「删除」相关提示的视觉一致
    val highlightColor = MaterialTheme.colorScheme.error
    val startIndex = message.indexOf(highlighted)

    if (startIndex < 0) return AnnotatedString(message)

    return buildAnnotatedString {
        append(message.substring(0, startIndex))
        withStyle(SpanStyle(color = highlightColor)) { append(highlighted) }
        append(message.substring(startIndex + highlighted.length))
    }
}

@Preview
@Composable
private fun DeleteItemWithFilesDialogPreview() {
    FindroidTheme {
        DeleteItemWithFilesDialog(itemName = "示例影片", onConfirm = {}, onDismiss = {})
    }
}
