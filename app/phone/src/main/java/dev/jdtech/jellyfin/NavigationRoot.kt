package dev.jdtech.jellyfin

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavOptions
import androidx.navigation.NavOptionsBuilder
import androidx.navigation.Navigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import androidx.window.core.layout.WindowSizeClass
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.FindroidBoxSet
import dev.jdtech.jellyfin.models.FindroidCollection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidFolder
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.models.MetadataFacet
import dev.jdtech.jellyfin.presentation.components.AppNavigationBar
import dev.jdtech.jellyfin.presentation.film.CollectionScreen
import dev.jdtech.jellyfin.presentation.film.DownloadsScreen
import dev.jdtech.jellyfin.presentation.film.EpisodeScreen
import dev.jdtech.jellyfin.presentation.film.FavoritesScreen
import dev.jdtech.jellyfin.presentation.film.FilterValueListScreen
import dev.jdtech.jellyfin.presentation.film.HomeScreen
import dev.jdtech.jellyfin.presentation.film.ItemFilterScreen
import dev.jdtech.jellyfin.presentation.film.LibraryScreen
import dev.jdtech.jellyfin.presentation.film.MediaScreen
import dev.jdtech.jellyfin.presentation.film.MovieScreen
import dev.jdtech.jellyfin.presentation.film.NamedItemListScreen
import dev.jdtech.jellyfin.presentation.film.PersonListScreen
import dev.jdtech.jellyfin.presentation.film.SeasonScreen
import dev.jdtech.jellyfin.presentation.film.ShowScreen
import dev.jdtech.jellyfin.presentation.settings.AboutScreen
import dev.jdtech.jellyfin.presentation.settings.SettingsFileEditScreen
import dev.jdtech.jellyfin.presentation.settings.SettingsScreen
import dev.jdtech.jellyfin.presentation.setup.addresses.ServerAddressesScreen
import dev.jdtech.jellyfin.presentation.setup.addserver.AddServerScreen
import dev.jdtech.jellyfin.presentation.setup.login.LoginScreen
import dev.jdtech.jellyfin.presentation.setup.servers.ServersScreen
import dev.jdtech.jellyfin.presentation.setup.users.UsersScreen
import dev.jdtech.jellyfin.presentation.setup.welcome.WelcomeScreen
import dev.jdtech.jellyfin.presentation.theme.Motion
import dev.jdtech.jellyfin.presentation.utils.LocalNavAnimatedVisibilityScope
import dev.jdtech.jellyfin.presentation.utils.LocalOfflineMode
import dev.jdtech.jellyfin.presentation.utils.LocalSharedTransitionScope
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable data object WelcomeRoute

@Serializable data object ServersRoute

@Serializable data object AddServerRoute

@Serializable data class ServerAddressesRoute(val serverId: String)

@Serializable data object UsersRoute

@Serializable data class LoginRoute(val username: String? = null)

@Serializable data object HomeRoute

@Serializable data object MediaRoute

@Serializable data object DownloadsRoute

@Serializable
data class LibraryRoute(
    val libraryId: String,
    val libraryName: String,
    val libraryType: CollectionType,
)

@Serializable data class CollectionRoute(val collectionId: String, val collectionName: String)

@Serializable data object FavoritesRoute

@Serializable data class MovieRoute(val movieId: String)

@Serializable data class ShowRoute(val showId: String)

@Serializable data class EpisodeRoute(val episodeId: String)

@Serializable data class SeasonRoute(val seasonId: String)

/**
 * 元数据维度浏览页。
 *
 * 各维度对应不同的列表形态（命名实体 / 人物 / 文本值），由同一个路由按维度分发到对应 Screen，
 * 避免为每个维度各建一套路由。
 */
@Serializable data class MetadataBrowseRoute(val facet: MetadataFacet)

/** 按某个元数据值筛选出的条目列表。[key] 为服务端实体 id 或文本值。 */
@Serializable
data class ItemFilterRoute(val facet: MetadataFacet, val key: String, val title: String)

@Serializable data class SettingsRoute(val indexes: IntArray)

@Serializable data class SettingsFileEditRoute(val filePath: String)

@Serializable data object AboutRoute

data class TabBarItem(
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
    val route: Any,
    val enabled: Boolean = true,
)

val homeTab =
    TabBarItem(title = CoreR.string.title_home, icon = CoreR.drawable.ic_home, route = HomeRoute)
val mediaTab =
    TabBarItem(
        title = CoreR.string.title_media,
        icon = CoreR.drawable.ic_library,
        route = MediaRoute,
    )
val settingsTab =
    TabBarItem(
        title = CoreR.string.title_settings,
        icon = CoreR.drawable.ic_settings,
        route = SettingsRoute(indexes = intArrayOf(CoreR.string.title_settings)),
    )

/** 判定滚动方向的最小位移，低于它的抖动不触发底栏显隐，避免动画闪烁。 */
private val SCROLL_DIRECTION_THRESHOLD = 8.dp

@Composable
fun NavigationRoot(
    navController: NavHostController,
    hasServers: Boolean,
    hasCurrentServer: Boolean,
    hasCurrentUser: Boolean,
) {
    val isOfflineMode = LocalOfflineMode.current

    val startDestination =
        when {
            hasServers && hasCurrentServer && hasCurrentUser -> HomeRoute
            hasServers && hasCurrentServer -> UsersRoute
            hasServers -> ServersRoute
            else -> WelcomeRoute
        }

    val navigationItems =
        when (isOfflineMode) {
            false -> listOf(homeTab, mediaTab, settingsTab)
            true -> listOf(homeTab, settingsTab)
        }
    val navBackStackEntry by navController.currentBackStackEntryAsState()

    var searchExpanded by remember { mutableStateOf(false) }

    val currentRoute = navBackStackEntry?.destination?.route
    // 目的地上的 route 是「模式串」：无参路由就是类名，带参路由会追加 ?indexes={indexes}
    // 这类占位符（设置标签属于后者），所以先还原成类名，再与各 tab 比对
    val currentTab =
        navigationItems.firstOrNull {
            it.route::class.qualifiedName == currentRoute?.substringBefore('?')
        }
    val showNavigationBar = currentTab != null && !searchExpanded

    // 列表向上滑时隐藏底栏，反向下滑恢复；切到别的 tab 后重新显示
    var hiddenByScroll by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute) { hiddenByScroll = false }

    val hideThresholdPx = with(LocalDensity.current) { SCROLL_DIRECTION_THRESHOLD.toPx() }
    val hideOnScrollConnection =
        remember(hideThresholdPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    // 只看纵向位移，横向切页（pager）的 y 为 0，不会误触发
                    when {
                        available.y < -hideThresholdPx -> hiddenByScroll = true
                        available.y > hideThresholdPx -> hiddenByScroll = false
                    }
                    // 不消费位移，列表照常滚动
                    return Offset.Zero
                }
            }
        }

    val windowAdaptiveInfo = currentWindowAdaptiveInfo()
    // 中宽屏（折叠屏展开）及以上底栏收成底部居中的悬浮胶囊；断点用 medium 而非 expanded，
    // 因为折叠屏展开后的宽度多在 600–840dp 之间，用 840dp 断点会漏掉大部分折叠屏
    val isFloatingNavigationBar =
        windowAdaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(
            WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND
        )

    Box(modifier = Modifier.fillMaxSize().nestedScroll(hideOnScrollConnection)) {
        SharedTransitionNavHost(
            navController = navController,
            startDestination = startDestination,
        ) {
            composable<WelcomeRoute> {
                WelcomeScreen(onContinueClick = { navController.safeNavigate(ServersRoute) })
            }
            composable<ServersRoute> {
                ServersScreen(
                    navigateToUsers = { navController.safeNavigate(UsersRoute) },
                    navigateToAddresses = { serverId ->
                        navController.safeNavigate(ServerAddressesRoute(serverId))
                    },
                    onAddClick = { navController.safeNavigate(AddServerRoute) },
                    onBackClick = { navController.safePopBackStack() },
                    showBack = navController.previousBackStackEntry != null,
                )
            }
            composable<AddServerRoute> {
                AddServerScreen(
                    onSuccess = { navController.safeNavigate(UsersRoute) },
                    onBackClick = { navController.safePopBackStack() },
                )
            }
            composable<ServerAddressesRoute> { backStackEntry ->
                val route: ServerAddressesRoute = backStackEntry.toRoute()
                ServerAddressesScreen(
                    serverId = route.serverId,
                    navigateBack = { navController.safePopBackStack() },
                )
            }
            composable<UsersRoute> {
                UsersScreen(
                    navigateToHome = { navigateHome(navController) },
                    onChangeServerClick = {
                        navController.safeNavigate(ServersRoute) {
                            popUpTo(ServersRoute) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onAddClick = { navController.safeNavigate(LoginRoute()) },
                    onBackClick = { navController.safePopBackStack() },
                    onPublicUserClick = { username ->
                        navController.safeNavigate(LoginRoute(username = username))
                    },
                    showBack = navController.previousBackStackEntry != null,
                )
            }
            composable<LoginRoute> { backStackEntry ->
                val route: LoginRoute = backStackEntry.toRoute()
                LoginScreen(
                    onSuccess = {
                        navController.safeNavigate(HomeRoute) {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    },
                    onChangeServerClick = {
                        navController.safeNavigate(ServersRoute) {
                            popUpTo(ServersRoute) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onBackClick = { navController.safePopBackStack() },
                    prefilledUsername = route.username,
                )
            }
            composable<HomeRoute> {
                SharedElementHost(this) {
                    HomeScreen(
                        onLibraryClick = {
                            navController.safeNavigate(
                                LibraryRoute(
                                    libraryId = it.id.toString(),
                                    libraryName = it.name,
                                    libraryType = it.type,
                                )
                            )
                        },
                        onSearchClick = {
                            searchExpanded = true
                            navController.safeNavigate(MediaRoute) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onDownloadsClick = { navController.safeNavigate(DownloadsRoute) },
                        onManageServers = { navController.safeNavigate(ServersRoute) },
                        onItemClick = { item ->
                            navigateToItem(navController = navController, item = item)
                        },
                    )
                }
            }
            composable<MediaRoute> {
                SharedElementHost(this) {
                    MediaScreen(
                        onItemClick = { item ->
                            navigateToItem(navController = navController, item = item)
                        },
                        onFavoritesClick = { navController.safeNavigate(FavoritesRoute) },
                        onFacetClick = { facet ->
                            navController.safeNavigate(MetadataBrowseRoute(facet))
                        },
                        searchExpanded = searchExpanded,
                        onSearchExpand = { searchExpanded = it },
                    )
                }
            }
            composable<DownloadsRoute> {
                SharedElementHost(this) {
                    DownloadsScreen(
                        onItemClick = { item ->
                            navigateToItem(navController = navController, item = item)
                        }
                    )
                }
            }
            composable<LibraryRoute> { backStackEntry ->
                val route: LibraryRoute = backStackEntry.toRoute()
                SharedElementHost(this) {
                    LibraryScreen(
                        libraryId = UUID.fromString(route.libraryId),
                        libraryName = route.libraryName,
                        libraryType = route.libraryType,
                        onItemClick = { item ->
                            navigateToItem(navController = navController, item = item)
                        },
                        navigateBack = { navController.safePopBackStack() },
                    )
                }
            }
            composable<CollectionRoute> { backStackEntry ->
                val route: CollectionRoute = backStackEntry.toRoute()
                SharedElementHost(this) {
                    CollectionScreen(
                        collectionId = UUID.fromString(route.collectionId),
                        collectionName = route.collectionName,
                        onItemClick = { item ->
                            navigateToItem(navController = navController, item = item)
                        },
                        navigateBack = { navController.safePopBackStack() },
                    )
                }
            }
            composable<FavoritesRoute> {
                SharedElementHost(this) {
                    FavoritesScreen(
                        onItemClick = { item ->
                            navigateToItem(navController = navController, item = item)
                        },
                        navigateBack = { navController.safePopBackStack() },
                    )
                }
            }
            composable<MovieRoute>(
                // 详情页：进入淡入 + 轻微放大，返回淡出 + 轻微收缩，与封面共享元素一起构成容器变换感
                enterTransition = { detailEnterTransition() },
                popExitTransition = { detailPopExitTransition() },
            ) { backStackEntry ->
                val route: MovieRoute = backStackEntry.toRoute()
                SharedElementHost(this) {
                    MovieScreen(
                        movieId = UUID.fromString(route.movieId),
                        navigateBack = { navController.safePopBackStack() },
                        navigateHome = { navigateHome(navController) },
                        navigateToPerson = { personId, personName ->
                            navController.safeNavigate(
                                ItemFilterRoute(
                                    facet = MetadataFacet.ACTOR,
                                    key = personId.toString(),
                                    title = personName,
                                )
                            )
                        },
                        navigateToMetadataFacet = { facet, key ->
                            // 类型 / 标签没有独立实体 id，直接按值筛选
                            navController.safeNavigate(
                                ItemFilterRoute(facet = facet, key = key, title = key)
                            )
                        },
                    )
                }
            }
            composable<ShowRoute>(
                enterTransition = { detailEnterTransition() },
                popExitTransition = { detailPopExitTransition() },
            ) { backStackEntry ->
                val route: ShowRoute = backStackEntry.toRoute()
                ShowScreen(
                    showId = UUID.fromString(route.showId),
                    navigateBack = { navController.safePopBackStack() },
                    navigateHome = { navigateHome(navController) },
                    navigateToItem = { item ->
                        navigateToItem(navController = navController, item = item)
                    },
                    navigateToPerson = { personId, personName ->
                        navController.safeNavigate(
                            ItemFilterRoute(
                                facet = MetadataFacet.ACTOR,
                                key = personId.toString(),
                                title = personName,
                            )
                        )
                    },
                )
            }
            composable<SeasonRoute>(
                enterTransition = { detailEnterTransition() },
                popExitTransition = { detailPopExitTransition() },
            ) { backStackEntry ->
                val route: SeasonRoute = backStackEntry.toRoute()
                SeasonScreen(
                    seasonId = UUID.fromString(route.seasonId),
                    navigateBack = { navController.safePopBackStack() },
                    navigateHome = { navigateHome(navController) },
                    navigateToItem = { item ->
                        navigateToItem(navController = navController, item = item)
                    },
                    navigateToSeries = { seriesId ->
                        navController.safeNavigate(ShowRoute(showId = seriesId.toString())) {
                            popUpTo(ShowRoute(showId = seriesId.toString()))
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<EpisodeRoute>(
                enterTransition = { detailEnterTransition() },
                popExitTransition = { detailPopExitTransition() },
            ) { backStackEntry ->
                val route: EpisodeRoute = backStackEntry.toRoute()
                EpisodeScreen(
                    episodeId = UUID.fromString(route.episodeId),
                    navigateBack = { navController.safePopBackStack() },
                    navigateHome = { navigateHome(navController) },
                    navigateToPerson = { personId, personName ->
                        navController.safeNavigate(
                            ItemFilterRoute(
                                facet = MetadataFacet.ACTOR,
                                key = personId.toString(),
                                title = personName,
                            )
                        )
                    },
                    navigateToSeason = { seasonId ->
                        navController.safeNavigate(SeasonRoute(seasonId = seasonId.toString())) {
                            popUpTo(SeasonRoute(seasonId = seasonId.toString()))
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable<MetadataBrowseRoute> { backStackEntry ->
                val route: MetadataBrowseRoute = backStackEntry.toRoute()
                when (route.facet) {
                    MetadataFacet.GENRE,
                    MetadataFacet.STUDIO ->
                        NamedItemListScreen(
                            facet = route.facet,
                            onItemClick = { item ->
                                navController.safeNavigate(
                                    ItemFilterRoute(
                                        facet = route.facet,
                                        key = item.id.toString(),
                                        title = item.name,
                                    )
                                )
                            },
                            navigateBack = { navController.safePopBackStack() },
                        )
                    MetadataFacet.ACTOR,
                    MetadataFacet.DIRECTOR,
                    MetadataFacet.WRITER ->
                        PersonListScreen(
                            facet = route.facet,
                            // 人物不再有独立详情页，点击直接进入按该人物筛选的作品列表
                            onPersonClick = { person ->
                                navController.safeNavigate(
                                    ItemFilterRoute(
                                        facet = route.facet,
                                        key = person.id.toString(),
                                        title = person.name,
                                    )
                                )
                            },
                            navigateBack = { navController.safePopBackStack() },
                        )
                    MetadataFacet.TAG,
                    MetadataFacet.OFFICIAL_RATING,
                    MetadataFacet.YEAR ->
                        FilterValueListScreen(
                            facet = route.facet,
                            onValueClick = { value ->
                                navController.safeNavigate(
                                    ItemFilterRoute(
                                        facet = route.facet,
                                        key = value,
                                        title = value,
                                    )
                                )
                            },
                            navigateBack = { navController.safePopBackStack() },
                        )
                }
            }
            composable<ItemFilterRoute> { backStackEntry ->
                val route: ItemFilterRoute = backStackEntry.toRoute()
                SharedElementHost(this) {
                    ItemFilterScreen(
                        facet = route.facet,
                        filterKey = route.key,
                        title = route.title,
                        onItemClick = { item ->
                            navigateToItem(navController = navController, item = item)
                        },
                        navigateBack = { navController.safePopBackStack() },
                    )
                }
            }
            composable<SettingsRoute> { backStackEntry ->
                val route: SettingsRoute = backStackEntry.toRoute()
                SettingsScreen(
                    indexes = route.indexes,
                    navigateToSettings = { indexes ->
                        navController.safeNavigate(SettingsRoute(indexes = indexes))
                    },
                    navigateToSettingsFileEdit = { filePath ->
                        navController.safeNavigate(SettingsFileEditRoute(filePath = filePath))
                    },
                    navigateToServers = { navController.safeNavigate(ServersRoute) },
                    navigateToUsers = { navController.safeNavigate(UsersRoute) },
                    navigateToAbout = { navController.safeNavigate(AboutRoute) },
                    navigateBack = { navController.safePopBackStack() },
                )
            }
            composable<SettingsFileEditRoute> { backStackEntry ->
                val route: SettingsFileEditRoute = backStackEntry.toRoute()
                SettingsFileEditScreen(
                    filePath = route.filePath,
                    navigateBack = { navController.safePopBackStack() },
                )
            }
            composable<AboutRoute> {
                AboutScreen(navigateBack = { navController.safePopBackStack() })
            }
        }

        AnimatedVisibility(
            visible = showNavigationBar && !hiddenByScroll,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            AppNavigationBar(
                items = navigationItems,
                selectedRoute = currentTab?.route?.let { it::class.qualifiedName },
                floating = isFloatingNavigationBar,
                onItemClick = { item ->
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

private fun navigateHome(navController: NavHostController) {
    navController.safeNavigate(HomeRoute) {
        popUpTo(navController.graph.startDestinationId)
        launchSingleTop = true
    }
}

private fun navigateToItem(navController: NavHostController, item: FindroidItem) {
    when (item) {
        is FindroidBoxSet ->
            navController.safeNavigate(
                CollectionRoute(collectionId = item.id.toString(), collectionName = item.name)
            )
        is FindroidMovie -> navController.safeNavigate(MovieRoute(movieId = item.id.toString()))
        is FindroidShow -> navController.safeNavigate(ShowRoute(showId = item.id.toString()))
        is FindroidSeason -> navController.safeNavigate(SeasonRoute(seasonId = item.id.toString()))
        is FindroidEpisode ->
            navController.safeNavigate(EpisodeRoute(episodeId = item.id.toString()))
        is FindroidCollection ->
            navController.safeNavigate(
                LibraryRoute(
                    libraryId = item.id.toString(),
                    libraryName = item.name,
                    libraryType = item.type,
                )
            )
        is FindroidFolder ->
            navController.safeNavigate(
                LibraryRoute(
                    libraryId = item.id.toString(),
                    libraryName = item.name,
                    libraryType = CollectionType.Folders,
                )
            )
        else -> Unit
    }
}

private fun <T : Any> NavHostController.safeNavigate(
    route: T,
    navOptions: NavOptions? = null,
    navigatorExtras: Navigator.Extras? = null,
) {
    if (this.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        this.navigate(route, navOptions, navigatorExtras)
    }
}

private fun <T : Any> NavHostController.safeNavigate(
    route: T,
    builder: NavOptionsBuilder.() -> Unit,
) {
    if (this.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        this.navigate(route, builder)
    }
}

private fun NavHostController.safePopBackStack(): Boolean {
    return if (this.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED) {
        this.popBackStack()
    } else {
        false
    }
}

/**
 * NavHost + SharedTransitionLayout 的组合。
 *
 * 全局注册共享元素作用域（[LocalSharedTransitionScope]），并把全局转场统一为 M3 规范：
 * 进入用 decelerate（快起缓停），退出用 accelerate 且更快（M3 非对称规则）。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionNavHost(
    navController: NavHostController,
    startDestination: Any,
    builder: NavGraphBuilder.() -> Unit,
) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                // 底部标签之间是瞬时切换（共享元素轮不到标签间播放，详情页仍走自己的转场）；
                // 其余导航沿用 M3 规范的全局淡入淡出。
                // 预测性返回已在 AndroidManifest 关闭（enableOnBackInvokedCallback=false），
                // 因此这里不再注册 predictivePop* 转场；若日后恢复预测性返回，需一并补回
                enterTransition = {
                    if (isTabSwitch()) EnterTransition.None else defaultEnterTransition()
                },
                exitTransition = {
                    if (isTabSwitch()) ExitTransition.None else defaultExitTransition()
                },
                popEnterTransition = {
                    if (isTabSwitch()) EnterTransition.None else defaultEnterTransition()
                },
                popExitTransition = {
                    if (isTabSwitch()) ExitTransition.None else defaultExitTransition()
                },
            ) {
                builder()
            }
        }
    }
}

/** 把目的地的转场作用域经 CompositionLocal 下发，供列表封面与详情页头部建立共享元素关联。 */
@Composable
private fun SharedElementHost(scope: AnimatedVisibilityScope, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides scope, content = content)
}

/**
 * 目的地对应的底部标签序号；不是标签页（详情、设置子页等）返回 null。
 *
 * 序号只用来区分「是不是同一个标签」，取值本身没有含义。
 */
private fun tabIndexOf(entry: NavBackStackEntry): Int? {
    // 目的地上的 route 是「模式串」，带参路由会追加 ?indexes={indexes}，先还原成类名再比对
    val routeClass = entry.destination.route?.substringBefore('?') ?: return null
    return when (routeClass) {
        HomeRoute::class.qualifiedName -> 0
        MediaRoute::class.qualifiedName -> 1
        SettingsRoute::class.qualifiedName -> {
            // 设置页复用同一个目的地，根部与各级子页只能靠参数区分：一级分类页（如「语言」）的
            // indexes 同样只有一项，所以要看内容是不是标签标题，不能按长度判断
            val indexes = runCatching { entry.toRoute<SettingsRoute>().indexes }.getOrNull()
            if (indexes?.firstOrNull() == CoreR.string.title_settings) 2 else null
        }
        else -> null
    }
}

/** 是否在两个不同的底部标签之间切换（标签间切换不做动画）。 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean {
    val fromIndex = tabIndexOf(initialState) ?: return false
    val toIndex = tabIndexOf(targetState) ?: return false
    return fromIndex != toIndex
}

/** 全局前进导航进入：淡入（快起缓停）。 */
private fun defaultEnterTransition(): EnterTransition =
    fadeIn(tween(durationMillis = Motion.DurationMedium4, easing = Motion.EasingEmphasizedDecelerate))

/** 全局前进导航退出：淡出，比进入更快（M3 非对称规则）。 */
private fun defaultExitTransition(): ExitTransition =
    fadeOut(tween(durationMillis = Motion.DurationShort4, easing = Motion.EasingEmphasizedAccelerate))

/** 详情页进入：整体淡入并轻微放大（0.94 → 1），与返回时的收缩构成容器变换感。 */
private fun detailEnterTransition(): EnterTransition =
    fadeIn(tween(durationMillis = Motion.DurationMedium4, easing = Motion.EasingEmphasizedDecelerate)) +
        scaleIn(
            initialScale = 0.94f,
            animationSpec =
                tween(
                    durationMillis = Motion.DurationMedium4,
                    easing = Motion.EasingEmphasizedDecelerate,
                ),
        )

/** 详情页退出（返回）：淡出并轻微收缩，退出比进入更快（M3 非对称规则）。 */
private fun detailPopExitTransition(): ExitTransition =
    fadeOut(tween(durationMillis = Motion.DurationShort4, easing = Motion.EasingEmphasizedAccelerate)) +
        scaleOut(
            targetScale = 0.94f,
            animationSpec =
                tween(
                    durationMillis = Motion.DurationShort4,
                    easing = Motion.EasingEmphasizedAccelerate,
                ),
        )
