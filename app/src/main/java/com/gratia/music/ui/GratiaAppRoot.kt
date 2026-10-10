package com.gratia.music.ui

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.animation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navigation
import androidx.navigation.navArgument
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.player.ExpandedPlayer
import com.gratia.music.ui.player.MiniPlayer

import com.gratia.music.ui.screens.*
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.components.liquidGlass

sealed class Screen(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object Browse : Screen("browse", "Explore", Icons.Outlined.Explore, Icons.Filled.Explore)
    data object Library : Screen("library", "Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic)
    data object Search : Screen("search", "Search", Icons.Outlined.Search, Icons.Filled.Search)
}

val bottomNavItems = listOf(Screen.Home, Screen.Browse, Screen.Library, Screen.Search)

val LocalBottomPadding = compositionLocalOf { 0.dp }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GratiaAppRoot(
    /**
     * Called once the app has its own first frame ready to draw. The system
     * splash is held until then, so the branded launch is the app *arriving*
     * rather than a logo pasted over a still-loading screen.
     */
    onReady: () -> Unit = {}
) {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val presentation by playerViewModel.presentation.collectAsState()
    val isPlayerExpanded = presentation is com.gratia.music.player.PlayerPresentationState.Expanded

    var sleepTimerSheetOpen by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsDataStore = remember { com.gratia.music.data.SettingsDataStore(context) }
    val themeOption by settingsDataStore.themeOptionFlow.collectAsState(initial = com.gratia.music.data.ThemeOption.SYSTEM)
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDark = when (themeOption) {
        com.gratia.music.data.ThemeOption.LIGHT -> false
        com.gratia.music.data.ThemeOption.DARK -> true
        com.gratia.music.data.ThemeOption.SYSTEM -> isSystemDark
    }
    val oledThemeEnabled by settingsDataStore.oledThemeEnabledFlow.collectAsState(initial = false)
    val onboardingCompleted by settingsDataStore.onboardingCompletedFlow.collectAsState(initial = null)
    val snackbarHostState = remember { SnackbarHostState() }

    // The one moment the launch screen is waiting for: the onboarding state has
    // resolved, so the app can decide what to draw. Until then the system
    // splash stays up (see MainActivity) — there is no second, in-app splash.
    LaunchedEffect(onboardingCompleted) {
        if (onboardingCompleted != null) onReady()
    }

    // Playback failures surface here once, app-wide. A remote track that fails
    // to resolve must not read as "nothing happened" — the old song kept
    // playing, and the listener is owed a line saying why the new one didn't.
    val playbackError by playerViewModel.playbackError.collectAsState()
    LaunchedEffect(playbackError) {
        playbackError?.let { message ->
            snackbarHostState.showSnackbar(message)
            playerViewModel.clearError()
        }
    }

    // No automatic update check. Updates start from Settings → Updates, because
    // someone asked for one — that is the whole policy now.

    GratiaTheme(themeOption = themeOption, isOledThemeEnabled = oledThemeEnabled) {
        CompositionLocalProvider(
            LocalSnackbarHostState provides snackbarHostState,
            LocalNavController provides navController,
            LocalPlayerViewModel provides playerViewModel
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (onboardingCompleted == null) {
                    Box(modifier = Modifier.fillMaxSize().background(GratiaTheme.colors.background))
                } else if (onboardingCompleted == false) {
                    var showPermissions by remember { mutableStateOf(false) }

                    if (!showPermissions) {
                        com.gratia.music.ui.onboarding.WelcomeScreen(
                            selectedTheme = themeOption,
                            settingsDataStore = settingsDataStore,
                            onContinue = { showPermissions = true }
                        )
                    } else {
                        com.gratia.music.ui.onboarding.PermissionIntroScreen(
                            onFinished = { /* Automatically handled by flow update */ },
                            settingsDataStore = settingsDataStore
                        )
                    }
                } else {
                    Scaffold(
                    containerColor = GratiaTheme.colors.background,
                    snackbarHost = { 
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = GratiaTheme.colors.surface,
                                contentColor = GratiaTheme.colors.textPrimary,
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    },
                    bottomBar = {
                var lastNavIndex by remember { mutableStateOf(0) }
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                val hierarchyMatch = bottomNavItems.indexOfFirst { screen ->
                    currentDestination?.hierarchy?.any { it.route == screen.route } == true
                }
                
                val navIndex = if (hierarchyMatch != -1) {
                    lastNavIndex = hierarchyMatch
                    hierarchyMatch
                } else {
                    lastNavIndex
                }

                com.gratia.music.ui.components.GratiaNavigationBar(
                    items = bottomNavItems,
                    selectedIndex = navIndex,
                    onItemSelected = { screen ->
                        // Top-level tabs always land on their canonical root
                        // (nav spec 14): the stack above Home is popped and no
                        // per-tab state is saved or restored, so a child route
                        // (a playlist, a tab deep-link like library_main?tab=…)
                        // can never be resurrected by a tab tap, and no second
                        // copy of a tab's graph can stack beneath the current
                        // one. Re-tapping a tab returns to that tab's root for
                        // the same reason.
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Home.route)
                            launchSingleTop = true
                        }
                    }
                )
            }
        ) { innerPadding ->
            val motion = GratiaTheme.motion
            
            // Calculate total dynamic bottom inset
            val miniPlayerHeight = if (currentSong != null && !isPlayerExpanded) 80.dp else 0.dp
            val totalBottomPadding = innerPadding.calculateBottomPadding() + miniPlayerHeight

            CompositionLocalProvider(LocalBottomPadding provides totalBottomPadding) {
                Box(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Home.route,
                        modifier = Modifier.fillMaxSize(), // Draw edge-to-edge behind the navbar
                enterTransition = {
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(motion.slow, easing = motion.standardEasing)
                    ) + fadeIn(animationSpec = tween(motion.slow))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(motion.slow))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(motion.slow))
                },
                popExitTransition = {
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(motion.slow, easing = motion.standardEasing)
                    ) + fadeOut(animationSpec = tween(motion.slow))
                }
            ) {
                composable(Screen.Home.route) {
                    val scope = rememberCoroutineScope()
                    HomeScreen(
                        playerViewModel = playerViewModel,
                        isDark = isDark,
                        onToggleTheme = {
                            scope.launch {
                                val nextTheme = if (isDark) com.gratia.music.data.ThemeOption.LIGHT else com.gratia.music.data.ThemeOption.DARK
                                settingsDataStore.setThemeOption(nextTheme)
                            }
                        },
                        onNavigateToUpload = { navController.navigate("upload") },
                        onNavigateToYou = { navController.navigate("you") },
                        onNavigateToRemotePlaylist = { navController.navigate("remote_playlist/${android.net.Uri.encode(it)}") },
                        onNavigateToStats = { navController.navigate("replay") },
                        onNavigateToHistory = { navController.navigate("listeningHistory") },
                        onNavigateToLibraryTab = { tab ->
                            navController.navigate("library_main?tab=${android.net.Uri.encode(tab)}")
                        }
                    )
                }
                navigation(startDestination = "search_main", route = Screen.Search.route) {
                    composable("search_main") {
                        SearchScreen(
                            playerViewModel = playerViewModel,
                            onNavigateToGenre = { navController.navigate("genre/${android.net.Uri.encode(it)}") },
                            onNavigateToRemoteArtist = { navController.navigate("remote_artist/${android.net.Uri.encode(it)}") },
                            onNavigateToRemoteAlbum = { navController.navigate("remote_album/${android.net.Uri.encode(it)}") },
                            onNavigateToRemotePlaylist = { navController.navigate("remote_playlist/${android.net.Uri.encode(it)}") }
                        )
                    }
                }
                
                // Global Search Destinations
                composable(
                    "genre/{genreName}",
                    arguments = listOf(navArgument("genreName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val genre = backStackEntry.arguments?.getString("genreName") ?: return@composable
                    GenreDetailScreen(
                        genre = genre,
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }

                // Remote Content Destinations (YouTube Music)
                composable(
                    "remote_artist/{channelId}",
                    arguments = listOf(navArgument("channelId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val channelId = backStackEntry.arguments?.getString("channelId") ?: return@composable
                    RemoteArtistScreen(
                        channelId = channelId,
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToAlbum = { navController.navigate("remote_album/${android.net.Uri.encode(it)}") },
                        onNavigateToArtist = { navController.navigate("remote_artist/${android.net.Uri.encode(it)}") },
                        // "Show all" is real navigation now, not in-page state —
                        // so Android Back returns to the list that was opened,
                        // and the list is a genuine stack entry (spec G/H/K).
                        onShowAllSongs = { playlistId, artistName ->
                            navController.navigate(
                                "remote_artist_collection/${android.net.Uri.encode(channelId)}" +
                                    "?kind=topSongs" +
                                    "&playlistId=${android.net.Uri.encode(playlistId ?: "")}" +
                                    "&title=${android.net.Uri.encode(artistName ?: "")}"
                            )
                        },
                        onShowAllAlbums = { artistName ->
                            navController.navigate(
                                "remote_artist_collection/${android.net.Uri.encode(channelId)}" +
                                    "?kind=albums&title=${android.net.Uri.encode(artistName ?: "")}"
                            )
                        },
                        onShowAllSingles = { artistName ->
                            navController.navigate(
                                "remote_artist_collection/${android.net.Uri.encode(channelId)}" +
                                    "?kind=singles&title=${android.net.Uri.encode(artistName ?: "")}"
                            )
                        }
                    )
                }

                composable(
                    "remote_artist_collection/{channelId}?kind={kind}&playlistId={playlistId}&title={title}",
                    arguments = listOf(
                        navArgument("channelId") { type = NavType.StringType },
                        navArgument("kind") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("playlistId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("title") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    )
                ) { backStackEntry ->
                    val channelId = backStackEntry.arguments?.getString("channelId") ?: return@composable
                    RemoteArtistCollectionScreen(
                        channelId = channelId,
                        kind = ArtistCollectionKind.fromKey(backStackEntry.arguments?.getString("kind")),
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToAlbum = { navController.navigate("remote_album/${android.net.Uri.encode(it)}") },
                        songsPlaylistId = backStackEntry.arguments?.getString("playlistId")
                            ?.takeIf { it.isNotBlank() },
                        knownArtistName = backStackEntry.arguments?.getString("title")
                            ?.takeIf { it.isNotBlank() },
                    )
                }

                composable(
                    "remote_album/{browseId}?title={title}",
                    arguments = listOf(
                        navArgument("browseId") { type = NavType.StringType },
                        // The display title rides along when the caller has it,
                        // so the page can show the name the listener tapped
                        // before the network answers (spec 27).
                        navArgument("title") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    )
                ) { backStackEntry ->
                    val browseId = backStackEntry.arguments?.getString("browseId") ?: return@composable
                    RemoteAlbumScreen(
                        browseId = browseId,
                        knownTitle = backStackEntry.arguments?.getString("title"),
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToArtist = { navController.navigate("remote_artist/${android.net.Uri.encode(it)}") }
                    )
                }

                composable(
                    "remote_playlist/{playlistId}?title={title}",
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.StringType },
                        navArgument("title") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    )
                ) { backStackEntry ->
                    val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
                    RemotePlaylistScreen(
                        playlistId = playlistId,
                        knownTitle = backStackEntry.arguments?.getString("title"),
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToArtist = { navController.navigate("remote_artist/${android.net.Uri.encode(it)}") },
                        onNavigateToAlbum = { navController.navigate("remote_album/${android.net.Uri.encode(it)}") }
                    )
                }
                navigation(startDestination = "library_main", route = Screen.Library.route) {
                    composable(
                        "library_main?tab={tab}",
                        arguments = listOf(navArgument("tab") { type = NavType.StringType; nullable = true })
                    ) { backStackEntry ->
                        val tab = backStackEntry.arguments?.getString("tab")
                        LibraryScreen(
                            playerViewModel = playerViewModel,
                            onNavigateToAlbum = { navController.navigate("album?name=${android.net.Uri.encode(it)}") },
                            onNavigateToArtist = { navController.navigate("artist?name=${android.net.Uri.encode(it)}") },
                            onNavigateToRemoteArtist = { navController.navigate("remote_artist/${android.net.Uri.encode(it)}") },
                            // Saved albums are remote catalogue entries, so their
                            // rows open the remote album page — the same page
                            // that offered the save, with the same identity.
                            onNavigateToRemoteAlbum = { navController.navigate("remote_album/${android.net.Uri.encode(it)}") },
                            onNavigateToFolder = { navController.navigate("folder/${android.net.Uri.encode(it)}") },
                            onNavigateToStats = { navController.navigate("replay") },
                            initialTab = tab
                        )
                    }
                }

                // Global Library Destinations
                composable("favorites") { // Favorites is still accessible from Home and Library
                    FavoritesScreen(playerViewModel = playerViewModel)
                }

                // Dedicated Local Music collection — local files only, with
                // its own Songs / Artists / Albums views. Reached from Library
                // → On Device → Local Music.
                composable("local_music") {
                    LocalMusicScreen(
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToAlbum = { navController.navigate("album?name=${android.net.Uri.encode(it)}") },
                        onNavigateToArtist = { navController.navigate("artist?name=${android.net.Uri.encode(it)}") }
                    )
                }
                composable(
                    "playlists?create={create}",
                    arguments = listOf(navArgument("create") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    })
                ) { backStackEntry ->
                    PlaylistsScreen(
                        autoOpenCreate = backStackEntry.arguments?.getString("create") == "true",
                        onNavigateToPlaylist = { navController.navigate("playlist/$it") },
                        onNavigateToAddMusic = { navController.navigate("playlist/$it?addMusic=true") }
                    )
                }
                composable(
                    "album?name={albumName}",
                    arguments = listOf(navArgument("albumName") { type = NavType.StringType; defaultValue = "" })
                ) { backStackEntry ->
                    val name = backStackEntry.arguments?.getString("albumName") ?: ""
                    AlbumDetailScreen(
                        albumName = name,
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    "artist?name={artistName}",
                    arguments = listOf(navArgument("artistName") { type = NavType.StringType; defaultValue = "" })
                ) { backStackEntry ->
                    val name = backStackEntry.arguments?.getString("artistName") ?: ""
                    ArtistDetailScreen(
                        artistName = name,
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    "folder/{folderName}",
                    arguments = listOf(navArgument("folderName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val name = backStackEntry.arguments?.getString("folderName") ?: ""
                    FolderDetailScreen(
                        folderName = name,
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    // addMusic=true is the create-flow continuation: a playlist
                    // made from the Playlists screen lands here with Add Music
                    // already open, so "what do I do now" is answered by the
                    // screen itself (playlist spec 16/27).
                    "playlist/{playlistId}?addMusic={addMusic}",
                    arguments = listOf(
                        navArgument("playlistId") { type = NavType.StringType },
                        navArgument("addMusic") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("playlistId") ?: ""
                    PlaylistDetailScreen(
                        playlistId = id,
                        autoOpenAddMusic = backStackEntry.arguments?.getString("addMusic") == "true",
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("upload") {
                    UploadScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Browse.route) {
                    BrowseScreen(
                        onNavigateToCategory = { browseId, params, title ->
                            navController.navigate(
                                "explore_category/${android.net.Uri.encode(browseId)}" +
                                    "?params=${android.net.Uri.encode(params ?: "")}" +
                                    "&title=${android.net.Uri.encode(title)}"
                            )
                        }
                    )
                }

                // Explore category page (Hindi, Chill, 1990s, …)
                composable(
                    "explore_category/{browseId}?params={params}&title={title}",
                    arguments = listOf(
                        navArgument("browseId") { type = NavType.StringType },
                        navArgument("params") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                        navArgument("title") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        }
                    )
                ) { backStackEntry ->
                    val browseId = backStackEntry.arguments?.getString("browseId") ?: return@composable
                    val params = backStackEntry.arguments?.getString("params")?.takeIf { it.isNotBlank() }
                    val title = backStackEntry.arguments?.getString("title")?.takeIf { it.isNotBlank() }
                        ?: "Explore"
                    BrowseCategoryScreen(
                        browseId = browseId,
                        params = params,
                        title = title,
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() },
                        onNavigateToAlbum = { navController.navigate("remote_album/${android.net.Uri.encode(it)}") },
                        onNavigateToArtist = { navController.navigate("remote_artist/${android.net.Uri.encode(it)}") },
                        onNavigateToPlaylist = { navController.navigate("remote_playlist/${android.net.Uri.encode(it)}") }
                    )
                }
                composable(
                    "editSong/{songId}",
                    arguments = listOf(navArgument("songId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val songId = backStackEntry.arguments?.getString("songId")
                    UploadScreen(
                        onNavigateBack = { navController.popBackStack() },
                        editSongId = songId
                    )
                }

                composable("you") {
                    YouScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToStorage = { navController.navigate("storage") },
                        onNavigateToListeningHistory = { navController.navigate("listeningHistory") },
                        onNavigateToAppearance = { navController.navigate("settings/appearance") },
                        onNavigateToEqualizer = { navController.navigate("equalizer") },
                        onNavigateToUpdate = { navController.navigate("update") },
                        onNavigateToLibrarySettings = { navController.navigate("settings/library") },
                        onNavigateToAbout = { navController.navigate("about") },
                        onNavigateToSongs = { navController.navigate("library_main?tab=Songs") },
                        onNavigateToAlbums = { navController.navigate("library_main?tab=Albums") },
                        onNavigateToArtists = { navController.navigate("library_main?tab=Artists") },
                        onNavigateToPlaylists = { navController.navigate("playlists") },
                        onNavigateToPlayback = { navController.navigate("settings/playback") },
                        onNavigateToLyrics = { navController.navigate("settings/lyrics") },
                        onNavigateToDownloads = { navController.navigate("settings/downloads") }
                    )
                }
                composable("listeningHistory") {
                    ListeningHistoryScreen(
                        playerViewModel = playerViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("replay") {
                    ReplayScreen(
                        playerViewModel = playerViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToAlbum = { navController.navigate("album?name=${android.net.Uri.encode(it)}") }
                    )
                }
                composable("storage") {
                    StorageScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("settings/appearance") {
                    SettingsAppearanceScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("settings/playback") {
                    SettingsPlaybackScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("settings/lyrics") {
                    SettingsLyricsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("settings/downloads") {
                    SettingsDownloadsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToStorage = { navController.navigate("storage") }
                    )
                }
                composable("settings/library") {
                    SettingsLibraryScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("update") {
                    UpdateScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("equalizer") {
                    EqualizerScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable("about") {
                    AboutScreen(
                        playerViewModel = playerViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToLicenses = { navController.navigate("licenses") },
                        onNavigateToDiagnostics = { navController.navigate("diagnostics") }
                    )
                }
                composable("diagnostics") {
                    PlaybackDiagnosticsScreen(
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("licenses") {
                    LicensesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }

            // Floating Mini Player Overlay
            // The mini player rides the same presentation state the expanded
            // player does, so collapse and reappear are two views of one fact
            // rather than two states that have to be kept in agreement.
            AnimatedVisibility(
                visible = currentSong != null && !isPlayerExpanded,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                MiniPlayer(playerViewModel = playerViewModel)
            }
            }
        }
        }


        AnimatedVisibility(
            visible = isPlayerExpanded && currentSong != null,
            enter = slideInVertically(
                initialOffsetY = { it }, 
                animationSpec = GratiaTheme.motion.springStandard()
            ) + fadeIn(animationSpec = tween(GratiaTheme.motion.normal)),
            exit = slideOutVertically(
                targetOffsetY = { it }, 
                animationSpec = tween(GratiaTheme.motion.normal, easing = GratiaTheme.motion.standardEasing)
            ) + fadeOut(animationSpec = tween(GratiaTheme.motion.normal))
        ) {
            ExpandedPlayer(
                playerViewModel = playerViewModel,
                onOpenSleepTimer = {
                    sleepTimerSheetOpen = true
                },
                onNavigateToAlbum = { albumName ->
                    navController.navigate("album?name=${android.net.Uri.encode(albumName)}")
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate("artist?name=${android.net.Uri.encode(artistName)}")
                },
                onNavigateToRemoteAlbum = { browseId ->
                    navController.navigate("remote_album/${android.net.Uri.encode(browseId)}")
                },
                onNavigateToRemoteArtist = { channelId ->
                    navController.navigate("remote_artist/${android.net.Uri.encode(channelId)}")
                },
                onNavigateToEqualizer = {
                    navController.navigate("equalizer")
                },
                onDismiss = {
                    playerViewModel.collapsePlayer()
                }
            )
        }

        // Queue is now handled inline inside ExpandedPlayer

        // Sleep Timer Bottom Sheet
        if (sleepTimerSheetOpen) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { sleepTimerSheetOpen = false },
                containerColor = GratiaTheme.colors.surface,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                shape = GratiaTheme.shapes.sheet
            ) {
                com.gratia.music.ui.player.SleepTimerSheet(
                    playerViewModel = playerViewModel,
                    onDismiss = { sleepTimerSheetOpen = false }
                )
            }
        }


        } // end of main app else block
    }
}
}
}
