package com.gratia.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.PlaylistEntity
import com.gratia.music.data.model.SongEntity
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.CollageArtwork
import com.gratia.music.ui.components.EmptyStateView
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.GratiaIcon
import com.gratia.music.ui.components.GratiaIconButton
import com.gratia.music.ui.components.GratiaButton
import com.gratia.music.ui.selection.SelectableSongRow
import com.gratia.music.ui.selection.SelectionManager
import com.gratia.music.ui.selection.SelectionToolbar
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.data.CoverColorCache
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.filled.CameraAlt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    val playlistFlow by playlistDao.getPlaylist(playlistId).collectAsState(initial = null)
    val playlistSongs by playlistDao.getSongsForPlaylist(playlistId).collectAsState(initial = emptyList())
    
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val selectionManager = remember { SelectionManager() }
    val selectedIds by selectionManager.selectedIds.collectAsState()
    val isSelectionMode by selectionManager.isSelectionMode.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddMusic by remember { mutableStateOf(false) }

    val playlist = playlistFlow ?: return

    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                val file = File(context.filesDir, "playlist_cover_${playlist.id}.jpg")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    playlistDao.insertPlaylist(playlist.copy(coverArtUri = file.absolutePath, updatedAt = System.currentTimeMillis()))
                } catch (e: Exception) {
                    // Ignore
                }
            }
        }
    }

    // Colors for animated background
    var color1 by remember { mutableStateOf(CoverColorCache.FALLBACK.darkMuted) }
    var color2 by remember { mutableStateOf(CoverColorCache.FALLBACK.dominant) }

    LaunchedEffect(playlistSongs) {
        if (playlistSongs.isNotEmpty()) {
            val colors1 = CoverColorCache.getColors(playlistSongs[0].id, playlistSongs[0].coverArtPath)
            color1 = colors1.darkMuted
            
            if (playlistSongs.size > 1) {
                val colors2 = CoverColorCache.getColors(playlistSongs[1].id, playlistSongs[1].coverArtPath)
                color2 = colors2.darkMuted
            } else {
                color2 = colors1.dominant
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "PlaylistBg")
    val animatedColor1 by infiniteTransition.animateColor(
        initialValue = color1,
        targetValue = color2,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BgColor1"
    )
    val animatedColor2 by infiniteTransition.animateColor(
        initialValue = color2,
        targetValue = color1,
        animationSpec = infiniteRepeatable(
            animation = tween(10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BgColor2"
    )

    val bgColor = GratiaTheme.colors.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            animatedColor1.copy(alpha = 0.5f),
                            bgColor,
                            bgColor
                        )
                    )
                )
            }
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(
                        top = 64.dp,
                        bottom = GratiaTheme.spacing.large,
                        start = GratiaTheme.spacing.large,
                        end = GratiaTheme.spacing.large
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val paths = playlistSongs.take(4).map { it.coverArtPath }
                    Box(
                        modifier = Modifier
                            .shadow(32.dp, GratiaTheme.shapes.extraLarge, spotColor = GratiaTheme.colors.accent)
                            .clickable {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                coverPicker.launch(arrayOf("image/*"))
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (playlist.coverArtUri != null && File(playlist.coverArtUri).exists()) {
                            AsyncImage(
                                model = File(playlist.coverArtUri),
                                contentDescription = "Playlist Cover",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(24.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(
                                                animatedColor1,
                                                animatedColor2
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueueMusic,
                                    contentDescription = "Playlist",
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.size(80.dp)
                                )
                            }
                        }
                        
                        // Edit overlay icon
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.Black.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = "Change Cover",
                                tint = Color.White.copy(alpha = 0.8f),
                                modifier = Modifier.size(48.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(GratiaTheme.spacing.large))
                    
                    GratiaText(
                        text = playlist.name,
                        style = GratiaTheme.typography.largeTitle,
                        color = GratiaTheme.colors.textPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    
                    val totalDurationMs = playlistSongs.sumOf { it.durationMs }
                    val minutes = totalDurationMs / (1000 * 60)
                    GratiaText(
                        text = "${playlistSongs.size} songs • $minutes min",
                        style = GratiaTheme.typography.caption,
                        color = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.fillMaxWidth().padding(top = GratiaTheme.spacing.small),
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(GratiaTheme.spacing.large))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.medium)
                    ) {
                        GratiaButton(
                            text = "Play",
                            icon = Icons.Default.PlayArrow,
                            onClick = { if (playlistSongs.isNotEmpty()) playerViewModel.playSong(playlistSongs.first(), playlistSongs) },
                            modifier = Modifier.weight(1f),
                            backgroundColor = GratiaTheme.colors.surface,
                            contentColor = GratiaTheme.colors.textPrimary
                        )
                        
                        GratiaButton(
                            text = "Shuffle",
                            icon = Icons.Default.Shuffle,
                            onClick = { 
                                if (playlistSongs.isNotEmpty()) {
                                    playerViewModel.toggleShuffle()
                                    playerViewModel.playSong(playlistSongs.random(), playlistSongs)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            backgroundColor = GratiaTheme.colors.surface,
                            contentColor = GratiaTheme.colors.textPrimary
                        )
                    }
                }
            }

            if (playlistSongs.isEmpty()) {
                item {
                    EmptyStateView(
                        icon = Icons.Default.QueueMusic,
                        headline = "Empty Playlist",
                        description = "Add some songs to this playlist to start listening.",
                        actionLabel = "Add Music",
                        onActionClick = { showAddMusic = true }
                    )
                }
            } else {
                itemsIndexed(playlistSongs, key = { _, s -> s.id }) { index, song ->
                    SelectableSongRow(
                        song = song,
                        index = index,
                        isActive = currentSong?.id == song.id,
                        isPlaying = currentSong?.id == song.id && isPlaying,
                        isSelectionMode = isSelectionMode,
                        isSelected = selectedIds.contains(song.id),
                        onPlay = { playerViewModel.playSong(song, playlistSongs) },
                        onLongPress = { selectionManager.startSelection(song.id) },
                        onToggleSelection = { selectionManager.toggle(song.id) },
                        modifier = Modifier.padding(horizontal = GratiaTheme.spacing.large)
                    )
                }
            }
        }

        // Top Bar Overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = GratiaTheme.spacing.medium, vertical = GratiaTheme.spacing.medium),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GratiaIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.surface),
                tint = GratiaTheme.colors.textSecondary
            )
            
            Box {
                GratiaIconButton(
                    icon = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(GratiaTheme.colors.surface),
                    tint = GratiaTheme.colors.textSecondary
                )
                
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(GratiaTheme.colors.surface)
                ) {
                    DropdownMenuItem(
                        text = { GratiaText("Add Music", style = GratiaTheme.typography.body, color = GratiaTheme.colors.textPrimary) },
                        onClick = { showMenu = false; showAddMusic = true }
                    )
                    DropdownMenuItem(
                        text = { GratiaText("Rename Playlist", style = GratiaTheme.typography.body, color = GratiaTheme.colors.textPrimary) },
                        onClick = { showMenu = false; showRenameDialog = true }
                    )
                    DropdownMenuItem(
                        text = { GratiaText("Delete Playlist", style = GratiaTheme.typography.body, color = GratiaTheme.colors.error) },
                        onClick = { showMenu = false; showDeleteConfirm = true }
                    )
                }
            }
        }
        
        // Selection Toolbar
        AnimatedVisibility(
            visible = isSelectionMode,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = spring(
                    dampingRatio = 0.8f,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it }
            ) + fadeOut()
        ) {
            SelectionToolbar(
                selectedCount = selectedIds.size,
                totalCount = playlistSongs.size,
                onAddToQueue = {
                    val selectedSongs = playlistSongs.filter { selectedIds.contains(it.id) }
                    selectedSongs.forEach { playerViewModel.addToQueue(it) }
                    selectionManager.clearSelection()
                },
                onAddToPlaylist = {
                    selectionManager.clearSelection()
                },
                onDelete = {
                    val selectedSongs = playlistSongs.filter { selectedIds.contains(it.id) }
                    scope.launch {
                        selectedSongs.forEach { song ->
                            playlistDao.removeSongFromPlaylist(com.gratia.music.data.model.PlaylistSongCrossRef(playlistId, song.id))
                        }
                    }
                    selectionManager.clearSelection()
                },
                onSelectAll = {
                    if (selectedIds.size == playlistSongs.size) {
                        selectionManager.clearSelection()
                    } else {
                        selectionManager.selectAll(playlistSongs.map { it.id })
                    }
                },
                onClose = { selectionManager.clearSelection() }
            )
        }
    }

    if (showAddMusic) {
        com.gratia.music.ui.components.AddMusicSheet(
            playlistId = playlist.id,
            onDismiss = { showAddMusic = false }
        )
    }
}
