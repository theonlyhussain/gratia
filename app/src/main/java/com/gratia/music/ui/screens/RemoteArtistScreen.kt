package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.gratia.music.provider.RemoteArtist
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.components.AppleSectionHeader
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
fun RemoteArtistScreen(
    channelId: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var artist by remember { mutableStateOf<RemoteArtist?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedSongForMenu by remember { mutableStateOf<SongEntity?>(null) }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val favoriteSongIds by playerViewModel.favoriteSongIds.collectAsState()

    LaunchedEffect(channelId) {
        isLoading = true
        error = null
        try {
            val res = withContext(Dispatchers.IO) {
                GratiaApp.instance.providerManager.youtubeMusicProvider.getArtist(channelId)
            }
            if (res != null) {
                artist = res
            } else {
                error = "Couldn't load artist details."
            }
        } catch (e: Exception) {
            error = "Failed to load artist: ${e.message}"
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
                        GratiaLoadingState(message = "Loading artist...")
                    }
                }
                error != null || artist == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GratiaText(
                                text = error ?: "Artist not found",
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
                    val currentArtist = artist!!
                    val topSongEntities = remember(currentArtist) {
                        currentArtist.topSongs.map { RemoteTrackMapper.toSongEntity(it) }
                    }
                    val bottomInset = com.gratia.music.ui.LocalBottomPadding.current

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge)
                    ) {
                        // Hero Artist Header
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(280.dp)
                            ) {
                                if (!currentArtist.artworkUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(currentArtist.artworkUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = currentArtist.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(GratiaTheme.colors.surface)
                                    )
                                }

                                // Dark gradient scrim for readability
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    GratiaTheme.colors.background.copy(alpha = 0.6f),
                                                    GratiaTheme.colors.background
                                                )
                                            )
                                        )
                                )

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(horizontal = 24.dp, vertical = 16.dp)
                                ) {
                                    Text(
                                        text = currentArtist.name,
                                        fontFamily = SpaceGrotesk,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 32.sp,
                                        color = GratiaTheme.colors.textPrimary,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    if (!currentArtist.subscribers.isNullOrBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "${currentArtist.subscribers} on YouTube Music",
                                            fontFamily = Inter,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = GratiaTheme.colors.accent
                                        )
                                    }
                                }
                            }
                        }

                        // Play & Shuffle Action Buttons
                        if (topSongEntities.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            playerViewModel.playSong(topSongEntities.first(), topSongEntities)
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
                                            val shuffled = topSongEntities.shuffled()
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
                                Spacer(Modifier.height(12.dp))
                            }
                        }

                        // Description
                        if (!currentArtist.description.isNullOrBlank()) {
                            item {
                                Text(
                                    text = currentArtist.description,
                                    fontFamily = Inter,
                                    fontSize = 14.sp,
                                    color = GratiaTheme.colors.textSecondary,
                                    lineHeight = 20.sp,
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }

                        // Top Songs
                        if (topSongEntities.isNotEmpty()) {
                            item {
                                AppleSectionHeader(title = "Top Songs")
                            }

                            itemsIndexed(topSongEntities, key = { _, s -> s.id }) { index, song ->
                                SongRow(
                                    song = song,
                                    index = index,
                                    isActive = currentSong?.id == song.id,
                                    isPlaying = currentSong?.id == song.id && isPlaying,
                                    onClick = { playerViewModel.playSong(song, topSongEntities) },
                                    onMoreClick = { selectedSongForMenu = song },
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }

                        // Albums
                        if (currentArtist.albums.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(16.dp))
                                AppleSectionHeader(title = "Albums")
                                Spacer(Modifier.height(8.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(currentArtist.albums) { alb ->
                                        Column(
                                            modifier = Modifier
                                                .width(140.dp)
                                                .bounceClick { onNavigateToAlbum(alb.id) }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(alb.artworkUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = alb.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(140.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(GratiaTheme.colors.surface)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = alb.title,
                                                fontFamily = Inter,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = GratiaTheme.colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!alb.year.isNullOrBlank()) {
                                                Text(
                                                    text = alb.year,
                                                    fontFamily = Inter,
                                                    fontSize = 12.sp,
                                                    color = GratiaTheme.colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Singles & EPs
                        if (currentArtist.singles.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(16.dp))
                                AppleSectionHeader(title = "Singles & EPs")
                                Spacer(Modifier.height(8.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(currentArtist.singles) { single ->
                                        Column(
                                            modifier = Modifier
                                                .width(140.dp)
                                                .bounceClick { onNavigateToAlbum(single.id) }
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(single.artworkUrl)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = single.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(140.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(GratiaTheme.colors.surface)
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = single.title,
                                                fontFamily = Inter,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                                color = GratiaTheme.colors.textPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!single.year.isNullOrBlank()) {
                                                Text(
                                                    text = single.year,
                                                    fontFamily = Inter,
                                                    fontSize = 12.sp,
                                                    color = GratiaTheme.colors.textSecondary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Related Artists
                        if (currentArtist.relatedArtists.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(16.dp))
                                AppleSectionHeader(title = "Fans Also Like")
                                Spacer(Modifier.height(8.dp))

                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    items(currentArtist.relatedArtists) { rel ->
                                        Column(
                                            modifier = Modifier
                                                .width(100.dp)
                                                .bounceClick {
                                                    if (!rel.id.isNullOrBlank()) {
                                                        onNavigateToArtist(rel.id)
                                                    }
                                                },
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(80.dp)
                                                    .clip(CircleShape)
                                                    .background(GratiaTheme.colors.surface),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = rel.name.take(1),
                                                    fontFamily = SpaceGrotesk,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 28.sp,
                                                    color = GratiaTheme.colors.textSecondary
                                                )
                                            }
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = rel.name,
                                                fontFamily = Inter,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                                color = GratiaTheme.colors.textPrimary,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
