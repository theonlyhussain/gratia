package com.gratia.music.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.ArtistRepository
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.*
import com.gratia.music.ui.theme.GratiaTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val artistRepo = remember { ArtistRepository(GratiaApp.instance.database.artistDao()) }

    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
    val artistSongs = remember(allSongs, artistName) {
        allSongs.filter { com.gratia.music.utils.ArtistParser.parseArtists(it.artist).contains(artistName) }
            .sortedBy { it.title }
    }

    // Group songs by album
    data class AlbumGroup(
        val albumName: String,
        val year: String?,
        val coverArtPath: String?,
        val songs: List<SongEntity>
    )

    val albumGroups = remember(artistSongs) {
        artistSongs.groupBy { it.album ?: "Singles" }
            .map { (albumName, songs) ->
                AlbumGroup(
                    albumName = albumName,
                    year = songs.firstOrNull()?.releaseDate?.take(4),
                    coverArtPath = songs.firstOrNull()?.coverArtPath,
                    songs = songs.sortedBy { it.trackNumber ?: 0 }
                )
            }
            .sortedBy { it.albumName }
    }

    // Track which albums are expanded
    val expandedAlbums = remember { mutableStateMapOf<String, Boolean>() }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val artistEntity by artistRepo.getArtistFlow(artistName).collectAsState(initial = null)

    // Resolve display image: Custom local path overrides Deezer URL
    val displayImagePath = artistEntity?.localPicturePath ?: artistEntity?.pictureUrl

    var isEditing by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                val timestamp = System.currentTimeMillis()
                val file = File(context.filesDir, "artist_img_${artistName}_$timestamp.jpg")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    val currentDbImage = artistEntity?.localPicturePath
                    if (currentDbImage != null && File(currentDbImage).exists()) {
                        File(currentDbImage).delete()
                    }
                    artistRepo.updateCustomImage(artistName, file.absolutePath)
                } catch (_: Exception) {}
            }
        }
        showEditSheet = false
        isEditing = false
    }

    // Bottom sheet for editing artist photo
    if (showEditSheet) {
        ModalBottomSheet(
            onDismissRequest = {
                showEditSheet = false
                isEditing = false
            },
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
                    modifier = Modifier.clickable { photoPicker.launch(arrayOf("image/*")) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Fetch Artist Image", color = GratiaTheme.colors.textPrimary) },
                    modifier = Modifier.clickable {
                        scope.launch { artistRepo.resetToDefaultImage(artistName) }
                        showEditSheet = false
                        isEditing = false
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge)
        ) {
            // ── HERO IMAGE HEADER ──
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    // Full-bleed artist image
                    if (displayImagePath != null) {
                        val model = if (displayImagePath.startsWith("/")) File(displayImagePath) else displayImagePath
                        coil.compose.AsyncImage(
                            model = model,
                            contentDescription = artistName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback: gradient placeholder with initials
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            GratiaTheme.colors.accent.copy(alpha = 0.4f),
                                            GratiaTheme.colors.surfaceHover
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = artistName.take(2).uppercase(),
                                style = GratiaTheme.typography.display.copy(fontSize = 72.sp),
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Scrim gradient at bottom for text readability
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f)
                                    )
                                )
                            )
                    )

                    // Back button (top-left)
                    GratiaIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 8.dp)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                            .align(Alignment.TopStart),
                        tint = Color.White
                    )

                    // Edit button (top-right)
                    GratiaEditAffordance(
                        isEditing = isEditing,
                        onToggle = {
                            isEditing = !isEditing
                            if (isEditing) {
                                showEditSheet = true
                            }
                        },
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(end = 16.dp, top = 8.dp)
                            .align(Alignment.TopEnd)
                    )

                    // Artist name + song count overlaid at bottom-left
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 20.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = artistName,
                            style = GratiaTheme.typography.largeTitle.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "${artistSongs.size} Songs",
                            style = GratiaTheme.typography.caption,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // Shuffle button at bottom-right
                    IconButton(
                        onClick = {
                            if (artistSongs.isNotEmpty()) {
                                val shuffled = artistSongs.shuffled()
                                playerViewModel.playSong(shuffled.first(), shuffled)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 12.dp)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(GratiaTheme.colors.accent.copy(alpha = 0.85f))
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Shuffle Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // ── ALBUM GROUPS ──
            albumGroups.forEach { albumGroup ->
                val isExpanded = expandedAlbums[albumGroup.albumName] ?: false

                // Album header row
                item(key = "album_header_${albumGroup.albumName}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                expandedAlbums[albumGroup.albumName] = !isExpanded
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Album cover art
                        CoverArtImage(
                            coverArtPath = albumGroup.coverArtPath,
                            title = albumGroup.albumName,
                            size = 56.dp,
                            cornerRadius = 8.dp
                        )

                        Spacer(Modifier.width(16.dp))

                        // Album title + meta
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = albumGroup.albumName,
                                style = GratiaTheme.typography.section,
                                color = GratiaTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val meta = buildString {
                                albumGroup.year?.let { append("$it · ") }
                                append("${albumGroup.songs.size} Song${if (albumGroup.songs.size != 1) "s" else ""}")
                            }
                            Text(
                                text = meta,
                                style = GratiaTheme.typography.caption,
                                color = GratiaTheme.colors.textSecondary
                            )
                        }

                        // Play album button
                        IconButton(
                            onClick = {
                                playerViewModel.playSong(albumGroup.songs.first(), albumGroup.songs)
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play ${albumGroup.albumName}",
                                tint = GratiaTheme.colors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Expand/collapse chevron
                        IconButton(
                            onClick = { expandedAlbums[albumGroup.albumName] = !isExpanded },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = GratiaTheme.colors.textSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Songs in this album (shown when expanded)
                if (isExpanded) {
                    items(
                        items = albumGroup.songs,
                        key = { song -> song.id }
                    ) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { playerViewModel.playSong(song, albumGroup.songs) }
                                .padding(start = 92.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = GratiaTheme.typography.body,
                                    color = if (currentSong?.id == song.id) GratiaTheme.colors.accent
                                            else GratiaTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = artistName,
                                    style = GratiaTheme.typography.caption,
                                    color = GratiaTheme.colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { /* show song menu */ },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = GratiaTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
