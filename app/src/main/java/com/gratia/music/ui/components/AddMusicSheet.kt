package com.gratia.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.model.PlaylistSongCrossRef
import com.gratia.music.data.model.playsFromDevice
import com.gratia.music.provider.RemoteTrackMapper
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Add Music — the sheet that fills a playlist (playlist spec 17–29).
 *
 * Two origins, clearly separated: what is already on the device, and the
 * central catalogue search every other screen uses — no second search stack.
 * Each row adds itself on tap and shows its own state: [+] addable, [✓]
 * already in this playlist, idempotent either way. A remote result is added
 * with its full remote identity (video id, artist/album ids, artwork), so the
 * playlist row later plays and resolves like any other track.
 *
 * The sheet is full height and runs in its own window: the navigation bar and
 * mini player sit underneath it, not through it (playlist spec 23).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMusicSheet(
    playlistId: String,
    playlistName: String? = null,
    onDismiss: () -> Unit
) {
    val songDao = remember { GratiaApp.instance.database.songDao() }
    val playlistDao = remember { GratiaApp.instance.database.playlistDao() }
    val allSongs by songDao.getAllSongs().collectAsState(initial = emptyList())
    val playlistSongs by playlistDao.getSongsForPlaylist(playlistId).collectAsState(initial = emptyList())
    val playlist by playlistDao.getPlaylist(playlistId).collectAsState(initial = null)
    val isOnline by com.gratia.music.data.network.NetworkMonitor.isOnline.collectAsState(initial = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var query by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf<List<com.gratia.music.data.model.SongEntity>>(emptyList()) }
    var searchFailed by remember { mutableStateOf(false) }

    // Already-in-playlist state, plus an optimistic overlay so the tap's [✓]
    // is instant even before Room re-emits (playlist spec 21).
    val addedIds = remember(playlistSongs) { playlistSongs.map { it.id }.toSet() }
    val pendingAdds = remember { mutableStateListOf<String>() }
    var addedThisSession by remember { mutableIntStateOf(0) }

    fun isAdded(songId: String) = songId in addedIds || songId in pendingAdds

    // The add is idempotent: the guard is checked again at write time, and the
    // cross-ref itself is keyed on (playlistId, songId).
    fun addToPlaylist(song: com.gratia.music.data.model.SongEntity) {
        if (isAdded(song.id) || song.id in pendingAdds) return
        pendingAdds.add(song.id)
        scope.launch(Dispatchers.IO) {
            try {
                // A remote result has no songs row until something persists it
                // — the cross-ref is foreign-keyed to one. Same rule as
                // Add to Playlist elsewhere in the app.
                if (playlistDao.getSongByIdOnce(song.id) == null) {
                    playlistDao.insertPlaylistSong(song)
                }
                playlistDao.addSongToPlaylist(
                    PlaylistSongCrossRef(
                        playlistId = playlistId,
                        songId = song.id,
                        addedAt = System.currentTimeMillis(),
                        sortOrder = Int.MAX_VALUE, // appended; ordering falls back to addedAt
                    )
                )
                playlist?.let { playlistDao.insertPlaylist(it.copy(updatedAt = System.currentTimeMillis())) }
                withContext(Dispatchers.Main) {
                    pendingAdds.remove(song.id)
                    addedThisSession++
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pendingAdds.remove(song.id)
                    android.widget.Toast.makeText(
                        context, "Couldn't add \"${song.title}\"", android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    // Central search, debounced — the same provider the Search screen uses.
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            searchResults = emptyList()
            searching = false
            searchFailed = false
            return@LaunchedEffect
        }
        searching = true
        searchFailed = false
        delay(400)
        val result = withContext(Dispatchers.IO) {
            runCatching {
                GratiaApp.instance.providerManager.youtubeMusicProvider
                    .search(trimmed, "songs", limit = 25)
                    .tracks
                    .map { RemoteTrackMapper.toSongEntity(it) }
            }.getOrNull()
        }
        searchResults = result.orEmpty()
        searchFailed = result == null
        searching = false
    }

    // On-device rows: local files and downloads. The text filter only applies
    // while a query is active; otherwise the section lists the whole library.
    val lowered = query.trim().lowercase()
    val onDeviceSongs = remember(allSongs, lowered) {
        allSongs.asSequence()
            .filter { it.playsFromDevice() }
            .filter { lowered.isEmpty() ||
                it.title.lowercase().contains(lowered) ||
                it.artist.lowercase().contains(lowered) }
            .take(100)
            .toList()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = GratiaTheme.colors.background,
        dragHandle = { BottomSheetDefaults.DragHandle(color = GratiaTheme.colors.glassBorder) },
        modifier = Modifier.fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Add music",
                        fontFamily = SpaceGrotesk,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = GratiaTheme.colors.textPrimary
                    )
                    if (!playlistName.isNullOrBlank()) {
                        Text(
                            text = "to $playlistName",
                            fontFamily = Inter,
                            fontSize = 14.sp,
                            color = GratiaTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (addedThisSession > 0) {
                    Text(
                        text = "${addedThisSession} added",
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        color = GratiaTheme.colors.accent,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = GratiaTheme.colors.textSecondary
                    )
                }
            }

            // Search field
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = {
                    Text("Search songs to add", fontFamily = Inter, color = GratiaTheme.colors.textSecondary)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = GratiaTheme.colors.textSecondary)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search", tint = GratiaTheme.colors.textSecondary)
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GratiaTheme.colors.accent,
                    unfocusedBorderColor = GratiaTheme.colors.glassBorder,
                    focusedTextColor = GratiaTheme.colors.textPrimary,
                    unfocusedTextColor = GratiaTheme.colors.textPrimary,
                    cursorColor = GratiaTheme.colors.accent,
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ── On device ───────────────────────────────────────────
                item(key = "section_device") {
                    SectionLabel("ON DEVICE")
                }
                if (onDeviceSongs.isEmpty()) {
                    item(key = "device_empty") {
                        EmptyHint(
                            if (lowered.isEmpty()) "No songs on this device yet. Download or import music to add it here."
                            else "No on-device songs match \"${query.trim()}\"."
                        )
                    }
                } else {
                    items(onDeviceSongs.size, key = { "device_${onDeviceSongs[it].id}" }) { i ->
                        val song = onDeviceSongs[i]
                        SongAddRow(
                            song = song,
                            added = isAdded(song.id),
                            onAdd = { addToPlaylist(song) }
                        )
                    }
                }

                // ── Search online ───────────────────────────────────────
                item(key = "section_online") {
                    SectionLabel("SEARCH GRATIA")
                }
                when {
                    lowered.length < 2 -> item(key = "online_hint") {
                        EmptyHint("Search YouTube Music to add songs that aren't on this device.")
                    }
                    searching -> item(key = "online_loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), color = GratiaTheme.colors.accent, strokeWidth = 2.5.dp)
                        }
                    }
                    searchFailed -> item(key = "online_failed") {
                        EmptyHint(if (isOnline) "Search didn't answer. Try again in a moment." else "You're offline — search needs a connection.")
                    }
                    searchResults.isEmpty() -> item(key = "online_empty") {
                        EmptyHint("No songs found for \"${query.trim()}\".")
                    }
                    else -> items(searchResults.size, key = { "online_${searchResults[it].id}" }) { i ->
                        val song = searchResults[i]
                        SongAddRow(
                            song = song,
                            added = isAdded(song.id),
                            onAdd = { addToPlaylist(song) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        letterSpacing = 1.2.sp,
        color = GratiaTheme.colors.textSecondary,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp, start = 4.dp)
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        fontFamily = Inter,
        fontSize = 13.sp,
        color = GratiaTheme.colors.textSecondary,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp)
    )
}

@Composable
private fun SongAddRow(
    song: com.gratia.music.data.model.SongEntity,
    added: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverArtImage(
            coverArtPath = song.coverArtPath ?: song.artworkUrl,
            title = song.title,
            artist = song.artist,
            size = 48.dp,
            cornerRadius = 10.dp,
            fontSize = 15.sp
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                color = if (added) GratiaTheme.colors.textSecondary else GratiaTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                fontFamily = Inter,
                fontSize = 12.sp,
                color = GratiaTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        // The add affordance is the row's state: [+] to add, [✓] in the
        // playlist. Tapping an added row does nothing — the state is already
        // the outcome (playlist spec 21).
        if (added) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Already in playlist",
                tint = GratiaTheme.colors.accent,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(GratiaTheme.colors.surfaceHover)
                    .clickable { onAdd() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add to playlist",
                    tint = GratiaTheme.colors.textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
