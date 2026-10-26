package dev.jdtech.jellyfin.settings.domain

import android.content.SharedPreferences
import dev.jdtech.jellyfin.logging.AppLog
import dev.jdtech.jellyfin.settings.domain.models.Preference
import dev.jdtech.jellyfin.settings.domain.models.TranslateSettings
import javax.inject.Inject

class AppPreferences @Inject constructor(val sharedPreferences: SharedPreferences) {
    // Server
    val currentServer = Preference<String?>("pref_current_server", null)

    // Language
    val preferredAudioLanguage = Preference<String?>("pref_audio_language", null)
    val preferredSubtitleLanguage = Preference<String?>("pref_subtitle_language", null)

    // Interface
    val theme = Preference("pref_theme", "system")
    val dynamicColors = Preference("pref_dynamic_colors", true)
    val homeSuggestions = Preference<Boolean>("home_suggestions", true)
    val homeContinueWatching = Preference<Boolean>("home_continue_watching", true)
    val homeNextUp = Preference<Boolean>("home_next_up", true)
    val homeLatest = Preference<Boolean>("home_latest", true)
    val displayExtraInfo = Preference("pref_display_extra_info", false)

    // Player
    val playerBackend = Preference("pref_player_backend", "exoplayer")
    val playerBrightness = Preference("pref_player_brightness", -1.0f)

    // Player - mpv
    val playerMpv = Preference("pref_player_mpv", false)
    val playerMpvHwdec = Preference("pref_player_mpv_hwdec", "mediacodec")
    val playerMpvVo = Preference("pref_player_mpv_vo", "gpu-next")
    val playerMpvAo = Preference("pref_player_mpv_ao", "aaudio")

    // Player - gestures
    val playerGestures = Preference("pref_player_gestures", true)
    val playerGesturesVB = Preference("pref_player_gestures_vb", true)
    val playerGesturesZoom = Preference("pref_player_gestures_zoom", true)
    val playerGesturesSeek = Preference("pref_player_gestures_seek", true)
    val playerGesturesSeekTrickplay = Preference("pref_player_gestures_seek_trickplay", true)
    val playerGesturesChapterSkip = Preference("pref_player_gestures_chapter_skip", true)
    val playerGesturesBrightnessRemember = Preference("pref_player_brightness_remember", true)
    val playerGesturesStartMaximized = Preference("pref_player_start_maximized", false)

    // Player - seeking
    val playerSeekBackInc = Preference("pref_player_seek_back_inc", 5_000L)
    val playerSeekForwardInc = Preference("pref_player_seek_forward_inc", 15_000L)
    val playerChapterMarkers = Preference("pref_player_chapter_markers", true)

    // Player - Media Segments
    val playerMediaSegmentsSkipButton
        get() = Preference("pref_player_media_segments_skip_button", true)

    val playerMediaSegmentsSkipButtonType
        get() = Preference("pref_player_media_segments_skip_button_type", setOf("INTRO", "OUTRO"))

    val playerMediaSegmentsSkipButtonDuration
        get() = Preference("pref_player_media_segments_skip_button_duration", 5L)

    val playerMediaSegmentsAutoSkip
        get() = Preference("pref_player_media_segments_auto_skip", false)

    val playerMediaSegmentsAutoSkipMode
        get() =
            Preference(
                "pref_player_media_segments_auto_skip_mode",
                Constants.PlayerMediaSegmentsAutoSkip.ALWAYS,
            )

    val playerMediaSegmentsAutoSkipType
        get() = Preference("pref_player_media_segments_auto_skip_type", setOf("INTRO", "OUTRO"))

    val playerMediaSegmentsNextEpisodeThreshold
        get() = Preference("pref_player_media_segments_next_episode_threshold", 5_000L)

    // Player - trickplay
    val playerTrickplay = Preference("pref_player_trickplay", true)

    // Player - PiP
    val playerPipGesture = Preference("pref_player_picture_in_picture_gesture", false)

    // Downloads
    val downloadOverMobileData = Preference("pref_downloads_mobile_data", false)
    val downloadWhenRoaming = Preference("pref_downloads_roaming", false)

    // Network
    val requestTimeout =
        Preference("pref_network_request_timeout", Constants.NETWORK_DEFAULT_REQUEST_TIMEOUT)
    val connectTimeout =
        Preference("pref_network_connect_timeout", Constants.NETWORK_DEFAULT_CONNECT_TIMEOUT)
    val socketTimeout =
        Preference("pref_network_socket_timeout", Constants.NETWORK_DEFAULT_SOCKET_TIMEOUT)

    // Network - nfo 抓取专用代理（只作用于抓取，不影响 Jellyfin 接口与下载），留空表示直连
    val scrapeProxy = Preference("pref_scrape_proxy", "")

    // Network - 外网评论（影片详情页底部的短评，数据来自 JavDB 的短评页）
    /** 是否在详情页加载外网短评，关闭后详情页不出现评论区。 */
    val commentsEnabled = Preference("pref_comments_enabled", true)

    /** 短评请求是否复用 [scrapeProxy]；关掉后即使填了代理也直连。 */
    val commentsUseProxy = Preference("pref_comments_use_proxy", true)

    /** 短评缓存文件总大小上限（MB），超出后按最后写入时间淘汰最旧的文件。 */
    val commentCacheSize =
        Preference("pref_comment_cache_size", Constants.COMMENT_CACHE_DEFAULT_SIZE_MB)

    /** 短评缓存有效期（天），未过期时重复打开影片不再请求站点。 */
    val commentCacheDays =
        Preference("pref_comment_cache_days", Constants.COMMENT_CACHE_DEFAULT_DAYS)

    /** 站点登录时用过的账号名（只用于回填输入框，密码不落盘）。 */
    val javdbAccount = Preference("pref_javdb_account", "")

    /** 已登录的账号名，空串表示未登录；退出登录时清空。 */
    val javdbLoggedInUser = Preference("pref_javdb_logged_in_user", "")

    /** 登录会话是否为 VIP（登录时从 profile 页的「用戶類型」解析），用于账号行显示徽标。 */
    val javdbVip = Preference("pref_javdb_vip", false)

    /**
     * 抓取会话 Cookie 的持久化副本（每行 `host\tSet-Cookie 行`）。
     *
     * OkHttp 默认只在内存里存 Cookie，进程重启就退出登录，因此把抓取用到的 Cookie 落盘，
     * 下次启动首个请求前恢复。只存抓取站点自己的 Cookie，不含 Jellyfin 的凭据。
     */
    val scraperCookies = Preference("pref_scraper_cookies", "")

    /**
     * nfo 抓取站点的自定义网址：按站点 key（见 `:core` 的 `ScraperSite.key`）在「更多」菜单里
     * 运行时配置，优先于打包时的 BuildConfig 本地配置（仅 debug 包带入真实值）；留空表示用打包配置。
     */
    fun scrapeSiteUrl(siteKey: String) = Preference("pref_scrape_site_url_$siteKey", "")

    // nfo 翻译（OpenAI 兼容接口）：只在「编辑 nfo」弹窗里配置，作用范围同抓取代理
    val translateUrl = Preference("pref_translate_url", Constants.TRANSLATE_DEFAULT_URL)
    val translateApiKey = Preference("pref_translate_api_key", "")
    val translateModel = Preference("pref_translate_model", Constants.TRANSLATE_DEFAULT_MODEL)
    val translateTitle = Preference("pref_translate_title", true)
    val translatePlot = Preference("pref_translate_plot", true)
    val translateGenresTags = Preference("pref_translate_genres_tags", true)
    val translateAuto = Preference("pref_translate_auto", true)

    /** 分类 / 标签的译文缓存（json：原值 → 译文），跨会话复用，避免同一个词反复请求大模型。 */
    val tagTranslateCache = Preference("pref_tag_translate_cache", "")

    // Cache
    val imageCache = Preference("pref_image_cache", true)
    val imageCacheSize = Preference("pref_image_cache_size", 900)

    // Sorting
    val sortBy = Preference("pref_sort_by", "SortName")
    val sortOrder = Preference("pref_sort_order", "Ascending")
    val libraryCoverMode = Preference("pref_library_cover_mode", "PORTRAIT")

    // Offline mode
    val offlineMode = Preference("pref_offline_mode", false)

    // Migrations
    val mpvMigrated = Preference("mpv_migrated", false)

    /** 一次性读出整套翻译设置，供状态初始化与界面回填。 */
    fun getTranslateSettings(): TranslateSettings =
        TranslateSettings(
            url = getValue(translateUrl),
            apiKey = getValue(translateApiKey),
            model = getValue(translateModel),
            translateTitle = getValue(translateTitle),
            translatePlot = getValue(translatePlot),
            translateGenresTags = getValue(translateGenresTags),
            autoTranslate = getValue(translateAuto),
        )

    /** 整套写回翻译设置，弹窗确认时调用一次。 */
    fun setTranslateSettings(settings: TranslateSettings) {
        setValue(translateUrl, settings.url)
        setValue(translateApiKey, settings.apiKey)
        setValue(translateModel, settings.model)
        setValue(translateTitle, settings.translateTitle)
        setValue(translatePlot, settings.translatePlot)
        setValue(translateGenresTags, settings.translateGenresTags)
        setValue(translateAuto, settings.autoTranslate)
    }

    inline fun <reified T> getValue(preference: Preference<T>): T {
        return try {
            @Suppress("UNCHECKED_CAST")
            when (preference.defaultValue) {
                is Boolean ->
                    sharedPreferences.getBoolean(preference.backendName, preference.defaultValue)
                        as T
                is Int ->
                    sharedPreferences.getInt(preference.backendName, preference.defaultValue) as T
                is Long ->
                    sharedPreferences.getLong(preference.backendName, preference.defaultValue) as T
                is Float ->
                    sharedPreferences.getFloat(preference.backendName, preference.defaultValue) as T
                is String? ->
                    sharedPreferences.getString(preference.backendName, preference.defaultValue)
                        as T
                is Set<*> ->
                    sharedPreferences.getStringSet(
                        preference.backendName,
                        preference.defaultValue as Set<String>,
                    ) as T
                else -> preference.defaultValue
            }
        } catch (_: Exception) {
            AppLog.w(
                "Failed to load %s preference. Resetting to default value...",
                preference.backendName,
            )
            setValue(preference, preference.defaultValue)
            preference.defaultValue
        }
    }

    inline fun <reified T> setValue(preference: Preference<T>, value: T) {
        val editor = sharedPreferences.edit()
        @Suppress("UNCHECKED_CAST")
        when (preference.defaultValue) {
            is Boolean -> editor.putBoolean(preference.backendName, value as Boolean)
            is Int -> editor.putInt(preference.backendName, value as Int)
            is Long -> editor.putLong(preference.backendName, value as Long)
            is Float -> editor.putFloat(preference.backendName, value as Float)
            is String? -> editor.putString(preference.backendName, value as String?)
            is Set<*> -> editor.putStringSet(preference.backendName, value as Set<String>)
            else -> throw Exception()
        }
        editor.apply()
    }
}
