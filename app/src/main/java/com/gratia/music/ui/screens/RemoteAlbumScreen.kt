package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.provider.RemoteAlbum
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.components.GratiaLoadingState
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.SongMenuSheet
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.components.bounceClick
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteAlbumScreen(
    browseId: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var album by remember { mutableStateOf<RemoteAlbum?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()

    LaunchedEffect(browseId) {
        isLoading = true
        error = null
        try {
            val res = withContext(Dispatchers.IO) {
                GratiaApp.instance.providerManager.youtubeMusicProvider.getAlbum(browseId)
            }
            if (res != null) {
                album = res
            } else {
                error = "Couldn't load album details."
            }
        } catch (e: Exception) {
            error = "Failed to load album: ${e.message}"
        } finally {
            isLoading = false
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
            onToggleLike = { playerViewModel.toggleFavorite(song) }
        )
    }

    Scaffold(
        containerColor = GratiaTheme.colors.background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = GratiaTheme.colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        GratiaLoadingState(message = "Loading album...")
                    }
                }
                error != null || album == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GratiaText(
                                text = error ?: "Album not found",
                                style = GratiaTheme.typography.body,
                                color = GratiaTheme.colors.textSecondary
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = onBack,
                                colors = ButtonDefaults.buttonColors(containerColor = GratiaTheme.colors.accent)
                            ) {
                                Text("Go Back", color = Color.White)
                            }
                        }
                    }
                }
                else -> {
                    val currentAlbum = album!!
                    val trackEntities = remember(currentAlbum) {
                        currentAlbum.tracks.map { RemoteTrackMapper.toSongEntity(it) }
                    }
                    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge)
                    ) {
                        // Album Header
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(currentAlbum.artworkUrl)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = currentAlbum.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(200.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(GratiaTheme.colors.surface)
                                )

                                Spacer(Modifier.height(16.dp))

                                Text(
                                    text = currentAlbum.title,
                                    fontFamily = SpaceGrotesk,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp,
                                    color = GratiaTheme.colors.textPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                Spacer(Modifier.height(6.dp))

                                val artistName = currentAlbum.artists.joinToString(", ") { it.name }.ifEmpty { "YouTube Music" }
                                Text(
                                    text = artistName,
                                    fontFamily = Inter,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp,
                                    color = GratiaTheme.colors.accent,
                                    modifier = Modifier.bounceClick {
                                        val artistId = currentAlbum.artists.firstOrNull()?.id
                                        if (!artistId.isNullOrBlank()) {
                                            onNavigateToArtist(artistId)
                                        }
                                    }
                                )

                                val infoLine = listOfNotNull(
                                    currentAlbum.year,
                                    currentAlbum.trackCount?.let { "$it tracks" },
                                    currentAlbum.durationText
                                ).joinToString(" • ")

                                if (infoLine.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = infoLine,
                                        fontFamily = Inter,
                                        fontSize = 13.sp,
                                        color = GratiaTheme.colors.textSecondary
                                    )
                                }

                                Spacer(Modifier.height(20.dp))

                                // Play & Shuffle Action Buttons
                                if (trackEntities.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                playerViewModel.playSong(trackEntities.first(), trackEntities)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = GratiaTheme.colors.accent)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Play", color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                val shuffled = trackEntities.shuffled()
                                                playerViewModel.playSong(shuffled.first(), shuffled)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = GratiaTheme.colors.textPrimary)
                                        ) {
                                            Icon(Icons.Default.Shuffle, contentDescription = null, tint = GratiaTheme.colors.textPrimary)
                                            Spacer(Modifier.width(8.dp))
                                            Text("Shuffle", color = GratiaTheme.colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }

                                Spacer(Modifier.height(16.dp))
                            }
                        }

                        // Tracks
                        itemsIndexed(trackEntities, key = { _, s -> s.id }) { index, song ->
                            SongRow(
                                song = song,
                                index = index,
                                isActive = currentSong?.id == song.id,
                                isPlaying = currentSong?.id == song.id && isPlaying,
                                onClick = { playerViewModel.playSong(song, trackEntities) },
                                onMoreClick = { selectedSongForMenu = song },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
