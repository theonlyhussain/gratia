package com.gratia.music.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.ExpandedPlayer
import com.gratia.music.ui.components.PlayerBar
import com.gratia.music.ui.screens.*
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.components.liquidGlass

sealed class Screen(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Outlined.Home, Icons.Filled.Home)
    data object Search : Screen("search", "Search", Icons.Outlined.Search, Icons.Filled.Search)
    data object Radio : Screen("radio", "Radio", Icons.Outlined.Radio, Icons.Filled.Radio)
    data object Favorites : Screen("favorites", "Favorites", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite)
    data object Playlists : Screen("playlists", "Playlists", Icons.Outlined.QueueMusic, Icons.Filled.QueueMusic)
}

val bottomNavItems = listOf(Screen.Home, Screen.Search, Screen.Radio, Screen.Favorites, Screen.Playlists)

@Composable
fun GratiaAppRoot() {
    val navController = rememberNavController()
    val playerViewModel: PlayerViewModel = viewModel()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val expandedPlayerOpen by playerViewModel.expandedPlayerOpen.collectAsState()
    val lyricsOverlayOpen by playerViewModel.lyricsOverlayOpen.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = GratiaTheme.colors.cotton,
            bottomBar = {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                Column {
                    // Mini Player — sits above bottom nav
                    if (currentSong != null) {
                        PlayerBar(playerViewModel = playerViewModel)
                    }

                    // Glassmorphism Bottom Navigation
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .shadow(24.dp, RoundedCornerShape(32.dp), spotColor = GratiaTheme.colors.maroon.copy(alpha = 0.15f), ambientColor = GratiaTheme.colors.maroon.copy(alpha = 0.05f))
                            .liquidGlass(
                                shape = RoundedCornerShape(32.dp),
                                backgroundColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                                borderColorStart = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.4f),
                                borderColorEnd = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f)
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .height(64.dp)
                    ) {
                        val navIndex = bottomNavItems.indexOfFirst { screen ->
                            currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        }.coerceAtLeast(0)
                        
                        val tabWidth = maxWidth / bottomNavItems.size
                        val indicatorOffsetX by animateDpAsState(
                            targetValue = tabWidth * navIndex,
                            animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                            label = "indicatorOffset"
                        )

                        // The sliding glass pill highlight
                        Box(
                            modifier = Modifier
                                .offset { androidx.compose.ui.unit.IntOffset(indicatorOffsetX.roundToPx(), 0) }
                                .width(tabWidth)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(28.dp))
                                .background(GratiaTheme.colors.maroon.copy(alpha = 0.08f))
                        )

                        // Tab icons and labels
                        Row(modifier = Modifier.fillMaxSize()) {
                            bottomNavItems.forEachIndexed { index, screen ->
                                val selected = index == navIndex
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
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
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.icon,
                                        contentDescription = screen.label,
                                        modifier = Modifier.size(24.dp),
                                        tint = if (selected) GratiaTheme.colors.cherryRed else GratiaTheme.colors.textSecondary
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = screen.label,
                                        fontSize = 10.sp,
                                        fontFamily = Inter,
                                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                                        color = if (selected) GratiaTheme.colors.cherryRed else GratiaTheme.colors.textSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = { fadeIn(animationSpec = tween(200)) },
                exitTransition = { fadeOut(animationSpec = tween(200)) },
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        playerViewModel = playerViewModel,
                        onNavigateToUpload = { navController.navigate("upload") },
                        onNavigateToProfile = { navController.navigate("profile") },
                        onNavigateToSettings = { navController.navigate("settings") }
                    )
                }
                composable(Screen.Search.route) {
                    SearchScreen(playerViewModel = playerViewModel)
                }
                composable(Screen.Radio.route) {
                    RadioScreen(playerViewModel = playerViewModel)
                }
                composable(Screen.Favorites.route) {
                    FavoritesScreen(playerViewModel = playerViewModel)
                }
                composable(Screen.Playlists.route) {
                    PlaylistsScreen()
                }
                composable("upload") {
                    UploadScreen(onNavigateBack = { navController.popBackStack() })
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
                        onNavigateToAbout = { navController.navigate("about") }
                    )
                }
                composable("about") {
                    AboutScreen(
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
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(animationSpec = tween(250))
        ) {
            ExpandedPlayer(
                playerViewModel = playerViewModel,
                onOpenLyrics = {
                    playerViewModel.setExpandedPlayerOpen(false)
                    val songId = currentSong?.id ?: return@ExpandedPlayer
                    navController.navigate("fullLyrics/$songId")
                }
            )
        }

        // Lyrics overlay (from lyrics button in expanded player)
        AnimatedVisibility(
            visible = lyricsOverlayOpen && currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(animationSpec = tween(250))
        ) {
            FullLyricsScreen(
                playerViewModel = playerViewModel,
                songId = currentSong?.id,
                onBack = { playerViewModel.setLyricsOverlayOpen(false) }
            )
        }

        // Splash Transition Overlay
        var showSplash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(800)
            showSplash = false
        }

        AnimatedVisibility(
            visible = showSplash,
            enter = fadeIn(tween(0)), // Starts visible
            exit = fadeOut(tween(400)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GratiaTheme.colors.cotton),
                contentAlignment = Alignment.Center
            ) {
                val logoScale by animateFloatAsState(
                    targetValue = if (showSplash) 1.0f else 0.92f,
                    animationSpec = tween(800, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                    label = "splashScale"
                )
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.gratia.music.R.drawable.gratia_logo),
                    contentDescription = "Gratia Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .scale(logoScale)
                        .clip(RoundedCornerShape(32.dp))
                )
            }
        }
    }
}
