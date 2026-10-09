package dev.jdtech.jellyfin.core.scraper

/** 按演员名抓头像的逐人结果：[Success] 与 [Failure] 各对应界面上的一行。 */
sealed interface ActorImageScrapeResult {
    val name: String

    /** 抓到了头像字节，等待用户确认后上传。 */
    data class Success(override val name: String, val bytes: ByteArray) : ActorImageScrapeResult

    /** 该演员抓不到头像，[reason] 说明原因，界面直接展示。 */
    data class Failure(override val name: String, val reason: String) : ActorImageScrapeResult
}
