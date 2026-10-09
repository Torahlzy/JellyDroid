package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil3.compose.AsyncImage
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyPerson
import dev.jdtech.jellyfin.core.scraper.ActorImageScrapeResult
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

// 头像统一按圆角方图显示，与演员行的小头像观感一致
private val ACTOR_IMAGE_SIZE = 48.dp

// 弹窗最高高度与列表滚动区高度：演员多时列表内部滚动，避免弹窗撑满屏幕
private val ACTOR_IMAGES_DIALOG_MAX_HEIGHT = 540.dp
private val ACTOR_IMAGES_LIST_MAX_HEIGHT = 400.dp

/**
 * 「编辑演员头像」弹窗。
 *
 * 列出当前影片的演员：已有头像的标出来不再处理；缺头像的按 [results] 里对应名字的结果
 * 显示「已抓到（可上传）/ 失败原因 / 抓取中」。抓取与上传都是显式动作——抓取只把图片留在
 * 内存里，用户逐人（或全部）确认后才写到服务器上的人物条目。
 *
 * [onUploadClick] 收到的列表即待上传的头像；传全部即「全部上传」。
 */
@Composable
fun EditActorImagesDialog(
    actors: List<FindroidItemPerson>,
    results: List<ActorImageScrapeResult>,
    isScraping: Boolean,
    isUploading: Boolean,
    onScrapeClick: () -> Unit,
    onUploadClick: (List<ActorImageScrapeResult.Success>) -> Unit,
    onDismiss: () -> Unit,
) {
    // 抓取结果按名字索引：结果与演员一一对应，用名字关联而不是下标，避免两边顺序对不上
    val resultsByName = results.associateBy { it.name }
    // 已抓到且还没上传的头像；「全部上传」一次全带上
    val scrapedImages =
        results.filterIsInstance<ActorImageScrapeResult.Success>()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = ACTOR_IMAGES_DIALOG_MAX_HEIGHT),
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.spacings.default)) {
                Text(
                    text = stringResource(CoreR.string.edit_actor_images),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                if (actors.isEmpty()) {
                    Text(
                        text = stringResource(CoreR.string.actor_images_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = ACTOR_IMAGES_LIST_MAX_HEIGHT),
                        verticalArrangement =
                            Arrangement.spacedBy(MaterialTheme.spacings.small),
                    ) {
                        items(actors, key = { it.id }) { actor ->
                            ActorImageRow(
                                actor = actor,
                                result = resultsByName[actor.name],
                                isScraping = isScraping,
                                isUploading = isUploading,
                                onUploadClick = { image -> onUploadClick(listOf(image)) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(MaterialTheme.spacings.default))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 抓取 / 全部上传都在左侧：右侧只留关闭，避免和「取消保存」类语义混淆
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)) {
                        TextButton(
                            onClick = onScrapeClick,
                            enabled = !isScraping && !isUploading,
                        ) {
                            Text(text = stringResource(CoreR.string.actor_images_scrape))
                        }
                        if (scrapedImages.size > 1) {
                            TextButton(
                                onClick = { onUploadClick(scrapedImages) },
                                enabled = !isScraping && !isUploading,
                            ) {
                                Text(text = stringResource(CoreR.string.actor_images_upload_all))
                            }
                        }
                    }
                    TextButton(onClick = onDismiss, enabled = !isScraping && !isUploading) {
                        Text(text = stringResource(CoreR.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActorImageRow(
    actor: FindroidItemPerson,
    result: ActorImageScrapeResult?,
    isScraping: Boolean,
    isUploading: Boolean,
    onUploadClick: (ActorImageScrapeResult.Success) -> Unit,
) {
    val hasAvatar = actor.image.uri != null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActorAvatar(actor)
        Text(
            text = actor.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        when {
            hasAvatar -> Text(
                text = stringResource(CoreR.string.actor_images_has_avatar),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            result is ActorImageScrapeResult.Success -> TextButton(
                onClick = { onUploadClick(result) },
                enabled = !isScraping && !isUploading,
            ) {
                Text(text = stringResource(CoreR.string.actor_images_upload))
            }
            result is ActorImageScrapeResult.Failure -> Text(
                text = result.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            // 还没抓到结果：抓取进行中显示进度，否则提示「未抓取」
            isScraping -> CircularProgressIndicator(
                modifier = Modifier.size(MaterialTheme.spacings.default),
                strokeWidth = 2.dp,
            )
            else -> Text(
                text = stringResource(CoreR.string.actor_images_not_scraped),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 人物头像：有图显示图，没图显示一个占位圆。 */
@Composable
private fun ActorAvatar(actor: FindroidItemPerson) {
    Box(modifier = Modifier.size(ACTOR_IMAGE_SIZE)) {
        val uri = actor.image.uri
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = actor.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ACTOR_IMAGE_SIZE).clip(CircleShape),
            )
        } else {
            Text(
                text = actor.name.take(1),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

@Preview
@Composable
private fun EditActorImagesDialogPreview() {
    FindroidTheme {
        EditActorImagesDialog(
            actors = listOf(dummyPerson),
            results =
                listOf(
                    ActorImageScrapeResult.Failure("不存在的人", "站点上找不到该演员"),
                ),
            isScraping = false,
            isUploading = false,
            onScrapeClick = {},
            onUploadClick = {},
            onDismiss = {},
        )
    }
}
