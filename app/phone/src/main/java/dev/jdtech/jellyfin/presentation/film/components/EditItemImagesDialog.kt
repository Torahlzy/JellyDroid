package dev.jdtech.jellyfin.presentation.film.components

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.scraper.ScrapedImage
import dev.jdtech.jellyfin.core.scraper.ScraperSite
import dev.jdtech.jellyfin.core.scraper.SiteImageScrapeResult
import dev.jdtech.jellyfin.models.FindroidItemImage
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import org.jellyfin.sdk.model.api.ImageType

// 弹窗最高高度与内容区高度：图片数量不定，超出时网格内部滚动，避免弹窗撑满屏幕
private val IMAGE_DIALOG_MAX_HEIGHT = 560.dp
private val IMAGE_GRID_MAX_HEIGHT = 400.dp
private val IMAGE_THUMBNAIL_MIN_WIDTH = 96.dp

// 抓取结果里缩略图的宽度：一行最多两张（横图 + 竖图），能看清是哪张图即可
private val RESULT_THUMBNAIL_WIDTH = 96.dp

/**
 * 「编辑封面」弹窗。
 *
 * 上半部分是「当前服务器使用」的图片缩略图（点击可全屏查看），服务器上没有任何图片时该区域留空；
 * 下半部分是抓取结果：按 [scrapeSites] 列出要抓的站点，每个站点抓到什么、失败什么原因都直接显示在对应行上，
 * 成功的行末给一个「上传」按钮（上传走与「清理」同一套服务器写入逻辑：先删同类型旧图，再写入新图）。
 * 右上角「清理」二次确认后删光服务器上的图片，左下角「抓取」重新抓一遍，右下角「关闭」。
 */
@Composable
fun EditItemImagesDialog(
    itemImages: List<FindroidItemImage>,
    isLoadingImages: Boolean,
    errorText: String?,
    scrapeSites: List<ScraperSite>,
    scrapeResults: List<SiteImageScrapeResult>,
    isScraping: Boolean,
    isUploading: Boolean,
    onUploadClick: (List<ScrapedImage>) -> Unit,
    onCleanConfirm: () -> Unit,
    onScrapeClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 清理会删光服务器上的图片且不可撤销，因此还要再确认一次
    var showCleanConfirm by remember { mutableStateOf(false) }

    // 同类图片多于一张时才显示序号，避免出现「背景」「背景 2」这种缺号
    val imageCountByType = remember(itemImages) { itemImages.groupingBy { it.imageType }.eachCount() }

    // 有结果、正在抓取、或压根没配站点（此时要把「去哪儿填地址」说出来）都要显示抓取区块；
    // 都没配站点时关闭「抓取」按钮，免得点进关键词弹窗后什么都没发生
    val showScrapeSection = isScraping || scrapeResults.isNotEmpty() || scrapeSites.isEmpty()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().heightIn(max = IMAGE_DIALOG_MAX_HEIGHT),
            shape = RoundedCornerShape(28.dp),
        ) {
            Column(modifier = Modifier.padding(vertical = MaterialTheme.spacings.default)) {
                // 标题行：标题在左，清理在右上角；左右边距与按钮行一致
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacings.default),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(CoreR.string.edit_item_images),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = { showCleanConfirm = true },
                        // 服务器上没有图片时没有可清理的内容；上传中会先删同类型旧图再写新图，也不能并发清理
                        enabled = !isLoadingImages && !isUploading && itemImages.isNotEmpty(),
                    ) {
                        Text(text = stringResource(CoreR.string.clean_item_images))
                    }
                }
                Spacer(Modifier.height(MaterialTheme.spacings.small))
                DialogContentGrid(
                    itemImages = itemImages,
                    imageCountByType = imageCountByType,
                    isLoadingImages = isLoadingImages,
                    errorText = errorText,
                    scrapeSites = scrapeSites,
                    scrapeResults = scrapeResults,
                    showScrapeSection = showScrapeSection,
                    isUploading = isUploading,
                    onUploadClick = onUploadClick,
                    // 用 weight 让网格吃掉剩余高度、超出时在网格内部滚动：站点多时若让网格按内容撑高，
                    // 会把下面的按钮行顶出卡片、被卡片裁掉；fill = false 则内容少时弹窗不被白白撑高
                    modifier =
                        Modifier.weight(1f, fill = false)
                            .padding(horizontal = MaterialTheme.spacings.default),
                )
                Spacer(Modifier.height(MaterialTheme.spacings.default))
                // 按钮行：抓取在左下角，关闭在右下角
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacings.default),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onScrapeClick, enabled = scrapeSites.isNotEmpty()) {
                        Text(text = stringResource(CoreR.string.scrape))
                    }
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(CoreR.string.close))
                    }
                }
            }
        }
    }

    if (showCleanConfirm) {
        AlertDialog(
            onDismissRequest = { showCleanConfirm = false },
            title = { Text(text = stringResource(CoreR.string.clean_item_images)) },
            text = { Text(text = stringResource(CoreR.string.clean_item_images_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showCleanConfirm = false
                        onCleanConfirm()
                    }
                ) {
                    Text(
                        text = stringResource(CoreR.string.item_images_all),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showCleanConfirm = false }) {
                    Text(text = stringResource(CoreR.string.cancel))
                }
            },
        )
    }
}

/**
 * 弹窗内容：「当前服务器使用」的图片 + 抓取结果。
 *
 * 两块放进同一个 [LazyVerticalGrid]：站点结果行占满整行，整体只有一处滚动，
 * 不会出现「网格自己滚、区块又各自滚」的嵌套（同方向的嵌套滚动在 Compose 里会直接崩）。
 */
@Composable
private fun DialogContentGrid(
    itemImages: List<FindroidItemImage>,
    imageCountByType: Map<ImageType, Int>,
    isLoadingImages: Boolean,
    errorText: String?,
    scrapeSites: List<ScraperSite>,
    scrapeResults: List<SiteImageScrapeResult>,
    showScrapeSection: Boolean,
    isUploading: Boolean,
    onUploadClick: (List<ScrapedImage>) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = IMAGE_THUMBNAIL_MIN_WIDTH),
        modifier = modifier.fillMaxWidth().heightIn(max = IMAGE_GRID_MAX_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(CoreR.string.current_server_images),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        when {
            // 加载失败时优先说明原因，避免让用户误以为服务器上真的没有图片
            errorText != null ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = errorText,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            isLoadingImages ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    CircularProgressIndicator(
                        modifier =
                            Modifier.padding(MaterialTheme.spacings.small)
                                .size(MaterialTheme.spacings.default),
                        strokeWidth = 2.dp,
                    )
                }
            else ->
                items(items = itemImages) { image ->
                    PreviewableImageThumbnail(
                        model = image.uri,
                        label =
                            imageLabel(
                                image = image,
                                showIndex = (imageCountByType[image.imageType] ?: 0) > 1,
                            ),
                    )
                }
        }

        if (showScrapeSection) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(CoreR.string.scrape_images_result_title),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            if (scrapeSites.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = stringResource(CoreR.string.scrape_site_not_configured),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                // 站点行占满整行：站点名 + 抓到的图 + 上传按钮
                items(items = scrapeSites, span = { GridItemSpan(maxLineSpan) }) { site ->
                    SiteScrapeRow(
                        site = site,
                        result = scrapeResults.firstOrNull { it.site == site },
                        isUploading = isUploading,
                        onUploadClick = onUploadClick,
                    )
                }
            }
        }
    }
}

/** 一个站点的抓取结果行；[result] 为 null 表示还没轮到它（正在抓取），显示转圈。 */
@Composable
private fun SiteScrapeRow(
    site: ScraperSite,
    result: SiteImageScrapeResult?,
    isUploading: Boolean,
    onUploadClick: (List<ScrapedImage>) -> Unit,
) {
    when (result) {
        null ->
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacings.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = site.displayName, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(MaterialTheme.spacings.small))
                CircularProgressIndicator(
                    modifier = Modifier.size(MaterialTheme.spacings.default),
                    strokeWidth = 2.dp,
                )
            }
        is SiteImageScrapeResult.Failure ->
            Text(
                text =
                    stringResource(
                        CoreR.string.scrape_site_failed,
                        site.displayName,
                        result.reason,
                    ),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacings.extraSmall),
            )
        is SiteImageScrapeResult.Success ->
            Row(
                modifier =
                    Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacings.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = site.displayName, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(MaterialTheme.spacings.extraSmall))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small)
                    ) {
                        result.images.forEach { image ->
                            PreviewableImageThumbnail(
                                model = image.bytes,
                                label = imageTypeLabel(image.imageType),
                                modifier = Modifier.width(RESULT_THUMBNAIL_WIDTH),
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { onUploadClick(result.images) },
                    // 上传期间禁用，避免同一张图被重复上传
                    enabled = !isUploading,
                ) {
                    Text(text = stringResource(CoreR.string.upload))
                }
            }
    }
}

/** 服务器图片的名称；[showIndex] 为 true 且图片带索引时补上序号，以区分同类的多张。 */
@Composable
private fun imageLabel(image: FindroidItemImage, showIndex: Boolean): String {
    val typeName = imageTypeLabel(image.imageType)
    val imageIndex = image.imageIndex
    return if (showIndex && imageIndex != null) "$typeName ${imageIndex + 1}" else typeName
}

@Preview
@Composable
private fun EditItemImagesDialogPreview() {
    FindroidTheme {
        EditItemImagesDialog(
            itemImages =
                listOf(
                    FindroidItemImage(ImageType.PRIMARY, null, Uri.EMPTY),
                    FindroidItemImage(ImageType.BACKDROP, 0, Uri.EMPTY),
                ),
            isLoadingImages = false,
            errorText = null,
            scrapeSites = listOf(ScraperSite.JAV321, ScraperSite.JAVBUS, ScraperSite.JAVDB),
            scrapeResults =
                listOf(
                    SiteImageScrapeResult.Failure(
                        ScraperSite.JAV321,
                        "请求被重定向到 ng53.5dso8.top，疑似该网络出口被站点拦截",
                    ),
                    SiteImageScrapeResult.Success(
                        site = ScraperSite.JAVBUS,
                        images =
                            listOf(
                                ScrapedImage(ImageType.PRIMARY, ByteArray(0)),
                                ScrapedImage(ImageType.BACKDROP, ByteArray(0)),
                            ),
                    ),
                ),
            isScraping = true,
            isUploading = false,
            onUploadClick = {},
            onCleanConfirm = {},
            onScrapeClick = {},
            onDismiss = {},
        )
    }
}
