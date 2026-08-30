package com.gratia.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gratia.music.GratiaApp
import com.gratia.music.data.SettingsDataStore
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.ArtistRepository
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.AddToPlaylistSheet
import com.gratia.music.ui.components.PlayingIndicator
import com.gratia.music.ui.components.SongInfoDialog
import com.gratia.music.ui.components.SongMenuSheet
import com.gratia.music.ui.components.clickableWithScale
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val artistRepo = remember { ArtistRepository(GratiaApp.instance.database.artistDao()) }
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val settingsDataStore = remember { SettingsDataStore(context) }
    val onlineDataEnabled by settingsDataStore.onlineDataEnabledFlow.collectAsState(initial = true)
    
    val artistEntity by artistRepo.getArtistFlow(artistName).collectAsState(initial = null)
    
    var artistSongs by remember { mutableStateOf<List<SongEntity>>(emptyList()) }
    LaunchedEffect(artistName) {
        songRepo.getAllSongs().collect { allSongs ->
            artistSongs = allSongs.filter { it.artist.contains(artistName, ignoreCase = true) }
        }
    }

    // Determine the image to use (custom overrides default)
    val displayImage = artistEntity?.localPicturePath ?: if (onlineDataEnabled) artistEntity?.pictureUrl else null

    var showEditSheet by remember { mutableStateOf(false) }
    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showSongInfo by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()
    val isPlayingState by playerViewModel.isPlaying.collectAsState()
    
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                artistRepo.updateCustomImage(artistName, uri.toString())
            }
        }
        showEditSheet = false
    }

    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEditSheet = false },
            containerColor = GratiaTheme.colors.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = GratiaTheme.colors.textSecondary) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 8.dp)
            ) {
                Text(
                    "Edit Artist Photo",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = GratiaTheme.colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                ListItem(
                    headlineContent = { Text("Change Photo", fontFamily = Inter, color = GratiaTheme.colors.textPrimary) },
                    leadingContent = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = GratiaTheme.colors.accent) },
                    modifier = Modifier.clickable { photoPicker.launch("image/*") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Reset to default", fontFamily = Inter, color = GratiaTheme.colors.error) },
                    leadingContent = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = GratiaTheme.colors.error) },
                    modifier = Modifier.clickable {
                        scope.launch { artistRepo.resetToDefaultImage(artistName) }
                        showEditSheet = false
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    selectedSongForMenu?.let { song ->
        val isFav = favoriteSongIds.contains(song.id)
        SongMenuSheet(
            song = song,
            isFavorite = isFav,
            onDismiss = { selectedSongForMenu = null },
            onPlayNext = { playerViewModel.playNext(song) },
            onAddToQueue = { playerViewModel.addToQueue(song) },
            onAddToPlaylist = {
                showAddToPlaylist = true
            },
            onToggleLike = { playerViewModel.toggleFavorite(song) },
            onGoToAlbum = {},
            onGoToArtist = {},
            onSongInfo = { showSongInfo = true },
            onDelete = { showDeleteConfirm = true }
        )
    }

    if (showSongInfo && selectedSongForMenu != null) {
        SongInfoDialog(
            song = selectedSongForMenu!!,
            onDismiss = { showSongInfo = false }
        )
    }

    if (showAddToPlaylist && selectedSongForMenu != null) {
        AddToPlaylistSheet(
            song = selectedSongForMenu!!,
            onDismiss = { showAddToPlaylist = false }
        )
    }

    if (showDeleteConfirm && selectedSongForMenu != null) {
        val songToDelete = selectedSongForMenu!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "Delete Song",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    color = GratiaTheme.colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete '${songToDelete.title}' from your library?",
                    fontFamily = Inter,
                    color = GratiaTheme.colors.textSecondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        playerViewModel.deleteSong(songToDelete) {
                            try {
                                val uri = Uri.parse(songToDelete.localUri)
                                val file = java.io.File(uri.path ?: "")
                                if (file.exists()) file.delete()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.error)
                ) {
                    Text("Delete", fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = GratiaTheme.colors.textSecondary)
                ) {
                    Text("Cancel", fontFamily = Inter)
                }
            },
            containerColor = GratiaTheme.colors.surface
        )
    }

    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
        ) {
            item {
                // Header Action Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = GratiaTheme.spacing.medium, vertical = GratiaTheme.spacing.medium),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(GratiaTheme.colors.surface)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GratiaTheme.colors.textSecondary)
                    }
                    if (onlineDataEnabled) {
                        IconButton(
                            onClick = { showEditSheet = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(GratiaTheme.colors.surface)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = GratiaTheme.colors.textSecondary)
                        }
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                }

                // Rounded Rect Artwork
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = GratiaTheme.spacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GratiaTheme.colors.surfaceHover),
                        contentAlignment = Alignment.Center
                    ) {
                        if (displayImage != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(displayImage)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Artist Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                tint = GratiaTheme.colors.textSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(96.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(GratiaTheme.spacing.large))
                }
            }

            // ── ARTIST TITLE & SHUFFLE CONTROLS ──
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artistName,
                            fontFamily = SpaceGrotesk,
                            fontWeight = FontWeight.Bold,
                            fontSize = 34.sp,
                            color = GratiaTheme.colors.textPrimary,
                            maxLines = 2,
                            lineHeight = 38.sp,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${artistSongs.size} Songs",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    // Mix/Shuffle Button
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(GratiaTheme.colors.accent)
                            .clickableWithScale {
                                if (artistSongs.isNotEmpty()) {
                                    val shuffled = artistSongs.shuffled()
                                    playerViewModel.playSong(shuffled.first(), shuffled)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Shuffle, 
                            contentDescription = "Shuffle",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── ALBUMS & SONGS ──
            val albums = artistSongs.groupBy { it.album ?: "Singles" }
            
            albums.forEach { (albumName, songs) ->
                item {
                    ArtistAlbumGroup(
                        albumName = albumName,
                        songs = songs,
                        playerViewModel = playerViewModel,
                        isPlayingState = isPlayingState,
                        onSongMenuClick = { song -> selectedSongForMenu = song }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistAlbumGroup(
    albumName: String,
    songs: List<SongEntity>,
    playerViewModel: PlayerViewModel,
    isPlayingState: Boolean,
    onSongMenuClick: (SongEntity) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")
    val context = LocalContext.current
    
    val currentSong by playerViewModel.currentSong.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(GratiaTheme.colors.surface)
            .padding(8.dp)
    ) {
        // Album Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { expanded = !expanded }
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(songs.firstOrNull()?.coverArtPath)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album Cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GratiaTheme.colors.surfaceHover)
            )
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = GratiaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${songs.size} Song${if (songs.size > 1) "s" else ""}",
                    fontFamily = Inter,
                    fontSize = 13.sp,
                    color = GratiaTheme.colors.textSecondary
                )
            }
            
            IconButton(
                onClick = { playerViewModel.playSong(songs.first(), songs) },
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(GratiaTheme.colors.accent.copy(alpha = 0.15f))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = GratiaTheme.colors.accent)
            }
            
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = "Expand",
                tint = GratiaTheme.colors.textSecondary,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }

        // Songs list
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                songs.forEach { song ->
                    val isPlaying = currentSong?.id == song.id
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp, horizontal = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isPlaying) GratiaTheme.colors.surfaceHover else Color.Transparent)
                            .clickable { playerViewModel.playSong(song, songs) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                fontFamily = Inter,
                                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                color = if (isPlaying) GratiaTheme.colors.accent else GratiaTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (song.artist != albumName) {
                                Text(
                                    text = song.artist,
                                    fontFamily = Inter,
                                    fontSize = 12.sp,
                                    color = if (isPlaying) GratiaTheme.colors.accent.copy(alpha = 0.8f) else GratiaTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isPlaying) {
                            Spacer(Modifier.width(8.dp))
                            PlayingIndicator(
                                isPaused = !isPlayingState,
                                color = GratiaTheme.colors.accent
                            )
                            Spacer(Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = { onSongMenuClick(song) },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) GratiaTheme.colors.surface else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = GratiaTheme.colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
