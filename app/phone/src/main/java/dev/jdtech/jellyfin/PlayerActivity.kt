package dev.jdtech.jellyfin

import android.app.AppOpsManager
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Rational
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import dagger.hilt.android.AndroidEntryPoint
import dev.jdtech.jellyfin.core.subtitle.SubtitleCompareCache
import dev.jdtech.jellyfin.core.subtitle.SubtitleDownloadController
import dev.jdtech.jellyfin.databinding.ActivityPlayerBinding
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.player.local.R as PlayerR
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.player.PlayerMenuDialogFragment
import dev.jdtech.jellyfin.presentation.player.subtitle.PlayerSubtitleDownloadDialogHost
import dev.jdtech.jellyfin.presentation.player.subtitle.SubtitleAdjustManager
import dev.jdtech.jellyfin.presentation.player.subtitle.SubtitleAdjustOverlay
import dev.jdtech.jellyfin.presentation.player.subtitle.SubtitleCompareOverlay
import dev.jdtech.jellyfin.presentation.player.subtitle.SubtitlePlaybackCoordinator
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.PlayerGestureHelper
import dev.jdtech.jellyfin.utils.PreviewScrubListener
import dev.jdtech.jellyfin.utils.captureFrameAsJpeg
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

var isControlsLocked: Boolean = false

@AndroidEntryPoint
class PlayerActivity : BasePlayerActivity(), PlayerMenuDialogFragment.Host {

    companion object {
        /** 指定播放条目下的哪个来源（多版本时由用户选择），未携带该 extra 时由播放器按默认规则挑选。 */
        const val EXTRA_MEDIA_SOURCE_INDEX = "mediaSourceIndex"

        /** 下载详情的本地数据来源随播放入口传递，不改变全局离线设置。 */
        const val EXTRA_USE_LOCAL_DATA = "useLocalData"

        /**
         * 开播后直接打开字幕对照面板（候选已由详情页落盘）。
         *
         * 同一部影片已在播放时只开面板、不重开视频，否则播放进度会被重置。
         */
        const val EXTRA_OPEN_SUBTITLE_COMPARE = "openSubtitleCompare"
    }

    @Inject lateinit var appPreferences: AppPreferences

    /** 播放页自己的一份字幕下载状态机（**不是单例**，与详情页互不干扰）。 */
    @Inject lateinit var subtitleController: SubtitleDownloadController
    @Inject lateinit var subtitleCompareCache: SubtitleCompareCache

    lateinit var binding: ActivityPlayerBinding
    private lateinit var subtitleAdjustManager: SubtitleAdjustManager

    /** 「下载字幕 + 字幕对照」的编排：实现细节都在这个类里，本 Activity 只做装配与生命周期接线。 */
    private lateinit var subtitleCoordinator: SubtitlePlaybackCoordinator

    private var playerGestureHelper: PlayerGestureHelper? = null
    override val viewModel: PlayerViewModel by viewModels()
    private var previewScrubListener: PreviewScrubListener? = null
    private var wasZoom: Boolean = false
    private var skipButtonTimeoutExpired: Boolean = true

    private lateinit var skipSegmentButton: Button

    private val isPipSupported by lazy {
        // Check if device has PiP feature
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            return@lazy false
        }

        // Check if PiP is enabled for the app
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager?
        appOps?.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            Process.myUid(),
            packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }

    private val handler = Handler(Looper.getMainLooper())
    private val skipButtonTimeout = Runnable {
        if (!binding.playerView.isControllerFullyVisible) {
            skipSegmentButton.isVisible = false
            skipButtonTimeoutExpired = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")
        // 未携带来源索引（-1）时置为 null，交给播放器按默认规则选源
        val mediaSourceIndex =
            intent.extras!!.getInt(EXTRA_MEDIA_SOURCE_INDEX, -1).takeIf { index -> index >= 0 }
        val openSubtitleCompare = consumeOpenSubtitleCompareExtra()

        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.playerView.player = viewModel.player

        // 字幕外观与位置的统一入口：加载已保存设置、计算默认位置、提供调节模式
        subtitleAdjustManager = SubtitleAdjustManager(binding.playerView, viewModel.player, appPreferences)
        subtitleAdjustManager.attach()
        binding.subtitleAdjustOverlay.setContent {
            FindroidTheme {
                SubtitleAdjustOverlay(subtitleAdjustManager)
            }
        }

        subtitleCoordinator =
            SubtitlePlaybackCoordinator(
                context = this,
                scope = lifecycleScope,
                player = viewModel.player,
                appPreferences = appPreferences,
                repositoryProvider = { viewModel.repository },
                isUsingLocalData = { viewModel.isUsingLocalData },
                subtitleController = subtitleController,
                compareCache = subtitleCompareCache,
                subtitleAdjustManager = subtitleAdjustManager,
                hidePlayerController = { binding.playerView.hideController() },
                currentPlayerItemId = { viewModel.currentItemId },
                currentPlaybackSession = { viewModel.currentPlaybackSession },
                reloadWithExternalSubtitle = { uri, label, language ->
                    viewModel.reloadCurrentItemWithExternalSubtitle(uri, label, language)
                },
            )
        binding.subtitleCompareOverlay.setContent {
            FindroidTheme {
                SubtitleCompareOverlay(
                    manager = subtitleCoordinator.compareManager,
                    onApply = subtitleCoordinator::confirmApplyCompare,
                    onClose = { subtitleCoordinator.closeComparePanel() },
                )
            }
        }
        // 与另外两个覆盖层一致套 FindroidTheme：否则弹窗会用 M3 默认配色（浅色），
        // 与详情页里同一个弹窗的外观不一致
        binding.subtitleDialogOverlay.setContent {
            FindroidTheme { PlayerSubtitleDownloadDialogHost(subtitleCoordinator) }
        }
        binding.playerView.setControllerVisibilityListener(
            PlayerView.ControllerVisibilityListener { visibility ->
                if (visibility == View.GONE) {
                    hideSystemUI()
                }
            }
        )

        val playerControls = binding.playerView.findViewById<View>(R.id.player_controls)
        val lockedControls = binding.playerView.findViewById<View>(R.id.locked_player_view)

        isControlsLocked = false

        configureInsets(playerControls)
        configureInsets(lockedControls)

        if (appPreferences.getValue(appPreferences.playerGestures)) {
            playerGestureHelper =
                PlayerGestureHelper(
                    appPreferences,
                    this,
                    binding.playerView,
                    getSystemService(AUDIO_SERVICE) as AudioManager,
                )
        }

        binding.playerView.findViewById<View>(R.id.back_button).setOnClickListener {
            finishPlayback()
        }

        // 系统返回键优先级：对照面板 > 字幕调节模式 > 退出播放器；
        // 调节模式关闭等同「关闭」=取消
        onBackPressedDispatcher.addCallback(this) {
            when {
                subtitleCoordinator.isCompareShowing -> subtitleCoordinator.closeComparePanel()
                subtitleAdjustManager.state.value.isAdjustMode -> subtitleAdjustManager.close()
                else -> finishPlayback()
            }
        }

        val videoNameTextView = binding.playerView.findViewById<TextView>(R.id.video_name)

        val menuButton = binding.playerView.findViewById<ImageButton>(R.id.btn_menu)
        skipSegmentButton = binding.playerView.findViewById(R.id.btn_skip_segment)
        val pipButton = binding.playerView.findViewById<ImageButton>(R.id.btn_pip)
        val lockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_lockview)
        val unlockButton = binding.playerView.findViewById<ImageButton>(R.id.btn_unlock)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { uiState ->
                        AppLog.d("$uiState")
                        uiState.apply {
                            // Title
                            videoNameTextView.text = currentItemTitle

                            // Media segment
                            currentSegment?.let { segment ->
                                // Skip Button - text
                                skipSegmentButton.text = getString(currentSkipButtonStringRes)
                                // Skip Button - visibility
                                skipSegmentButton.isVisible = !isInPictureInPictureMode
                                if (skipSegmentButton.isVisible) {
                                    skipButtonTimeoutExpired = false
                                    handler.removeCallbacks(skipButtonTimeout)
                                    handler.postDelayed(
                                        skipButtonTimeout,
                                        viewModel.segmentsSkipButtonDuration * 1000,
                                    )
                                }
                                // Skip Button - onClick
                                skipSegmentButton.setOnClickListener {
                                    viewModel.skipSegment(segment)
                                    skipSegmentButton.isVisible = false
                                }
                            } ?: run { skipSegmentButton.isVisible = false }

                            binding.playerView.setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility ->
                                    if (skipButtonTimeoutExpired && currentSegment != null) {
                                        skipSegmentButton.visibility = visibility
                                    }
                                }
                            )

                            // Trickplay
                            previewScrubListener?.let { it.currentTrickplay = currentTrickplay }

                            playerGestureHelper?.let { it.currentTrickplay = currentTrickplay }

                            // Chapters
                            val playerControlView =
                                findViewById<PlayerControlView>(R.id.exo_controller)
                            if (currentChapters.isNotEmpty()) {
                                val numOfChapters = currentChapters.size
                                playerControlView.setExtraAdGroupMarkers(
                                    LongArray(numOfChapters) { index ->
                                        currentChapters[index].startPosition
                                    },
                                    BooleanArray(numOfChapters) { false },
                                )
                            } else {
                                playerControlView.setExtraAdGroupMarkers(null, null)
                            }

                            // File Loaded
                            if (fileLoaded) {
                                menuButton.isEnabled = true
                                menuButton.imageAlpha = 255
                                lockButton.isEnabled = true
                                lockButton.imageAlpha = 255
                                pipButton.isEnabled = true
                                pipButton.imageAlpha = 255
                            }
                        }
                    }
                }

                launch {
                    // 字幕调节模式覆盖层的显示 / 隐藏
                    subtitleAdjustManager.state.collect { state ->
                        binding.subtitleAdjustOverlay.isVisible = state.isAdjustMode
                    }
                }

                launch {
                    // 对照面板跟随播放：发现播放器内部切集 + 每 150ms 重算各行 cue。
                    // 放在 repeatOnLifecycle(STARTED) 里，进后台即停，不白烧电
                    subtitleCoordinator.playbackLoop()
                }

                launch {
                    subtitleCoordinator.observeSubtitleEvents()
                }

                launch {
                    subtitleCoordinator.observeCompareUpdates()
                }

                launch {
                    viewModel.eventsChannelFlow.collect { event ->
                        when (event) {
                            is PlayerEvents.NavigateBack -> finishPlayback()
                            is PlayerEvents.IsPlayingChanged -> {
                                if (event.isPlaying) {
                                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    window.clearFlags(
                                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                                    )
                                }

                                if (appPreferences.getValue(appPreferences.playerPipGesture)) {
                                    try {
                                        setPictureInPictureParams(pipParams(event.isPlaying))
                                    } catch (_: IllegalArgumentException) {}
                                }
                            }
                        }
                    }
                }

                launch {
                    while (true) {
                        viewModel.updatePlaybackProgress()
                        delay(5000L)
                    }
                }

                if (
                    appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton) ||
                        appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)
                ) {
                    launch {
                        while (true) {
                            viewModel.updateCurrentSegment()
                            delay(1000L)
                        }
                    }
                }
            }
        }

        menuButton.isEnabled = false
        menuButton.imageAlpha = 75

        lockButton.isEnabled = false
        lockButton.imageAlpha = 75

        if (isPipSupported) {
            pipButton.isEnabled = false
            pipButton.imageAlpha = 75
        } else {
            val pipSpace = binding.playerView.findViewById<Space>(R.id.space_pip)
            pipButton.isVisible = false
            pipSpace.isVisible = false
        }

        menuButton.setOnClickListener {
            PlayerMenuDialogFragment().show(supportFragmentManager, "playermenudialog")
        }

        val exoPlayerControlView = findViewById<FrameLayout>(R.id.player_controls)
        val lockedLayout = findViewById<FrameLayout>(R.id.locked_player_view)

        lockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.GONE
            lockedLayout.visibility = View.VISIBLE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            isControlsLocked = true
        }

        unlockButton.setOnClickListener {
            exoPlayerControlView.visibility = View.VISIBLE
            lockedLayout.visibility = View.GONE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            isControlsLocked = false
        }

        pipButton.setOnClickListener { pictureInPicture() }

        // Set marker color
        val timeBar = binding.playerView.findViewById<DefaultTimeBar>(R.id.exo_progress)
        timeBar.setAdMarkerColor(Color.WHITE)

        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
            val imagePreview = binding.playerView.findViewById<ImageView>(R.id.image_preview)
            previewScrubListener = PreviewScrubListener(imagePreview, timeBar, viewModel.player)

            timeBar.addListener(previewScrubListener!!)
        }

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
            mediaSourceIndex = mediaSourceIndex,
            useLocalData = intent.getBooleanExtra(EXTRA_USE_LOCAL_DATA, false),
        )
        hideSystemUI()

        // 进播放页就把字幕关键词识别好（等用户打开「下载字幕」时输入框里已有番号）；
        // 详情页点了「播放视频时对比」则直接把对照面板铺出来（不上传任何字幕）
        subtitleCoordinator.onPlaybackStarted(itemId, openSubtitleCompare)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        val itemId = UUID.fromString(intent.extras!!.getString("itemId"))
        val itemKind = intent.extras!!.getString("itemKind")
        val startFromBeginning = intent.extras!!.getBoolean("startFromBeginning")
        val mediaSourceIndex =
            intent.extras!!.getInt(EXTRA_MEDIA_SOURCE_INDEX, -1).takeIf { index -> index >= 0 }
        val openSubtitleCompare = consumeOpenSubtitleCompareExtra()
        val useLocalData = intent.getBooleanExtra(EXTRA_USE_LOCAL_DATA, false)
        val isUsingLocalData = useLocalData || appPreferences.getValue(appPreferences.offlineMode)

        // 同一部影片只开对照面板、不重开视频：否则播放进度会被重置
        if (
            openSubtitleCompare && itemId == viewModel.currentItemId &&
                isUsingLocalData == viewModel.isUsingLocalData
        ) {
            subtitleCoordinator.openComparePanel(itemId)
            return
        }

        viewModel.initializePlayer(
            itemId = itemId,
            itemKind = itemKind ?: "",
            startFromBeginning = startFromBeginning,
            mediaSourceIndex = mediaSourceIndex,
            useLocalData = useLocalData,
        )
        // 换了一部影片：字幕目标条目与关键词跟着换，「播放视频时对比」则直接铺面板
        subtitleCoordinator.onPlaybackStarted(itemId, openSubtitleCompare)
    }

    /** 读一次并清掉「打开对照面板」标记：旋转屏幕 / 重入时不该再开一次。 */
    private fun consumeOpenSubtitleCompareExtra(): Boolean {
        val extras = intent.extras ?: return false
        val requested = extras.getBoolean(EXTRA_OPEN_SUBTITLE_COMPARE)
        if (requested) {
            intent.removeExtra(EXTRA_OPEN_SUBTITLE_COMPARE)
        }
        return requested
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
                appPreferences.getValue(appPreferences.playerPipGesture) &&
                viewModel.player.isPlaying &&
                !isControlsLocked
        ) {
            pictureInPicture()
        }
    }

    override fun onDestroy() {
        // 播放器即将销毁，摘掉字幕管理器对 Player 的监听，避免泄漏
        if (::subtitleAdjustManager.isInitialized) {
            subtitleAdjustManager.detach()
        }
        // 字幕下载状态与对照快照的收尾（清内存状态 + 后台删快照）都在编排类里
        if (this::subtitleCoordinator.isInitialized) {
            subtitleCoordinator.release()
        }
        super.onDestroy()
    }

    /**
     * 菜单里的「截图设为横屏封面」：先确认服务器上还没有横屏封面，再截取当前画面上传。
     *
     * 已有封面时不覆盖服务器数据，只提示用户先去删除，避免把服务器上已挑好的封面冲掉。
     */
    override val canModifyServer: Boolean
        get() = !viewModel.isUsingLocalData

    override fun adjustSubtitles() {
        binding.playerView.hideController()
        subtitleAdjustManager.enterAdjustMode()
    }

    override fun downloadSubtitles() {
        subtitleCoordinator.showDownloadDialog()
    }

    override fun captureBackdrop() {
        val itemId = viewModel.currentItemId
        val session = viewModel.currentPlaybackSession
        val surfaceView = binding.playerView.videoSurfaceView as? SurfaceView
        if (itemId == null || surfaceView == null) {
            Toast.makeText(this, PlayerR.string.player_backdrop_frame_unavailable, Toast.LENGTH_SHORT)
                .show()
            return
        }

        lifecycleScope.launch {
            val hasBackdrop =
                try {
                    viewModel.hasBackdrop(itemId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLog.e("检查横屏封面失败，错误类型：%s", e.javaClass.simpleName)
                    Toast.makeText(
                            this@PlayerActivity,
                            PlayerR.string.player_backdrop_set_failed,
                            Toast.LENGTH_SHORT,
                        )
                        .show()
                    return@launch
                }

            if (!isCaptureSessionCurrent(itemId, session)) return@launch
            if (hasBackdrop) {
                Toast.makeText(
                        this@PlayerActivity,
                        PlayerR.string.player_backdrop_already_exists,
                        Toast.LENGTH_LONG,
                    )
                    .show()
                return@launch
            }

            val imageBytes = try {
                surfaceView.captureFrameAsJpeg(displayAspectRatio())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.e(e, "截图编码失败")
                null
            }
            if (!isCaptureSessionCurrent(itemId, session)) return@launch
            if (imageBytes == null) {
                Toast.makeText(
                    this@PlayerActivity,
                    PlayerR.string.player_backdrop_frame_unavailable,
                    Toast.LENGTH_SHORT,
                ).show()
                return@launch
            }
            uploadBackdrop(itemId, imageBytes)
        }
    }

    private fun isCaptureSessionCurrent(itemId: UUID, session: Long): Boolean {
        if (viewModel.currentItemId == itemId && viewModel.currentPlaybackSession == session) return true
        Toast.makeText(this, PlayerR.string.player_backdrop_frame_unavailable, Toast.LENGTH_SHORT).show()
        return false
    }

    /** 把截图字节上传为横屏封面，并提示结果。 */
    private fun uploadBackdrop(itemId: UUID, imageBytes: ByteArray) {
        lifecycleScope.launch {
            try {
                viewModel.setBackdrop(itemId, imageBytes)
                Toast.makeText(
                        this@PlayerActivity,
                        PlayerR.string.player_backdrop_set_success,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            } catch (e: CancellationException) {
                // Activity 销毁会取消本协程，不是上传失败，直接放行
                throw e
            } catch (e: Exception) {
                AppLog.e("设置横屏封面失败，错误类型：%s", e.javaClass.simpleName)
                Toast.makeText(
                        this@PlayerActivity,
                        PlayerR.string.player_backdrop_set_failed,
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }
        }
    }

    /** 视频的显示宽高比（已折算 90/270 度旋转），用于裁掉 fit 缩放留下的黑边；尺寸未知时返回 null。 */
    private fun displayAspectRatio(): Float? {
        val videoSize = viewModel.player.videoSize
        if (videoSize.width <= 0 || videoSize.height <= 0) {
            return null
        }

        val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        if (ratio <= 0f) {
            return null
        }
        val isRotated =
            videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270
        return if (isRotated) 1f / ratio else ratio
    }

    private fun finishPlayback() {
        try {
            viewModel.player.clearVideoSurfaceView(
                binding.playerView.videoSurfaceView as SurfaceView
            )
        } catch (e: Exception) {
            AppLog.e(e)
        }
        handler.removeCallbacks(skipButtonTimeout)
        finish()
    }

    private fun pipParams(
        enableAutoEnter: Boolean = viewModel.player.isPlaying
    ): PictureInPictureParams {
        val displayAspectRatio = Rational(binding.playerView.width, binding.playerView.height)

        val aspectRatio =
            binding.playerView.player?.videoSize?.let {
                Rational(
                    it.width.coerceAtMost((it.height * 2.39f).toInt()),
                    it.height.coerceAtMost((it.width * 2.39f).toInt()),
                )
            }

        val sourceRectHint =
            if (displayAspectRatio < aspectRatio!!) {
                val space =
                    ((binding.playerView.height -
                            (binding.playerView.width.toFloat() / aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    0,
                    space,
                    binding.playerView.width,
                    (binding.playerView.width.toFloat() / aspectRatio.toFloat()).toInt() + space,
                )
            } else {
                val space =
                    ((binding.playerView.width -
                            (binding.playerView.height.toFloat() * aspectRatio.toFloat())) / 2)
                        .toInt()
                Rect(
                    space,
                    0,
                    (binding.playerView.height.toFloat() * aspectRatio.toFloat()).toInt() + space,
                    binding.playerView.height,
                )
            }

        val builder =
            PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .setSourceRectHint(sourceRectHint)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(enableAutoEnter)
        }

        return builder.build()
    }

    private fun pictureInPicture() {
        if (!isPipSupported) {
            return
        }

        try {
            enterPictureInPictureMode(pipParams())
        } catch (_: IllegalArgumentException) {}
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModel.isInPictureInPictureMode = isInPictureInPictureMode
        when (isInPictureInPictureMode) {
            true -> {
                binding.playerView.useController = false
                skipSegmentButton.isVisible = false

                wasZoom = playerGestureHelper?.isZoomEnabled == true
                playerGestureHelper?.updateZoomMode(false)

                // 对照面板是 Compose 覆盖层，会跟着进小窗：整个隐藏并恢复真实字幕轨，
                // 小窗里仍有字幕可看。缓存保留，退出小窗后可以重新打开
                subtitleCoordinator.onEnterPictureInPicture()

                // Brightness mode Auto
                window.attributes =
                    window.attributes.apply {
                        screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    }
            }

            false -> {
                binding.playerView.useController = true
                playerGestureHelper?.updateZoomMode(wasZoom)

                // 画中画期间交给系统自动亮度，退出后重新应用记住的亮度
                playerGestureHelper?.restoreSavedBrightness()

                subtitleCoordinator.onExitPictureInPicture()
            }
        }
    }
}
