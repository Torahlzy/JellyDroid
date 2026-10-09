package dev.jdtech.jellyfin.film.presentation.actorimage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.core.scraper.ActorImageScraper
import dev.jdtech.jellyfin.core.scraper.SiteActorImageScrapeResult
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.ImageType

/**
 * 「获取头像」弹窗的编排：抓取、上传与代理设置。
 *
 * 独立于具体页面（电影详情页 / 人物列表页都靠长按演员条目打开它），头像统一上传为
 * 人物条目（Person）的服务器封面，成功后由界面自己决定怎么刷新列表。
 */
@HiltViewModel
class ActorImageScrapeViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val actorImageScraper: ActorImageScraper,
) : ViewModel() {
    // 站点列表来自本地配置（BuildConfig），整个会话内不会变，直接放进初始 state，
    // 免得打开弹窗时要等一次异步加载、先闪一下「没配站点」的空态
    private val _state =
        MutableStateFlow(
            ActorImageScrapeState(
                sites = actorImageScraper.sites,
                proxy = appPreferences.getValue(appPreferences.scrapeProxy),
            )
        )
    val state = _state.asStateFlow()

    private val eventsChannel = Channel<ActorImageScrapeEvent>()
    val events = eventsChannel.receiveAsFlow()

    /** 正在跑的抓取任务，用户取消时直接 cancel 掉。 */
    private var scrapeJob: Job? = null

    fun onAction(action: ActorImageScrapeAction) {
        when (action) {
            is ActorImageScrapeAction.Scrape -> {
                scrapeActorImage(action.personId, action.name, action.hasAvatar)
            }
            is ActorImageScrapeAction.Upload -> {
                uploadActorImage(action.image)
            }
            is ActorImageScrapeAction.UpdateProxy -> {
                updateProxy(action.address)
            }
            is ActorImageScrapeAction.Close -> {
                close()
            }
        }
    }

    /** 打开「获取头像」弹窗并立即抓取演员头像，各站点的结果按站点覆盖进 state 供界面展示。 */
    private fun scrapeActorImage(personId: UUID, name: String, hasAvatar: Boolean) {
        // 上传还没结束时又点开别的演员，会把头像传到错的人身上，直接忽略（也不要取消上传）
        if (_state.value.isUploading) return
        scrapeJob?.cancel()
        scrapeJob =
            viewModelScope.launch {
                _state.emit(
                    _state.value.copy(
                        personId = personId,
                        personName = name,
                        hasAvatar = hasAvatar,
                        results = emptyList(),
                    )
                )
                // 搜索 + 下载都是网络 IO，放到 IO 线程，别占着主线程
                withContext(Dispatchers.IO) {
                    try {
                        actorImageScraper.scrapeActorImage(name) { result ->
                            // 抓取器已把并发回调串行化，按站点替换结果即可
                            val updated =
                                _state.value.results.filterNot { it.site == result.site } + result
                            _state.emit(_state.value.copy(results = updated))
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 抓取器内部已把单站失败转成结果，这里兜住整体异常，不让页面崩掉
                        AppLog.e(e, "抓取演员头像出错")
                        // 还没出结果的站点补一条失败：否则这些行会一直停在「抓取中」的转圈状态
                        failPendingSites(e)
                    }
                }
            }
    }

    /** 给所有还没出结果的站点补一条失败结果，[error] 的说明直接展示给用户。 */
    private suspend fun failPendingSites(error: Exception) {
        val results = _state.value.results
        val pending =
            _state.value.sites.filterNot { site -> results.any { result -> result.site == site } }
        if (pending.isEmpty()) return
        val reason = error.message?.takeIf { it.isNotBlank() } ?: DEFAULT_ERROR_REASON
        val failed = pending.map { site -> SiteActorImageScrapeResult.Failure(site, reason) }
        _state.emit(_state.value.copy(results = results + failed))
    }

    /**
     * 把 [image]（某个站点抓到的头像）上传为对应人物在服务器上的封面。
     *
     * 人物条目与影片解耦：头像挂在 Person 上，该演员的所有影片都会受益，直接用弹窗当前演员的人物 Id 上传。
     */
    private fun uploadActorImage(image: SiteActorImageScrapeResult.Success) {
        if (_state.value.isUploading) return
        val personId = _state.value.personId ?: return
        viewModelScope.launch {
            _state.emit(_state.value.copy(isUploading = true))
            try {
                repository.setItemImage(personId, ImageType.PRIMARY, image.bytes)
                eventsChannel.send(ActorImageScrapeEvent.Uploaded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "上传演员头像失败")
                eventsChannel.send(ActorImageScrapeEvent.UploadFailed(e))
            } finally {
                _state.emit(_state.value.copy(isUploading = false))
            }
        }
    }

    /** 保存抓取专用的本地代理，只影响之后的抓取请求。 */
    private fun updateProxy(address: String) {
        appPreferences.setValue(appPreferences.scrapeProxy, address)
        viewModelScope.launch { _state.emit(_state.value.copy(proxy = address)) }
    }

    /** 关闭「获取头像」弹窗：中断进行中的抓取，并丢掉已抓到的头像（都只在内存里）。 */
    private fun close() {
        scrapeJob?.cancel()
        scrapeJob = null
        viewModelScope.launch {
            _state.emit(
                _state.value.copy(
                    personId = null,
                    personName = "",
                    hasAvatar = false,
                    results = emptyList(),
                )
            )
        }
    }

    private companion object {
        /** 异常没有自带说明时，界面上展示的兜底文案。 */
        const val DEFAULT_ERROR_REASON = "未知错误"
    }
}
