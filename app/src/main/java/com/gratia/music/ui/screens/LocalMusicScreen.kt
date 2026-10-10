package com.gratia.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.data.model.MusicSource
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.model.isLocalTrack
import com.gratia.music.player.PlayerViewModel
import com.gratia.music.player.PlaybackOrigin
import com.gratia.music.ui.LocalBottomPadding
import com.gratia.music.ui.components.AppleListRow
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.components.EmptyStateView
import com.gratia.music.ui.components.GratiaText
import com.gratia.music.ui.components.SongRow
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk

/**
 * A dedicated collection screen for the user's own files.
 *
 * Why this exists as its own screen rather than a filter of the general
 * library: the library mixes sources, and mixing is precisely the bug this is
 * meant to end — a Gratia download is not a file the user put on the phone,
 * and everything in here is the latter. Every list below is built from
 * [MusicSource.LOCAL] rows only; remote metadata cannot leak in by
 * construction.
 *
 * The three tabs group the *same* set of local songs three ways, so Songs,
 * Artists and Albums always agree on what "local music" contains. Tapping a
 * song plays the visible list as the queue — no remote recommendation can
 * enter it, because nothing in this screen ever asks the catalogue for
 * anything.
 */
enum class LocalMusicTab(val label: String) {
    SONGS("Songs"),
    ARTISTS("Artists"),
    ALBUMS("Albums")
}

@Composable
fun LocalMusicScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
) {
    val songRepo = remember { com.gratia.music.data.repository.SongRepository(com.gratia.music.GratiaApp.instance.database.songDao()) }
    val allSongs by songRepo.getAllSongs().collectAsState(initial = emptyList())

    // Local rows only, at the single point of entry to this screen.
    val localSongs = remember(allSongs) { allSongs.filter { it.isLocalTrack() } }

    var selectedTab by rememberSaveable { mutableStateOf(LocalMusicTab.SONGS.name) }
    var query by remember { mutableStateOf("") }

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    // This screen never hits the network, so the filter is done in memory —
    // the set is already paid for and a query here must feel instant.
    val filteredSongs = remember(localSongs, query) {
        if (query.isBlank()) localSongs
        else localSongs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                (it.album?.contains(query, ignoreCase = true) == true)
        }
    }

    // Groups are computed from the filtered set, so searching while on the
    // Artists/Albums tab narrows those too — the three tabs stay the same
    // collection, seen three ways.
    val artists = remember(filteredSongs) {
        filteredSongs
            .flatMap { song -> com.gratia.music.utils.ArtistParser.parseArtists(song.artist).map { it to song } }
            .groupBy({ it.first }, { it.second })
            .map { (name, songs) -> LocalArtist(name, songs) }
            .sortedBy { it.name.lowercase() }
    }
    val albums = remember(filteredSongs) {
        filteredSongs
            .groupBy { it.album ?: "Unknown Album" }
            .map { (name, songs) -> LocalAlbum(name, songs) }
            .sortedBy { it.name.lowercase() }
    }

    val tab = LocalMusicTab.valueOf(selectedTab)
    val bottomInset = LocalBottomPadding.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .statusBarsPadding()
    ) {
        // ── Header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GratiaTheme.colors.accent
                )
            }
            Column {
                GratiaText(
                    text = "Local Music",
                    style = GratiaTheme.typography.title,
                    color = GratiaTheme.colors.textPrimary
                )
                GratiaText(
                    text = "${localSongs.size} song${if (localSongs.size == 1) "" else "s"} on this device",
                    style = GratiaTheme.typography.caption,
                    color = GratiaTheme.colors.textSecondary
                )
            }
        }

        // ── Search this collection ──────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(GratiaTheme.colors.surface),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = GratiaTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 8.dp).size(20.dp)
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    GratiaText(
                        text = "Search this collection",
                        style = GratiaTheme.typography.body,
                        color = GratiaTheme.colors.textSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = GratiaTheme.typography.body.copy(color = GratiaTheme.colors.textPrimary),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.accent)
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear search",
                    tint = GratiaTheme.colors.textSecondary,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(16.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(GratiaTheme.colors.textSecondary.copy(alpha = 0.2f))
                        .clickable { query = "" }
                        .padding(2.dp)
                )
            }
        }

        // ── Tabs ────────────────────────────────────────────────────────
        TabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            contentColor = GratiaTheme.colors.accent,
            divider = {}
        ) {
            LocalMusicTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = { selectedTab = entry.name },
                    text = {
                        Text(
                            text = entry.label,
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            fontWeight = if (tab == entry) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                )
            }
        }

        // ── Content ─────────────────────────────────────────────────────
        when {
            localSongs.isEmpty() -> {
                EmptyStateView(
                    icon = Icons.Default.Folder,
                    headline = "No local music found",
                    description = "Audio files already on this device will appear here."
                )
            }
            tab == LocalMusicTab.SONGS && filteredSongs.isEmpty() -> {
                EmptyStateView(
                    icon = Icons.Default.Search,
                    headline = "Nothing matches \"$query\"",
                    description = "Try a different search."
                )
            }
            tab == LocalMusicTab.SONGS -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.small)
                ) {
                    itemsIndexed(filteredSongs, key = { _, song -> song.id }) { index, song ->
                        SongRow(
                            song = song,
                            index = index,
                            isActive = currentSong?.id == song.id,
                            isPlaying = currentSong?.id == song.id && isPlaying,
                            onClick = {
                                // The queue is exactly this list — the visible
                                // local collection. Nothing else joins it.
                                playerViewModel.playSong(song, filteredSongs, PlaybackOrigin.LOCAL_MUSIC)
                            }
                        )
                    }
                }
            }
            tab == LocalMusicTab.ARTISTS && artists.isEmpty() -> {
                EmptyStateView(
                    icon = Icons.Default.Person,
                    headline = "No artists found",
                    description = "Artists from your local files will appear here."
                )
            }
            tab == LocalMusicTab.ARTISTS -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.mediumSmall)
                ) {
                    items(artists, key = { it.name }) { artist ->
                        val coverArtPath = artist.songs.firstOrNull { it.coverArtPath != null }?.coverArtPath
                        AppleListRow(
                            title = artist.name,
                            subtitle = "${artist.songs.size} song${if (artist.songs.size == 1) "" else "s"}",
                            leadingContent = {
                                ArtistRowImage(artistName = artist.name, fallbackPath = coverArtPath, size = 56.dp)
                            },
                            onClick = { onNavigateToArtist(artist.name) }
                        )
                    }
                }
            }
            tab == LocalMusicTab.ALBUMS && albums.isEmpty() -> {
                EmptyStateView(
                    icon = Icons.Default.Album,
                    headline = "No albums found",
                    description = "Albums from your local files will appear here."
                )
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomInset + GratiaTheme.spacing.heroLarge, top = GratiaTheme.spacing.small),
                    verticalArrangement = Arrangement.spacedBy(GratiaTheme.spacing.mediumSmall)
                ) {
                    items(albums, key = { it.name }) { album ->
                        val coverArtPath = album.songs.firstOrNull { it.coverArtPath != null }?.coverArtPath
                        AppleListRow(
                            title = album.name,
                            subtitle = "${album.songs.size} song${if (album.songs.size == 1) "" else "s"}",
                            leadingContent = {
                                CoverArtImage(coverArtPath = coverArtPath, title = album.name, size = 56.dp, cornerRadius = 6.dp)
                            },
                            onClick = { onNavigateToAlbum(album.name) }
                        )
                    }
                }
            }
        }
    }
}

private data class LocalArtist(val name: String, val songs: List<SongEntity>)
private data class LocalAlbum(val name: String, val songs: List<SongEntity>)
