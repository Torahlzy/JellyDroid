package dev.jdtech.jellyfin.core.scraper

import org.jellyfin.sdk.model.api.ImageType

/**
 * 抓取到、尚未上传的一张图片。
 *
 * [imageType] 决定上传到服务器后落到哪一类（[ImageType.PRIMARY] 竖图 / [ImageType.BACKDROP] 横图）。
 *
 * 刻意不写成 `data class`：内部的 [bytes] 是 `ByteArray`，自动生成的 `equals` 只比引用，
 * 名字叫「相等」却给出误导结果；状态里每次都是整体替换列表，用引用比较判断变化已经足够。
 */
class ScrapedImage(val imageType: ImageType, val bytes: ByteArray)
