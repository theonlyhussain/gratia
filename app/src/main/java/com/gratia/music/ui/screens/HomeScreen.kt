package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.ui.components.ArtistFallback
import com.gratia.music.ui.components.CollageArtwork
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.Header
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.clickableWithScale
import com.gratia.music.ui.theme.GratiaTheme

@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateToUpload: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {}
) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    val recentlyPlayed by songRepo.getRecentlyPlayed(10).collectAsState(initial = emptyList())
    val mostPlayed by songRepo.getMostPlayed(10).collectAsState(initial = emptyList())
    val lastAdded by songRepo.getLastAdded(8).collectAsState(initial = emptyList())
    val recentArtists by songRepo.getRecentArtists(10).collectAsState(initial = emptyList())

    // Load profile from Room database
    val profileDao = remember { GratiaApp.instance.database.userProfileDao() }
    val profileFlow by profileDao.getProfile().collectAsState(initial = null)
    
    val displayName = profileFlow?.displayName ?: "Music Lover"
    val avatarPath = profileFlow?.avatarPath

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
    ) {
        Header(
            displayName = displayName,
            avatarPath = avatarPath,
            isDark = isDark,
            onToggleTheme = onToggleTheme,
            onNavigateToProfile = onNavigateToProfile,
            onNavigateToSettings = onNavigateToSettings
        )

        val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
        val collectionDao = remember { GratiaApp.instance.database.collectionDao() }
        val playlists by playlistDao.getAllPlaylists().collectAsState(initial = emptyList())
        val collections by collectionDao.getAllCollections().collectAsState(initial = emptyList())
        val favorites by songRepo.getFavorites().collectAsState(initial = emptyList())

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = GratiaTheme.spacing.heroLarge) // space for mini player + nav
        ) {
            if (!isDark) {
                // LIGHT MODE: Continue Listening, Newly Added, Most Played, Playlists, Collections
                if (recentlyPlayed.isNotEmpty()) {
                    item { SectionTitle("Continue Listening") }
                    item { FeaturedCarousel(recentlyPlayed, playerViewModel) }
                }
                if (lastAdded.isNotEmpty()) {
                    item { SectionTitle("Newly Added") }
                    item { FeaturedCarousel(lastAdded, playerViewModel) } // Spec says horizontal cards
                }
                if (mostPlayed.isNotEmpty()) {
                    item { SectionTitle("Most Played") }
                    item { SongList(mostPlayed, playerViewModel) } // Spec says vertical
                }
                if (playlists.isNotEmpty()) {
                    item { SectionTitle("Playlists") }
                    item { PlaylistsRow(playlists) }
                }
                if (collections.isNotEmpty()) {
                    item { SectionTitle("Collections") }
                    item { CollectionsRow(collections) }
                }
            } else {
                // DARK MODE: Top Artists, On Repeat, Recently Played, Favorites, Playlists, Collections
                if (recentArtists.isNotEmpty()) {
                    item { SectionTitle("Top Artists") }
                    item { TopArtistsRow(recentArtists) }
                }
                if (mostPlayed.isNotEmpty()) {
                    item { SectionTitle("On Repeat") }
                    item { FeaturedCarousel(mostPlayed, playerViewModel) } // Spec says horizontal
                }
                if (recentlyPlayed.isNotEmpty()) {
                    item { SectionTitle("Recently Played") }
                    item { SongList(recentlyPlayed, playerViewModel) } // Spec says vertical
                }
                if (favorites.isNotEmpty()) {
                    item { SectionTitle("Favorites") }
                    item { FeaturedCarousel(favorites, playerViewModel) } // Spec says horizontal
                }
                if (playlists.isNotEmpty()) {
                    item { SectionTitle("Playlists") }
                    item { PlaylistsRow(playlists) }
                }
                if (collections.isNotEmpty()) {
                    item { SectionTitle("Collections") }
                    item { CollectionsRow(collections) }
                }
            }
        }
    }
}

@Composable
fun PlaylistsRow(playlists: List<com.gratia.music.data.model.PlaylistEntity>) {
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.medium),
        contentPadding = PaddingValues(horizontal = GratiaTheme.spacing.large)
    ) {
        items(playlists) { playlist ->
            val songs by playlistDao.getSongsForPlaylist(playlist.id).collectAsState(initial = emptyList())
            val paths = songs.take(4).map { it.coverArtPath }
            
            Column(
                modifier = Modifier
                    .width(140.dp)
                    .clickableWithScale {}
            ) {
                CollageArtwork(
                    paths = paths,
                    size = 140.dp,
                    cornerRadius = 20.dp
                )
                Spacer(Modifier.height(GratiaTheme.spacing.small))
                GratiaText(
                    text = playlist.name,
                    style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = GratiaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CollectionsRow(collections: List<com.gratia.music.data.model.CollectionEntity>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.medium),
        contentPadding = PaddingValues(horizontal = GratiaTheme.spacing.large)
    ) {
        items(collections) { collection ->
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(80.dp)
                    .clip(GratiaTheme.shapes.large)
                    .background(GratiaTheme.colors.surface)
                    .clickableWithScale {},
                contentAlignment = Alignment.Center
            ) {
                GratiaText(
                    text = collection.name,
                    style = GratiaTheme.typography.section,
                    color = GratiaTheme.colors.textPrimary
                )
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) {
    GratiaText(
        text = title,
        style = GratiaTheme.typography.section,
        color = GratiaTheme.colors.textPrimary,
        modifier = Modifier.padding(
            horizontal = GratiaTheme.spacing.large,
            vertical = GratiaTheme.spacing.medium
        )
    )
}

@Composable
fun FeaturedCarousel(songs: List<SongEntity>, playerViewModel: PlayerViewModel) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.medium),
        contentPadding = PaddingValues(horizontal = GratiaTheme.spacing.large)
    ) {
        items(songs) { song ->
            Column(
                modifier = Modifier
                    .width(160.dp)
                    .clickableWithScale { playerViewModel.playSong(song, songs) }
            ) {
                CoverArtImage(
                    coverArtPath = song.coverArtPath,
                    title = song.title,
                    artist = song.artist,
                    size = 160.dp,
                    cornerRadius = 24.dp, // GDL shapes.extraLarge
                    fontSize = GratiaTheme.typography.display.fontSize
                )
                Spacer(Modifier.height(GratiaTheme.spacing.mediumSmall))
                GratiaText(
                    text = song.title,
                    style = GratiaTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                    color = GratiaTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                GratiaText(
                    text = song.artist,
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SongList(songs: List<SongEntity>, playerViewModel: PlayerViewModel, showPlayCount: Boolean = false) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    Column(
        verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.small)
    ) {
        songs.forEachIndexed { index, song ->
            com.gratia.music.ui.components.SongRow(
                song = song,
                index = index,
                isActive = currentSong?.id == song.id,
                isPlaying = currentSong?.id == song.id && isPlaying,
                onClick = { playerViewModel.playSong(song, songs) },
                modifier = Modifier.padding(horizontal = GratiaTheme.spacing.large)
            )
        }
    }
}

@Composable
fun TopArtistsRow(artists: List<String>) {
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.medium),
        contentPadding = PaddingValues(horizontal = GratiaTheme.spacing.large)
    ) {
        items(artists) { artistName ->
            val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())
            val artistSongs = remember(allSongs, artistName) { allSongs.filter { it.artist == artistName } }
            val coverArtPath = artistSongs.firstOrNull { it.coverArtPath != null }?.coverArtPath ?: artistSongs.firstOrNull()?.coverArtPath
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(80.dp)
                    .clickableWithScale {}
            ) {
                if (coverArtPath != null) {
                    CoverArtImage(
                        coverArtPath = coverArtPath,
                        title = artistName,
                        artist = artistName,
                        size = 80.dp,
                        cornerRadius = 40.dp, // fully rounded
                        fontSize = GratiaTheme.typography.title.fontSize
                    )
                } else {
                    ArtistFallback(
                        artistName = artistName,
                        size = 80.dp,
                        fontSize = GratiaTheme.typography.title.fontSize
                    )
                }
                Spacer(Modifier.height(GratiaTheme.spacing.small))
                GratiaText(
                    text = artistName,
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
