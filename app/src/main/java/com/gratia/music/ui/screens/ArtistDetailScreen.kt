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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
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
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.ArtistRepository
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.ScallopedStarShape
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
    
    val artistEntity by artistRepo.getArtistFlow(artistName).collectAsState(initial = null)
    
    var artistSongs by remember { mutableStateOf<List<SongEntity>>(emptyList()) }
    LaunchedEffect(artistName) {
        songRepo.getAllSongs().collect { allSongs ->
            artistSongs = allSongs.filter { it.artist.contains(artistName, ignoreCase = true) }
        }
    }

    // Determine the image to use (custom overrides default)
    val displayImage = artistEntity?.localPicturePath ?: artistEntity?.pictureUrl

    var showEditSheet by remember { mutableStateOf(false) }
    
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
            dragHandle = { BottomSheetDefaults.DragHandle(color = GratiaTheme.colors.textSecondary) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp, top = 8.dp)
            ) {
                Text(
                    "Edit Artist Photo",
                    style = GratiaTheme.typography.title,
                    color = GratiaTheme.colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                ListItem(
                    headlineContent = { Text("Change Photo", color = GratiaTheme.colors.textPrimary) },
                    modifier = Modifier.clickable { photoPicker.launch("image/*") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Reset to default", color = GratiaTheme.colors.error) },
                    modifier = Modifier.clickable {
                        scope.launch { artistRepo.resetToDefaultImage(artistName) }
                        showEditSheet = false
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        // Background Blur
        if (displayImage != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(displayImage)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .blur(60.dp)
            )
            // Gradient overlay to blend blur into background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                GratiaTheme.colors.background.copy(alpha = 0.3f),
                                GratiaTheme.colors.background.copy(alpha = 0.8f),
                                GratiaTheme.colors.background
                            )
                        )
                    )
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
        ) {
            item {
                Spacer(Modifier.statusBarsPadding())
                
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    IconButton(
                        onClick = { showEditSheet = true },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.White)
                    }
                }
            }

            // ── HERO ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(16.dp))
                    
                    // Circular Hero Image
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(displayImage)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Artist Image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(240.dp)
                            .clip(CircleShape)
                            .background(GratiaTheme.colors.surfaceHover)
                    )

                    Spacer(Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artistName,
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 36.sp
                                ),
                                color = GratiaTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${artistSongs.size} Songs",
                                style = MaterialTheme.typography.titleMedium,
                                color = GratiaTheme.colors.textSecondary
                            )
                        }

                        // M3 Scalloped Star FAB
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(ScallopedStarShape())
                                .background(MaterialTheme.colorScheme.primary) // Fix: use primary for full solid global accent
                                .clickable {
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
                                tint = MaterialTheme.colorScheme.primary, // The solid accent color
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                }
            }

            // ── SONGS ──
            // Group songs by album
            val albums = artistSongs.groupBy { it.album ?: "Singles" }
            
            albums.forEach { (albumName, songs) ->
                item {
                    ArtistAlbumGroup(
                        albumName = albumName,
                        songs = songs,
                        playerViewModel = playerViewModel
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
    playerViewModel: PlayerViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "chevron")
    val context = LocalContext.current
    
    val currentSong by playerViewModel.currentSong.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(GratiaTheme.colors.surfaceHover)
            )
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = GratiaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${songs.size} Song${if (songs.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GratiaTheme.colors.textSecondary
                )
            }
            
            IconButton(
                onClick = { playerViewModel.playSong(songs.first(), songs) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.primary)
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
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isPlaying) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { playerViewModel.playSong(song, songs) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = if (isPlaying) MaterialTheme.colorScheme.primary else GratiaTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (song.artist != albumName) {
                                Text(
                                    text = song.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else GratiaTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        
                        IconButton(onClick = { /* TODO More Actions */ }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = if (isPlaying) MaterialTheme.colorScheme.primary else GratiaTheme.colors.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}
