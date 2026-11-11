package dev.jdtech.jellyfin.player.local.presentation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.domain.models.ExternalSubtitle
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.domain.PlaylistManager
import dev.jdtech.jellyfin.player.local.mpv.MPVPlayer
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    private val application: Application,
    private val playlistManager: PlaylistManager,
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(), Player.Listener {
    val player: Player

    private val _uiState =
        MutableStateFlow(
            UiState(
                currentItemTitle = "",
                currentSegment = null,
                currentSkipButtonStringRes = R.string.player_controls_skip_intro,
                currentTrickplay = null,
                currentChapters = emptyList(),
                fileLoaded = false,
            )
        )
    val uiState = _uiState.asStateFlow()

    private val eventsChannel = Channel<PlayerEvents>()
    val eventsChannelFlow = eventsChannel.receiveAsFlow()

    data class UiState(
        val currentItemTitle: String,
        val currentSegment: FindroidSegment?,
        val currentSkipButtonStringRes: Int,
        val currentTrickplay: Trickplay?,
        val currentChapters: List<PlayerChapter>,
        val fileLoaded: Boolean,
    )

    private var items: MutableList<PlayerItem> = mutableListOf()

    private val trackSelector = DefaultTrackSelector(application)
    var playWhenReady = true
    private var currentMediaItemIndex = savedStateHandle["mediaItemIndex"] ?: 0
    private var playbackPosition: Long = savedStateHandle["position"] ?: 0
    private var currentMediaItemSegments: List<FindroidSegment> = emptyList()

    // Segments preferences
    var segmentsSkipButton: Boolean = false
    private var segmentsSkipButtonTypes: Set<String> = emptySet()
    var segmentsSkipButtonDuration: Long = 0L
    var segmentsAutoSkip: Boolean = false
    private var segmentsAutoSkipTypes: Set<String> = emptySet()
    private var segmentsAutoSkipMode: String = "always"

    var playbackSpeed: Float = 1f

    var isInPictureInPictureMode: Boolean = false

    /**
     * 正在为「应用对照字幕」重载当前条目。
     *
     * 重载期间暂停进度上报：重载会把播放位置短暂清零，此时上报会把服务器上的观看进度冲回开头。
     */
    private var isReloadingForSubtitle: Boolean = false
    private var subtitleReloadItemId: String? = null
    private var subtitleReloadJob: Job? = null
    private var pendingSubtitleId: String? = null

    /** 每次切集或重新开播均换代，异步操作不能跨播放会话生效。 */
    var currentPlaybackSession: Long = 0L
        private set

    /** 重载标记的兜底时长：超过它还没到 READY 就强制恢复进度上报（见 [reloadCurrentItemWithExternalSubtitle]）。 */
    private val reloadFlagTimeoutMs = 15_000L

    init {
        segmentsSkipButton = appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton)
        segmentsSkipButtonTypes =
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonType)
        segmentsSkipButtonDuration =
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonDuration)
        segmentsAutoSkip = appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)
        segmentsAutoSkipTypes =
            appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipType)
        segmentsAutoSkipMode =
            appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipMode)

        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        // 字幕默认选择：优先用播放器里记住的选择（手动选过的轨语言或手动关闭），
        // 从未手动选过才回落到设置页的语言偏好
        val rememberedSubtitleTrack =
            appPreferences.getValue(appPreferences.playerLastSubtitleTrack)
        val parametersBuilder =
            trackSelector
                .buildUponParameters()
                .setTunnelingEnabled(true)
                .setPreferredAudioLanguage(
                    appPreferences.getValue(appPreferences.preferredAudioLanguage)
                )
        when (rememberedSubtitleTrack) {
            Constants.SUBTITLE_TRACK_OFF -> parametersBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            null ->
                parametersBuilder.setPreferredTextLanguage(
                    appPreferences.getValue(appPreferences.preferredSubtitleLanguage)
                )
            else -> parametersBuilder.setPreferredTextLanguage(rememberedSubtitleTrack)
        }
        trackSelector.setParameters(parametersBuilder)

        val playerBackend = appPreferences.getValue(appPreferences.playerBackend)
        player =
            when (playerBackend) {
                "exoplayer" -> {
                    val renderersFactory =
                        DefaultRenderersFactory(application)
                            .setExtensionRendererMode(
                                DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            )
                    ExoPlayer.Builder(application, renderersFactory)
                        .setAudioAttributes(audioAttributes, true)
                        .setTrackSelector(trackSelector)
                        .setSeekBackIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekBackInc)
                        )
                        .setSeekForwardIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekForwardInc)
                        )
                        .setPauseAtEndOfMediaItems(true)
                        .build()
                }
                "mpv" -> {
                    MPVPlayer.Builder(application)
                        .setAudioAttributes(audioAttributes, true)
                        .setTrackSelectionParameters(trackSelector.parameters)
                        .setSeekBackIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekBackInc)
                        )
                        .setSeekForwardIncrementMs(
                            appPreferences.getValue(appPreferences.playerSeekForwardInc)
                        )
                        .setPauseAtEndOfMediaItems(true)
                        .setVideoOutput(appPreferences.getValue(appPreferences.playerMpvVo))
                        .setAudioOutput(appPreferences.getValue(appPreferences.playerMpvAo))
                        .setHwDec(appPreferences.getValue(appPreferences.playerMpvHwdec))
                        .build()
                }

                else -> throw RuntimeException("$playerBackend is not a valid player backend")
            }
    }

    fun initializePlayer(
        itemId: UUID,
        itemKind: String,
        startFromBeginning: Boolean,
        mediaSourceIndex: Int? = null,
    ) {
        currentPlaybackSession++
        subtitleReloadJob?.cancel()
        isReloadingForSubtitle = false
        subtitleReloadItemId = null
        pendingSubtitleId = null
        player.addListener(this)

        viewModelScope.launch {
            repository.initialize()
            val startItem =
                try {
                    playlistManager.getInitialItem(
                        itemId = itemId,
                        itemKind = BaseItemKind.fromName(itemKind),
                        mediaSourceIndex = mediaSourceIndex,
                        startFromBeginning = startFromBeginning,
                    )
                } catch (e: Exception) {
                    AppLog.e("播放器操作失败，错误类型：%s", e.javaClass.simpleName)
                    Toast.makeText(application, e.localizedMessage, Toast.LENGTH_LONG).show()
                    null
                }

            if (startItem == null) {
                AppLog.e("No start item, stopping player initialization")
                return@launch
            }

            items = listOfNotNull(startItem).toMutableList()
            currentMediaItemIndex = items.indexOf(startItem)

            val mediaItems = mutableListOf<MediaItem>()
            try {
                for (item in items) {
                    mediaItems.add(item.toMediaItem())
                }
            } catch (e: Exception) {
                AppLog.e("播放器操作失败，错误类型：%s", e.javaClass.simpleName)
            }

            val startPosition =
                if (playbackPosition == 0L) {
                    items.getOrNull(currentMediaItemIndex)?.playbackPosition ?: C.TIME_UNSET
                } else {
                    playbackPosition
                }

            player.setMediaItems(mediaItems, 0, startPosition)
            player.prepare()
            player.play()
        }
    }

    private fun PlayerItem.toMediaItem(extraSubtitle: ExternalSubtitle? = null): MediaItem {
        val streamUrl = mediaSourceUri
        val allSubtitles = externalSubtitles + listOfNotNull(extraSubtitle)
        val mediaSubtitles = allSubtitles.map { externalSubtitle ->
            MediaItem.SubtitleConfiguration.Builder(externalSubtitle.uri)
                .setId(externalSubtitle.uri.toString())
                .setLabel(
                    externalSubtitle.title.ifBlank { application.getString(R.string.external) }
                )
                .setMimeType(externalSubtitle.mimeType)
                .setLanguage(externalSubtitle.language)
                .build()
        }

        AppLog.d("准备播放条目：%s", itemId)
        val mediaItem =
            MediaItem.Builder()
                .setMediaId(itemId.toString())
                .setUri(streamUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
                .setSubtitleConfigurations(mediaSubtitles)
                .build()

        return mediaItem
    }

    /**
     * 把刚上传的字幕挂到当前播放器上（ExoPlayer 后端）。
     *
     * 不能只 `replaceMediaItem`：实测在已 READY 的播放器上原地替换当前条目后，
     * 虽然会 BUFFERING → READY，但新的 SubtitleConfiguration 不进播放图，
     * `currentTracks` 里始终没有文本轨、连 `onTracksChanged` 都不派发。
     * 因此改走与重新播放相同的 `setMediaItems` 重建播放列表，并顺带重取一次
     * 服务器外挂字幕，并用具有唯一 id 的本地副本明确选中本次应用的字幕。
     * 播放位置、播放状态与上下集列表都在这里显式保住。
     */
    fun reloadCurrentItemWithExternalSubtitle(uri: Uri, label: String, language: String) {
        if (player.currentMediaItem == null) {
            AppLog.tag("SubSideload").w("注入中止：currentMediaItem 为空")
            return
        }
        val currentIndex = player.currentMediaItemIndex
        val item = items.getOrNull(currentIndex)
        if (item == null) {
            AppLog.tag("SubSideload").w(
                "注入中止：items 里没有 index=%d 的条目（共 %d 条）",
                currentIndex,
                items.size,
            )
            return
        }
        val session = currentPlaybackSession
        val extraSubtitle =
            ExternalSubtitle(
                title = label,
                language = language,
                uri = uri,
                mimeType = subtitleMimeType(uri),
            )
        val parameters = player.trackSelectionParameters
        AppLog.tag("SubSideload").d(
            "注入前：index=%d，state=%d，文本组=%d，TEXT 禁用=%b，override=%d，playlist=%d，items=%d，mime=%s",
            currentIndex,
            player.playbackState,
            player.currentTracks.groups.count { it.type == C.TRACK_TYPE_TEXT },
            C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes,
            parameters.overrides.size,
            player.mediaItemCount,
            items.size,
            extraSubtitle.mimeType,
        )
        subtitleReloadJob?.cancel()
        subtitleReloadJob = viewModelScope.launch {
            // 服务器刚把上传的字幕写进 mediaStreams，重取一次；失败时保留播放会话原有的字幕列表，
            // 否则会把影片原本就有的外挂字幕一起丢掉
            val serverSubtitles =
                try {
                    playlistManager.getExternalSubtitles(item.itemId, item.mediaSourceId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.tag("SubSideload").w("重取服务器字幕失败，错误类型：%s", e.javaClass.simpleName)
                    item.externalSubtitles
                }
            if (session != currentPlaybackSession || currentItemId != item.itemId ||
                player.currentMediaItemIndex != currentIndex ||
                items.getOrNull(currentIndex)?.mediaSourceId != item.mediaSourceId) return@launch
            // 索引数量不能识别具体的新轨，明确应用时始终选择有唯一 id 的本地副本。
            items[currentIndex] = item.copy(externalSubtitles = serverSubtitles)
            val position = player.currentPosition
            val wasPlaying = player.playWhenReady
            val mediaItems =
                items.mapIndexed { index, playerItem ->
                    if (index == currentIndex) {
                        playerItem.toMediaItem(extraSubtitle = extraSubtitle)
                    } else {
                        playerItem.toMediaItem()
                    }
                }
            AppLog.tag("SubSideload").d(
                "setMediaItems 重建：服务器字幕=%d，本地侧载=%b，列表=%d，index=%d，position=%d",
                serverSubtitles.size,
                true,
                mediaItems.size,
                currentIndex,
                position,
            )
            // 紧挨着重建设置：网络请求期间不该屏蔽条目切换，而 setMediaItems 触发的
            // 切换与短暂位置清零必须屏蔽，否则会重复上报进度、重复插入上下集
            pendingSubtitleId = uri.toString()
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setPreferredTextLanguage(language)
                .build()
            isReloadingForSubtitle = true
            subtitleReloadItemId = item.itemId.toString()
            player.setMediaItems(mediaItems, currentIndex, position)
            player.prepare()
            player.playWhenReady = wasPlaying
            AppLog.tag("SubSideload").d(
                "setMediaItems 已返回：文本组=%d，state=%d，playlist=%d",
                player.currentTracks.groups.count { it.type == C.TRACK_TYPE_TEXT },
                player.playbackState,
                player.mediaItemCount,
            )
            // 兜底计时必须从重建发起后才开始：网络请求比它更慢时，提前复位标记
            // 会让随后触发的条目切换失去屏蔽
            viewModelScope.launch {
                delay(reloadFlagTimeoutMs)
                if (session == currentPlaybackSession && isReloadingForSubtitle) {
                    isReloadingForSubtitle = false
                    AppLog.w("侧载字幕重载超时未就绪，已恢复进度上报")
                }
            }
        }
        AppLog.tag("SubSideload").d("已排队重建当前条目：%s（%s）", label, language)
    }

    /** 侧载字幕的 MIME 类型：只收 srt / vtt（与字幕站的下载格式一致），其余按 srt 兜底。 */
    private fun subtitleMimeType(uri: Uri): String =
        when (uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase()) {
            "vtt" -> MimeTypes.TEXT_VTT
            else -> MimeTypes.APPLICATION_SUBRIP
        }

    @OptIn(DelicateCoroutinesApi::class)
    private fun releasePlayer() {
        val mediaId = player.currentMediaItem?.mediaId
        val position = player.currentPosition
        val duration = player.duration
        GlobalScope.launch {
            delay(200L)
            try {
                if (mediaId != null && duration != C.TIME_UNSET) {
                    AppLog.d("Sending playback stop")
                    repository.postPlaybackStop(
                        UUID.fromString(mediaId),
                        position.times(10000),
                        position.div(duration.toFloat()).times(100).toInt(),
                    )
                }
            } catch (e: Exception) {
                AppLog.e("播放器操作失败，错误类型：%s", e.javaClass.simpleName)
            }
        }

        _uiState.update { it.copy(currentTrickplay = null) }
        playWhenReady = false
        playbackPosition = 0L
        currentMediaItemIndex = 0
        player.removeListener(this)
        player.release()
    }

    fun updatePlaybackProgress() {
        if (isReloadingForSubtitle) return
        AppLog.d("Updating playback progress")
        viewModelScope.launch(Dispatchers.Main) {
            savedStateHandle["position"] = player.currentPosition
            if (player.currentMediaItem != null && player.currentMediaItem!!.mediaId.isNotEmpty()) {
                val itemId = UUID.fromString(player.currentMediaItem!!.mediaId)
                try {
                    repository.postPlaybackProgress(
                        itemId,
                        player.currentPosition.times(10000),
                        !player.isPlaying,
                    )
                } catch (e: Exception) {
                    AppLog.e("播放器操作失败，错误类型：%s", e.javaClass.simpleName)
                }
            }
        }
    }

    fun updateCurrentSegment() {
        AppLog.d("Updating current segment")
        viewModelScope.launch(Dispatchers.Main) {
            if (currentMediaItemSegments.isEmpty()) {
                return@launch
            }

            val milliSeconds = player.currentPosition

            // Get current segment, - 100 milliseconds to avoid showing button after segment ends
            val currentSegment = currentMediaItemSegments.find { segment ->
                milliSeconds in segment.startTicks..<(segment.endTicks - 100L)
            }

            if (currentSegment == null) {
                // Remove button if not pressed and there is no current segment
                if (_uiState.value.currentSegment != null) {
                    _uiState.update { it.copy(currentSegment = null) }
                }
                return@launch
            }

            AppLog.tag("SegmentInfo").d("currentSegment: %s", currentSegment)

            if (
                segmentsAutoSkip &&
                    segmentsAutoSkipTypes.contains(currentSegment.type.toString()) &&
                    (segmentsAutoSkipMode == Constants.PlayerMediaSegmentsAutoSkip.ALWAYS ||
                        (segmentsAutoSkipMode == Constants.PlayerMediaSegmentsAutoSkip.PIP &&
                            isInPictureInPictureMode))
            ) {
                // Auto Skip segment
                skipSegment(currentSegment)
            } else if (segmentsSkipButtonTypes.contains(currentSegment.type.toString())) {
                // Skip Button segment
                _uiState.update {
                    it.copy(
                        currentSegment = currentSegment,
                        currentSkipButtonStringRes = getSkipButtonTextStringId(currentSegment),
                    )
                }
            } else {
                _uiState.update { it.copy(currentSegment = null) }
            }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        val subtitleId = pendingSubtitleId ?: return
        for (group in tracks.groups) {
            if (group.type != C.TRACK_TYPE_TEXT || !group.isSupported) continue
            val index = (0 until group.length).firstOrNull { group.getTrackFormat(it).id == subtitleId }
                ?: continue
            pendingSubtitleId = null
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
                .build()
            return
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // 注入侧载字幕会用 setMediaItems 重建播放列表，进而触发一次条目切换。这里必须跳过：
        // 否则会重复 postPlaybackStart、重拉 segments / trickplay，并往播放列表里再插一遍上 / 下一集
        if (isReloadingForSubtitle &&
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
            mediaItem?.mediaId == subtitleReloadItemId) {
            AppLog.d("重载侧载字幕中，跳过条目切换处理：${mediaItem?.mediaId}")
            return
        }
        isReloadingForSubtitle = false
        subtitleReloadItemId = null
        currentPlaybackSession++
        subtitleReloadJob?.cancel()
        pendingSubtitleId = null
        AppLog.d("Playing MediaItem: ${mediaItem?.mediaId}")
        savedStateHandle["mediaItemIndex"] = player.currentMediaItemIndex
        viewModelScope.launch {
            try {
                items
                    .first { it.itemId.toString() == player.currentMediaItem?.mediaId }
                    .let { item ->
                        val itemTitle =
                            if (item.parentIndexNumber != null && item.indexNumber != null) {
                                if (item.indexNumberEnd == null) {
                                    "S${item.parentIndexNumber}:E${item.indexNumber} - ${item.name}"
                                } else {
                                    "S${item.parentIndexNumber}:E${item.indexNumber}-${item.indexNumberEnd} - ${item.name}"
                                }
                            } else {
                                item.name
                            }
                        _uiState.update {
                            it.copy(
                                currentItemTitle = itemTitle,
                                currentSegment = null,
                                currentChapters = item.chapters,
                                fileLoaded = false,
                            )
                        }

                        repository.postPlaybackStart(item.itemId)

                        if (segmentsSkipButton || segmentsAutoSkip) {
                            getSegments(item.itemId)
                        }

                        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
                            getTrickplay(item)
                        }

                        playlistManager.setCurrentMediaItemIndex(item.itemId)

                        val previousItem = playlistManager.getPreviousPlayerItem()
                        if (previousItem != null) {
                            items.add(player.currentMediaItemIndex, previousItem)
                            player.addMediaItem(
                                player.currentMediaItemIndex,
                                previousItem.toMediaItem(),
                            )
                        }

                        val nextItem = playlistManager.getNextPlayerItem()
                        if (nextItem != null) {
                            items.add(player.currentMediaItemIndex + 1, nextItem)
                            player.addMediaItem(
                                player.currentMediaItemIndex + 1,
                                nextItem.toMediaItem(),
                            )
                        }

                        AppLog.tag("PlayerItems").d(items.map { it.indexNumber }.toString())
                    }
            } catch (e: Exception) {
                AppLog.e("播放器操作失败，错误类型：%s", e.javaClass.simpleName)
            }
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // Report playback stopped for current item and transition to the next one
        if (
            !playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                player.playbackState == ExoPlayer.STATE_READY
        ) {
            viewModelScope.launch {
                val mediaId = player.currentMediaItem?.mediaId
                val position = player.currentPosition
                val duration = player.duration
                try {
                    repository.postPlaybackStop(
                        UUID.fromString(mediaId),
                        position.times(10000),
                        position.div(duration.toFloat()).times(100).toInt(),
                    )
                } catch (e: Exception) {
                    AppLog.e("播放器操作失败，错误类型：%s", e.javaClass.simpleName)
                }
                player.seekToNextMediaItem()
                player.play()
            }
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        var stateString = "UNKNOWN_STATE             -"
        when (state) {
            ExoPlayer.STATE_IDLE -> {
                stateString = "ExoPlayer.STATE_IDLE      -"
            }
            ExoPlayer.STATE_BUFFERING -> {
                stateString = "ExoPlayer.STATE_BUFFERING -"
            }
            ExoPlayer.STATE_READY -> {
                stateString = "ExoPlayer.STATE_READY     -"
                _uiState.update { it.copy(fileLoaded = true) }
                isReloadingForSubtitle = false
            }
            ExoPlayer.STATE_ENDED -> {
                stateString = "ExoPlayer.STATE_ENDED     -"
                eventsChannel.trySend(PlayerEvents.NavigateBack)
            }
        }
        AppLog.d("Changed player state to $stateString")
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)
        AppLog.e(
            "播放出错，错误代码：%s，错误类型：%s",
            error.errorCodeName,
            error.javaClass.simpleName,
        )
        // 重载失败时不会再到 STATE_READY，这里必须把标记清掉，否则进度上报会一直停着
        isReloadingForSubtitle = false
    }

    override fun onCleared() {
        super.onCleared()
        AppLog.d("Clearing Player ViewModel")
        releasePlayer()
    }

    fun switchToTrack(trackType: @C.TrackType Int, index: Int) {
        if (trackType == C.TRACK_TYPE_TEXT) {
            rememberSubtitleTrackChoice(index)
        }
        // Index -1 equals disable track
        if (index == -1) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(trackType)
                    .setTrackTypeDisabled(trackType, true)
                    .build()
        } else {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(
                        TrackSelectionOverride(
                            player.currentTracks.groups
                                .filter { it.type == trackType && it.isSupported }[index]
                                .mediaTrackGroup,
                            0,
                        )
                    )
                    .setTrackTypeDisabled(trackType, false)
                    .build()
        }
    }

    /**
     * 把用户手动选的字幕轨持久化，下次播放作为默认选择。
     *
     * 记语言代码而不是轨 index：index 在不同影片之间不稳定，语言才能跨条目匹配。
     * 轨没有语言信息（language 为 null）时无法跨条目识别，跳过记忆、保留上一次的值。
     */
    private fun rememberSubtitleTrackChoice(index: Int) {
        if (index == -1) {
            appPreferences.setValue(appPreferences.playerLastSubtitleTrack, Constants.SUBTITLE_TRACK_OFF)
            return
        }
        val language =
            player.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                .getOrNull(index)
                ?.mediaTrackGroup
                ?.getFormat(0)
                ?.language
        if (language == null) {
            AppLog.w("选中的字幕轨缺少语言信息，不记忆（index=%d）", index)
            return
        }
        appPreferences.setValue(appPreferences.playerLastSubtitleTrack, language)
    }

    fun selectSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        playbackSpeed = speed
    }

    /** 当前播放条目在服务器上的 id；尚未开始播放时为 null。 */
    val currentItemId: UUID?
        get() =
            player.currentMediaItem?.mediaId
                ?.takeIf { it.isNotEmpty() }
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    /** 判断条目是否已有横屏封面，截图前用它避免覆盖服务器上的现有封面。 */
    suspend fun hasBackdrop(itemId: UUID): Boolean =
        repository.getItemImages(itemId).any { it.imageType == ImageType.BACKDROP }

    /** 把播放画面截图上传为条目的横屏封面。 */
    suspend fun setBackdrop(itemId: UUID, data: ByteArray) {
        repository.setItemImage(itemId, ImageType.BACKDROP, data)
    }

    private suspend fun getSegments(itemId: UUID) {
        try {
            currentMediaItemSegments = repository.getSegments(itemId)
        } catch (e: Exception) {
            currentMediaItemSegments = emptyList()
            AppLog.e("播放器操作失败，错误类型：%s", e.javaClass.simpleName)
        }
    }

    private suspend fun getTrickplay(item: PlayerItem) {
        val trickplayInfo = item.trickplayInfo ?: return
        AppLog.d("Trickplay Resolution: ${trickplayInfo.width}")

        withContext(Dispatchers.Default) {
            val maxIndex =
                ceil(
                        trickplayInfo.thumbnailCount
                            .toDouble()
                            .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                    )
                    .toInt()
            val bitmaps = mutableListOf<Bitmap>()

            for (i in 0..maxIndex) {
                repository.getTrickplayData(item.itemId, trickplayInfo.width, i)?.let { byteArray ->
                    val fullBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                    for (offsetY in
                        0..<trickplayInfo.height * trickplayInfo.tileHeight step
                            trickplayInfo.height) {
                        for (offsetX in
                            0..<trickplayInfo.width * trickplayInfo.tileWidth step
                                trickplayInfo.width) {
                            val bitmap =
                                Bitmap.createBitmap(
                                    fullBitmap,
                                    offsetX,
                                    offsetY,
                                    trickplayInfo.width,
                                    trickplayInfo.height,
                                )
                            bitmaps.add(bitmap)
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(currentTrickplay = Trickplay(trickplayInfo.interval, bitmaps))
            }
        }
    }

    fun skipSegment(segment: FindroidSegment) {
        if (shouldSkipToNextEpisode(segment)) {
            player.seekToNextMediaItem()
        } else {
            player.seekTo(segment.endTicks)
        }
        _uiState.update { it.copy(currentSegment = null) }
    }

    // Check if the outro segment's end time is within n milliseconds of the player's total duration
    private fun shouldSkipToNextEpisode(segment: FindroidSegment): Boolean {
        return if (segment.type == FindroidSegmentType.OUTRO && player.hasNextMediaItem()) {
            val segmentEndTimeMillis = segment.endTicks
            val playerDurationMillis = player.duration
            val thresholdMillis =
                playerDurationMillis -
                    appPreferences.getValue(appPreferences.playerMediaSegmentsNextEpisodeThreshold)

            segmentEndTimeMillis > thresholdMillis
        } else {
            false
        }
    }

    private fun getSkipButtonTextStringId(segment: FindroidSegment): Int {
        return when (shouldSkipToNextEpisode(segment)) {
            true -> R.string.player_controls_next_episode
            false ->
                when (segment.type) {
                    FindroidSegmentType.INTRO -> R.string.player_controls_skip_intro
                    FindroidSegmentType.OUTRO -> R.string.player_controls_skip_outro
                    FindroidSegmentType.RECAP -> R.string.player_controls_skip_recap
                    FindroidSegmentType.COMMERCIAL -> R.string.player_controls_skip_commercial
                    FindroidSegmentType.PREVIEW -> R.string.player_controls_skip_preview
                    else -> R.string.player_controls_skip_unknown
                }
        }
    }

    /**
     * Get chapters of current item
     *
     * @return list of [PlayerChapter]
     */
    private fun getChapters(): List<PlayerChapter> {
        return uiState.value.currentChapters
    }

    /**
     * Get the index of the current chapter
     *
     * @return the index of the current chapter
     */
    private fun getCurrentChapterIndex(): Int? {
        val chapters = getChapters()

        for (i in chapters.indices.reversed()) {
            if (chapters[i].startPosition < player.currentPosition) {
                return i
            }
        }

        return null
    }

    /**
     * Get the index of the next chapter
     *
     * @return the index of the next chapter
     */
    private fun getNextChapterIndex(): Int? {
        val chapters = getChapters()
        val currentChapterIndex = getCurrentChapterIndex() ?: return null

        return minOf(chapters.size - 1, currentChapterIndex + 1)
    }

    /**
     * Get the index of the previous chapter. Only use this for seeking as it will return the
     * current chapter when player position is more than 5 seconds past the start of the chapter
     *
     * @return the index of the previous chapter
     */
    private fun getPreviousChapterIndex(): Int? {
        val chapters = getChapters()
        val currentChapterIndex = getCurrentChapterIndex() ?: return null

        // Return current chapter when more than 5 seconds past chapter start
        if (player.currentPosition > chapters[currentChapterIndex].startPosition + 5000L) {
            return currentChapterIndex
        }

        return maxOf(0, currentChapterIndex - 1)
    }

    fun isLastChapter(): Boolean =
        getChapters().let { chapters -> getCurrentChapterIndex() == chapters.size - 1 }

    /**
     * Seek to chapter
     *
     * @param [chapterIndex] the index of the chapter to seek to
     * @return the [PlayerChapter] which has been sought to
     */
    private fun seekToChapter(chapterIndex: Int): PlayerChapter? {
        return getChapters().getOrNull(chapterIndex)?.also { chapter ->
            player.seekTo(chapter.startPosition)
        }
    }

    /**
     * Seek to the next chapter
     *
     * @return the [PlayerChapter] which has been sought to
     */
    fun seekToNextChapter(): PlayerChapter? {
        return getNextChapterIndex()?.let { seekToChapter(it) }
    }

    /**
     * Seek to the previous chapter Will seek to start of current chapter if player position is more
     * than 5 seconds past start of chapter
     *
     * @return the [PlayerChapter] which has been sought to
     */
    fun seekToPreviousChapter(): PlayerChapter? {
        return getPreviousChapterIndex()?.let { seekToChapter(it) }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        eventsChannel.trySend(PlayerEvents.IsPlayingChanged(isPlaying))
    }
}

sealed interface PlayerEvents {
    data object NavigateBack : PlayerEvents

    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvents
}
