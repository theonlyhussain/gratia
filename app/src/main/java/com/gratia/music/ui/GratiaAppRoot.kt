package com.gratia.music.ui

import kotlinx.coroutines.launch
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
import androidx.compose.foundation.clickable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.player.ExpandedPlayer
import com.gratia.music.ui.player.MiniPlayer
import com.gratia.music.ui.player.QueueSheet
import com.gratia.music.ui.screens.*
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.components.liquidGlass

sealed class Screen(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Home : Screen("home", "Listen Now", Icons.Outlined.PlayCircleOutline, Icons.Filled.PlayCircle)
    data object Browse : Screen("browse", "Browse", Icons.Outlined.Explore, Icons.Filled.Explore)
    data object Library : Screen("library", "Library", Icons.Outlined.LibraryMusic, Icons.Filled.LibraryMusic)
    data object Search : Screen("search", "Search", Icons.Outlined.Search, Icons.Filled.Search)
}

val bottomNavItems = listOf(Screen.Home, Screen.Browse, Screen.Library, Screen.Search)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GratiaAppRoot() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val expandedPlayerOpen by playerViewModel.expandedPlayerOpen.collectAsState()
    val lyricsOverlayOpen by playerViewModel.lyricsOverlayOpen.collectAsState()
    var queueSheetOpen by remember { mutableStateOf(false) }
    var sleepTimerSheetOpen by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsDataStore = remember { com.gratia.music.data.SettingsDataStore(context) }
    val themeOption by settingsDataStore.themeOptionFlow.collectAsState(initial = com.gratia.music.data.ThemeOption.SYSTEM)
    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val onboardingCompleted by settingsDataStore.onboardingCompletedFlow.collectAsState(initial = null)
    
    val isDark = when (themeOption) {
        com.gratia.music.data.ThemeOption.LIGHT -> false
        com.gratia.music.data.ThemeOption.DARK -> true
        com.gratia.music.data.ThemeOption.SYSTEM -> isSystemDark
    }

    val snackbarHostState = remember { SnackbarHostState() }

    GratiaTheme(isDark = isDark) {
        CompositionLocalProvider(
            LocalSnackbarHostState provides snackbarHostState,
            LocalNavController provides navController,
            LocalPlayerViewModel provides playerViewModel
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (onboardingCompleted == null) {
                    Box(modifier = Modifier.fillMaxSize().background(GratiaTheme.colors.background))
                } else if (onboardingCompleted == false) {
                    com.gratia.music.ui.onboarding.PermissionIntroScreen(
                        onFinished = { /* Automatically handled by flow update */ },
                        settingsDataStore = settingsDataStore
                    )
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
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                Column {
                    // Mini Player — sits above bottom nav
                    AnimatedVisibility(
                        visible = currentSong != null && !expandedPlayerOpen,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        MiniPlayer(playerViewModel = playerViewModel)
                    }

                    // Apple Music Style Bottom Navigation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(GratiaTheme.colors.surface.copy(alpha = 0.85f))
                            .border(
                                width = 0.5.dp,
                                color = GratiaTheme.colors.textSecondary.copy(alpha = 0.2f),
                                shape = androidx.compose.ui.graphics.RectangleShape
                            )
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        val navIndex = bottomNavItems.indexOfFirst { screen ->
                            currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        }.coerceAtLeast(0)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            bottomNavItems.forEachIndexed { index, screen ->
                                val selected = index == navIndex
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null, // Apple has no ripple
                                            onClick = {
                                                navController.navigate(screen.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            }
                                        ),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    val iconTint by androidx.compose.animation.animateColorAsState(
                                        targetValue = if (selected) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary,
                                        label = "iconTint"
                                    )
                                    val textTint by androidx.compose.animation.animateColorAsState(
                                        targetValue = if (selected) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary,
                                        label = "textTint"
                                    )
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.icon,
                                        contentDescription = screen.label,
                                        modifier = Modifier.size(26.dp),
                                        tint = iconTint
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = screen.label,
                                        fontSize = 10.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Medium else androidx.compose.ui.text.font.FontWeight.Normal,
                                        color = textTint,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            val motion = GratiaTheme.motion
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding),
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
                        onNavigateToProfile = { navController.navigate("profile") },
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(playerViewModel = playerViewModel)
                }
                composable(Screen.Library.route) {
                    LibraryScreen(
                        playerViewModel = playerViewModel,
                        onNavigateToAlbum = { navController.navigate("album/$it") },
                        onNavigateToArtist = { navController.navigate("artist/$it") },
                        onNavigateToFolder = { navController.navigate("folder/$it") }
                    )
                }
                composable("favorites") { // Favorites is still accessible from Home
                    FavoritesScreen(playerViewModel = playerViewModel)
                }
                composable("playlists") {
                    PlaylistsScreen(onNavigateToPlaylist = { navController.navigate("playlist/$it") })
                }
                composable("upload") {
                    UploadScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable(Screen.Browse.route) {
                    BrowseScreen(playerViewModel = playerViewModel)
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
                composable(
                    "fullLyrics/{songId}",
                    arguments = listOf(navArgument("songId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val songId = backStackEntry.arguments?.getString("songId")
                    FullLyricsScreen(
                        playerViewModel = playerViewModel,
                        songId = songId,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("fullLyrics") {
                    FullLyricsScreen(
                        playerViewModel = playerViewModel,
                        songId = null,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("profile") {
                    ProfileScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToStorage = { navController.navigate("storage") }
                    )
                }
                composable("storage") {
                    StorageScreen(onNavigateBack = { navController.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToAbout = { navController.navigate("about") },
                        onNavigateToLicenses = { navController.navigate("licenses") },
                        onNavigateToEqualizer = { navController.navigate("equalizer") }
                    )
                }
                composable("equalizer") {
                    EqualizerScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    "album/{albumName}",
                    arguments = listOf(navArgument("albumName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val name = backStackEntry.arguments?.getString("albumName") ?: ""
                    AlbumDetailScreen(
                        albumName = name,
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    "artist/{artistName}",
                    arguments = listOf(navArgument("artistName") { type = NavType.StringType })
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
                    "playlist/{playlistId}",
                    arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("playlistId") ?: ""
                    PlaylistDetailScreen(
                        playlistId = id,
                        playerViewModel = playerViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("about") {
                    AboutScreen(
                        playerViewModel = playerViewModel,
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToLicenses = { navController.navigate("licenses") }
                    )
                }
                composable("licenses") {
                    LicensesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // Expanded Player Overlay
        AnimatedVisibility(
            visible = expandedPlayerOpen && currentSong != null,
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
                onOpenLyrics = {
                    playerViewModel.setExpandedPlayerOpen(false)
                    val songId = currentSong?.id ?: return@ExpandedPlayer
                    navController.navigate("fullLyrics/$songId")
                },
                onOpenQueue = {
                    queueSheetOpen = true
                },
                onOpenSleepTimer = {
                    sleepTimerSheetOpen = true
                },
                onNavigateToAlbum = { albumName ->
                    navController.navigate("album/$albumName")
                },
                onNavigateToArtist = { artistName ->
                    navController.navigate("artist/$artistName")
                },
                onDismiss = {
                    playerViewModel.setExpandedPlayerOpen(false)
                }
            )
        }

        // Queue Bottom Sheet
        if (queueSheetOpen) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { queueSheetOpen = false },
                containerColor = GratiaTheme.colors.surface,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f),
                shape = GratiaTheme.shapes.sheet
            ) {
                QueueSheet(
                    playerViewModel = playerViewModel,
                    onDismiss = { queueSheetOpen = false }
                )
            }
        }

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

        // Lyrics overlay (from lyrics button in expanded player)
        AnimatedVisibility(
            visible = lyricsOverlayOpen && currentSong != null,
            enter = slideInVertically(
                initialOffsetY = { it }, 
                animationSpec = GratiaTheme.motion.springStandard()
            ) + fadeIn(animationSpec = tween(GratiaTheme.motion.normal)),
            exit = slideOutVertically(
                targetOffsetY = { it }, 
                animationSpec = tween(GratiaTheme.motion.normal, easing = GratiaTheme.motion.standardEasing)
            ) + fadeOut(animationSpec = tween(GratiaTheme.motion.normal))
        ) {
            FullLyricsScreen(
                playerViewModel = playerViewModel,
                songId = currentSong?.id,
                onBack = { playerViewModel.setLyricsOverlayOpen(false) }
            )
        }

        } // end of main app else block

        // Splash Transition Overlay
        var showSplash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(800)
            showSplash = false
        }

        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(tween(GratiaTheme.motion.instant)), // Starts visible
            exit = fadeOut(tween(GratiaTheme.motion.hero)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GratiaTheme.colors.background),
                contentAlignment = Alignment.Center
            ) {
                val logoScale by animateFloatAsState(
                    targetValue = if (showSplash) 1.0f else 0.92f,
                    animationSpec = tween(GratiaTheme.motion.hero, easing = GratiaTheme.motion.standardEasing),
                    label = "splashScale"
                )
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.gratia.music.R.drawable.gratia_logo),
                    contentDescription = "Gratia Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .scale(logoScale)
                        .clip(GratiaTheme.shapes.hero)
                )
            }
            }
        }
    }
}
}
