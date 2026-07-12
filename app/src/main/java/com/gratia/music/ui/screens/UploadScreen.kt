package com.gratia.music.ui.screens

import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gratia.music.GratiaApp
import com.gratia.music.data.CoverArtManager
import com.gratia.music.data.model.SongEntity
import com.gratia.music.data.repository.SongRepository
import com.gratia.music.ui.components.CoverArtImage
import com.gratia.music.ui.theme.GratiaTheme
import com.gratia.music.ui.theme.Inter
import com.gratia.music.ui.theme.SpaceGrotesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadScreen(
    onNavigateBack: () -> Unit,
    editSongId: String? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val songRepo = remember { SongRepository(GratiaApp.instance.database.songDao()) }

    val isEditMode = editSongId != null
    var existingSong by remember { mutableStateOf<SongEntity?>(null) }
    var formLoaded by remember { mutableStateOf(!isEditMode) }

    // File state
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf("") }
    var mimeType by remember { mutableStateOf("") }
    var detectedDurationMs by remember { mutableLongStateOf(0L) }

    // Form state
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }
    var mood by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf("") }
    var lyricsInput by remember { mutableStateOf("") }

    val detectedModeText by remember(lyricsInput) {
        derivedStateOf {
            if (lyricsInput.isBlank()) {
                ""
            } else {
                val mode = com.gratia.music.lyrics.LyricsModeDetector.detectMode(lyricsInput)
                try {
                    val parsed = com.gratia.music.lyrics.LyricsParser.parse(lyricsInput)
                    when (mode) {
                        com.gratia.music.lyrics.LyricsMode.JSON -> {
                            if (parsed is com.gratia.music.lyrics.LyricsDocument.WordSynced && parsed.lines.isNotEmpty()) {
                                "Detected: JSON word sync"
                            } else {
                                "Invalid lyric format"
                            }
                        }
                        com.gratia.music.lyrics.LyricsMode.ELRC -> {
                            if (parsed is com.gratia.music.lyrics.LyricsDocument.WordSynced && parsed.lines.isNotEmpty()) {
                                "Detected: Enhanced LRC word sync"
                            } else {
                                "Invalid lyric format"
                            }
                        }
                        com.gratia.music.lyrics.LyricsMode.LRC -> {
                            if (parsed is com.gratia.music.lyrics.LyricsDocument.LineSynced && parsed.lines.isNotEmpty()) {
                                "Detected: LRC line sync"
                            } else {
                                "Invalid lyric format"
                            }
                        }
                        com.gratia.music.lyrics.LyricsMode.PLAIN -> {
                            "Detected: Plain lyrics"
                        }
                    }
                } catch (_: Exception) {
                    "Invalid lyric format"
                }
            }
        }
    }

    // Cover art state
    var coverArtPath by remember { mutableStateOf<String?>(null) }
    var coverSource by remember { mutableStateOf<String?>(null) }
    var hasEmbeddedCover by remember { mutableStateOf(false) }
    var selectedCoverUri by remember { mutableStateOf<Uri?>(null) }

    // Audio quality state
    var audioFormat by remember { mutableStateOf<String?>(null) }
    var audioBitrate by remember { mutableStateOf<Int?>(null) }
    var audioSampleRate by remember { mutableStateOf<Int?>(null) }
    var fileSizeBytes by remember { mutableLongStateOf(0L) }

    // UI state
    var uploading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var validationErrors by remember { mutableStateOf(mapOf<String, String>()) }

    val moodChips = listOf("Chill", "Sad", "Happy", "Romantic", "Energetic", "Focus", "Late Night", "Workout")
    val languageChips = listOf("English", "Hindi", "Japanese", "Korean", "Instrumental", "Other")

    // Load existing song for edit mode
    LaunchedEffect(editSongId) {
        if (editSongId != null) {
            val song = songRepo.getSongById(editSongId)
            if (song != null) {
                existingSong = song
                title = song.title
                artist = song.artist
                album = song.album ?: ""
                language = song.language ?: ""
                mood = song.mood ?: ""
                tags = song.tags ?: ""
                aliases = song.aliases ?: ""
                lyricsInput = if (song.lyricsMode == "synced") song.lyricsSynced ?: "" else song.lyricsPlain ?: song.lyrics ?: ""
                coverArtPath = song.coverArtPath
                coverSource = song.coverSource
                selectedUri = song.localUri?.let { Uri.parse(it) }
                fileName = song.fileName ?: ""
                detectedDurationMs = song.durationMs
                mimeType = song.mimeType ?: ""
                audioFormat = song.format
                audioBitrate = song.bitrate
                audioSampleRate = song.sampleRate
                fileSizeBytes = song.fileSizeBytes ?: 0L
                fileSize = formatFileSize(fileSizeBytes)
            }
            formLoaded = true
        }
    }

    // Cover image picker
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        selectedCoverUri = uri
        coverSource = "user_selected"

        // Preview will use this URI; actual save happens on submit
        scope.launch {
            val songId = existingSong?.id ?: UUID.randomUUID().toString()
            withContext(Dispatchers.IO) {
                val path = CoverArtManager.copyCoverFromUri(context, songId, uri)
                if (path != null) {
                    coverArtPath = path
                }
            }
        }
    }

    // File picker
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult

        // Persist permission
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) { }

        selectedUri = uri
        error = null
        success = false
        hasEmbeddedCover = false

        // Get file info
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: "Unknown"
                if (sizeIdx >= 0) {
                    fileSizeBytes = cursor.getLong(sizeIdx)
                    fileSize = formatFileSize(fileSizeBytes)
                }
            }
        }

        mimeType = context.contentResolver.getType(uri) ?: "audio/*"

        // Auto-fill title from filename
        val cleanName = fileName.replace(Regex("\\.[^.]+$"), "")
            .replace(Regex("[-_]"), " ").trim()
        if (title.isBlank()) title = cleanName

        // Detect metadata + embedded cover
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, uri)
                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    detectedDurationMs = durationStr?.toLongOrNull() ?: 0L

                    val metaArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    val metaAlbum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                    val metaTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    val metaBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    val metaMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)

                    if (!metaTitle.isNullOrBlank() && !isEditMode) title = metaTitle
                    if (!metaArtist.isNullOrBlank() && artist.isBlank()) artist = metaArtist
                    if (!metaAlbum.isNullOrBlank() && album.isBlank()) album = metaAlbum

                    // Audio quality
                    audioFormat = metaMime?.let { detectFormat(it, fileName) }
                    audioBitrate = metaBitrate?.toIntOrNull()?.let { it / 1000 } // kbps

                    // Extract embedded cover
                    val embeddedCover = CoverArtManager.extractEmbeddedCover(context, uri)
                    if (embeddedCover != null && coverArtPath == null) {
                        val songId = existingSong?.id ?: UUID.randomUUID().toString()
                        coverArtPath = CoverArtManager.saveCoverToInternal(context, songId, embeddedCover)
                        coverSource = "embedded"
                        hasEmbeddedCover = true
                        embeddedCover.recycle()
                    } else if (embeddedCover != null) {
                        hasEmbeddedCover = true
                        embeddedCover.recycle()
                    }

                    retriever.release()
                } catch (_: Exception) { }
            }
        }
    }

    // Real-time automatic validation replaces manual validate function

    fun handleSubmit() {
        val currentErrors = mutableMapOf<String, String>()
        if (!isEditMode && selectedUri == null) {
            currentErrors["file"] = "Please select an audio file"
        }
        if (title.isBlank()) {
            currentErrors["title"] = "Title is required"
        }
        if (artist.isBlank()) {
            currentErrors["artist"] = "Artist is required"
        }
        
        validationErrors = currentErrors
        if (currentErrors.isNotEmpty()) return

        uploading = true
        error = null

        val isSyncedMode = detectedModeText.startsWith("Detected:") && detectedModeText.contains("sync")
        val finalLyricsPlain = if (isSyncedMode) null else lyricsInput.trim().ifBlank { null }
        val finalLyricsSynced = if (isSyncedMode) lyricsInput.trim().ifBlank { null } else null
        val finalLyricsMode = if (isSyncedMode) "synced" else "plain"

        scope.launch {
            try {
                if (isEditMode && existingSong != null) {
                    val updated = existingSong!!.copy(
                        title = title.trim(),
                        artist = artist.trim(),
                        album = album.trim().ifBlank { null },
                        mood = mood.trim().ifBlank { null },
                        language = language.trim().ifBlank { null },
                        tags = tags.trim().ifBlank { null },
                        aliases = aliases.trim().ifBlank { null },
                        lyrics = finalLyricsPlain,
                        lyricsPlain = finalLyricsPlain,
                        lyricsSynced = finalLyricsSynced,
                        lyricsMode = finalLyricsMode,
                        coverArtPath = coverArtPath,
                        coverSource = coverSource,
                        format = audioFormat,
                        bitrate = audioBitrate,
                        sampleRate = audioSampleRate,
                        fileSizeBytes = if (fileSizeBytes > 0) fileSizeBytes else null,
                        updatedAt = System.currentTimeMillis(),
                    )
                    songRepo.updateSong(updated)
                    success = true
                } else {
                    val song = SongEntity(
                        title = title.trim(),
                        artist = artist.trim(),
                        album = album.trim().ifBlank { null },
                        mood = mood.trim().ifBlank { null },
                        language = language.trim().ifBlank { null },
                        tags = tags.trim().ifBlank { null },
                        aliases = aliases.trim().ifBlank { null },
                        lyrics = finalLyricsPlain,
                        lyricsPlain = finalLyricsPlain,
                        lyricsSynced = finalLyricsSynced,
                        lyricsMode = finalLyricsMode,
                        durationMs = detectedDurationMs,
                        localUri = selectedUri.toString(),
                        mimeType = mimeType,
                        fileName = fileName,
                        storageProvider = "local",
                        coverArtPath = coverArtPath,
                        coverSource = coverSource,
                        format = audioFormat,
                        bitrate = audioBitrate,
                        sampleRate = audioSampleRate,
                        fileSizeBytes = if (fileSizeBytes > 0) fileSizeBytes else null,
                    )
                    songRepo.insertSong(song)
                    success = true
                    // Reset form
                    selectedUri = null; fileName = ""; fileSize = ""
                    title = ""; artist = ""; album = ""; language = ""
                    mood = ""; tags = ""; aliases = ""; lyricsInput = ""
                    detectedDurationMs = 0L
                    coverArtPath = null; coverSource = null
                    hasEmbeddedCover = false
                    audioFormat = null; audioBitrate = null
                    audioSampleRate = null; fileSizeBytes = 0L
                }
            } catch (e: Exception) {
                error = "Failed to save song. Please try again."
            } finally {
                uploading = false
            }
        }
    }

    val canSubmit = !uploading

    if (!formLoaded) {
        Box(modifier = Modifier.fillMaxSize().background(GratiaTheme.colors.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = GratiaTheme.colors.accent)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GratiaTheme.colors.background)
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, GratiaTheme.colors.glassBorder, CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp), tint = GratiaTheme.colors.textSecondary)
            }
            Text(
                if (isEditMode) "Edit Song" else "Upload to ",
                fontFamily = SpaceGrotesk,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = GratiaTheme.colors.textPrimary
            )
            if (!isEditMode) {
                Text(
                    "Gratia",
                    fontFamily = SpaceGrotesk,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = GratiaTheme.colors.accent
                )
            }
        }

        // Glass panel
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = GratiaTheme.colors.surface,
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.glassBorder)
            ),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // File picker zone (hide in edit mode if file already set)
                if (!isEditMode || selectedUri == null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { filePicker.launch(arrayOf("audio/*", "video/*")) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedUri != null) GratiaTheme.colors.accent.copy(alpha = 0.05f)
                        else GratiaTheme.colors.surface,
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (validationErrors.containsKey("file")) GratiaTheme.colors.error
                                else if (selectedUri != null) GratiaTheme.colors.accent.copy(alpha = 0.3f)
                                else GratiaTheme.colors.glassBorder
                            )
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                if (selectedUri != null) Icons.Default.AudioFile else Icons.Default.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = if (selectedUri != null) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            if (selectedUri != null) {
                                Text(fileName, fontFamily = Inter, fontSize = 13.sp, color = GratiaTheme.colors.textPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(fileSize, fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                                    if (detectedDurationMs > 0) {
                                        Text("·", fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                                        Text(formatDuration(detectedDurationMs), fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                                    }
                                    audioFormat?.let {
                                        Text("·", fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                                        Text(it.uppercase(), fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.accent)
                                    }
                                }
                                if (audioBitrate != null) {
                                    Text("${audioBitrate} kbps", fontFamily = Inter, fontSize = 10.sp, color = GratiaTheme.colors.textSecondary)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text("Tap to change file", fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                            } else {
                                Text("Tap to select an audio file", fontFamily = Inter, fontSize = 13.sp, color = GratiaTheme.colors.textSecondary)
                                Text("mp3, m4a, wav, ogg, aac, flac", fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                            }
                        }
                    }
                    if (validationErrors.containsKey("file")) {
                        Spacer(Modifier.height(4.dp))
                        Text(validationErrors["file"]!!, fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.error)
                    }
                } else {
                    // Show file info in edit mode
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = GratiaTheme.colors.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.AudioFile, null, tint = GratiaTheme.colors.accent, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(fileName.ifBlank { "Audio file" }, fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.textPrimary)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (fileSize.isNotBlank()) Text(fileSize, fontFamily = Inter, fontSize = 10.sp, color = GratiaTheme.colors.textSecondary)
                                    audioFormat?.let { Text(it.uppercase(), fontFamily = Inter, fontSize = 10.sp, color = GratiaTheme.colors.accent) }
                                    audioBitrate?.let { Text("${it} kbps", fontFamily = Inter, fontSize = 10.sp, color = GratiaTheme.colors.textSecondary) }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ======= Cover Art Section =======
                Text("Cover Image", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Cover preview
                    CoverArtImage(
                        coverArtPath = coverArtPath,
                        title = title.ifBlank { "?" },
                        artist = artist,
                        size = 72.dp,
                        cornerRadius = 12.dp,
                        fontSize = 20.sp
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        if (hasEmbeddedCover && coverSource == "embedded") {
                            Text("Cover found in audio file", fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.success)
                        } else if (coverArtPath != null) {
                            Text("Cover image selected", fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.accent)
                        } else {
                            Text("No cover found", fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.textSecondary)
                            Text("Add an optional cover image.", fontFamily = Inter, fontSize = 10.sp, color = GratiaTheme.colors.textSecondary)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                    coverPicker.launch(arrayOf("image/*"))
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = GratiaTheme.colors.surface,
                            ) {
                                Text(
                                    if (coverArtPath != null) "Change cover" else "Add cover image",
                                    fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.accent,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            if (coverArtPath != null && coverSource != "embedded") {
                                Surface(
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                                        coverArtPath = null
                                        coverSource = null
                                        selectedCoverUri = null
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = GratiaTheme.colors.surface,
                                ) {
                                    Text(
                                        "Remove",
                                        fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.error,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Title
                GratiaTextField(
                    value = title, 
                    onValueChange = { 
                        title = it
                        if (validationErrors.containsKey("title")) validationErrors = validationErrors - "title"
                    }, 
                    label = "Title *", 
                    placeholder = "Song title",
                    errorMessage = validationErrors["title"]
                )
                Spacer(Modifier.height(12.dp))

                // Artist
                GratiaTextField(
                    value = artist, 
                    onValueChange = { 
                        artist = it
                        if (validationErrors.containsKey("artist")) validationErrors = validationErrors - "artist"
                    }, 
                    label = "Artist *", 
                    placeholder = "Artist name",
                    errorMessage = validationErrors["artist"]
                )
                Spacer(Modifier.height(12.dp))

                // Album
                GratiaTextField(value = album, onValueChange = { album = it }, label = "Album", placeholder = "Album name")
                Spacer(Modifier.height(12.dp))

                // Language chips
                Text("Language", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    languageChips.forEach { chip ->
                        GratiaChip(text = chip, selected = language == chip, onClick = { language = chip })
                    }
                }
                Spacer(Modifier.height(8.dp))
                GratiaTextField(value = language, onValueChange = { language = it }, label = "", placeholder = "e.g. Hindi, English, Tamil")
                Spacer(Modifier.height(12.dp))

                // Mood chips
                Text("Mood", fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    moodChips.forEach { chip ->
                        val moods = mood.split(",").map { it.trim() }
                        GratiaChip(text = chip, selected = chip in moods, onClick = {
                            mood = if (chip in moods) moods.filter { it != chip }.joinToString(", ")
                            else if (mood.isBlank()) chip else "$mood, $chip"
                        })
                    }
                }
                Spacer(Modifier.height(8.dp))
                GratiaTextField(value = mood, onValueChange = { mood = it }, label = "", placeholder = "Comma-separated moods")
                Spacer(Modifier.height(12.dp))

                // Tags
                GratiaTextField(value = tags, onValueChange = { tags = it }, label = "Tags", placeholder = "e.g. bollywood, acoustic, 90s")
                Spacer(Modifier.height(12.dp))

                // Aliases
                GratiaTextField(value = aliases, onValueChange = { aliases = it }, label = "Aliases", placeholder = "Alternate spellings for search")
                Spacer(Modifier.height(16.dp))

                // ======= Lyrics Section =======
                HorizontalDivider(color = GratiaTheme.colors.glassBorder)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Lyrics",
                    fontFamily = Inter,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = GratiaTheme.colors.textSecondary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Paste plain lyrics, LRC, Enhanced LRC, or JSON word lyrics",
                    fontFamily = Inter,
                    fontSize = 11.sp,
                    color = GratiaTheme.colors.textSecondary
                )
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = lyricsInput,
                    onValueChange = { lyricsInput = it },
                    placeholder = {
                        Text(
                            text = "Paste lyrics here...",
                            color = GratiaTheme.colors.textSecondary,
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GratiaTheme.colors.accent.copy(alpha = 0.4f),
                        unfocusedBorderColor = GratiaTheme.colors.glassBorder,
                        focusedContainerColor = GratiaTheme.colors.surface,
                        unfocusedContainerColor = GratiaTheme.colors.surface,
                        focusedTextColor = GratiaTheme.colors.textPrimary,
                        unfocusedTextColor = GratiaTheme.colors.textPrimary,
                        cursorColor = GratiaTheme.colors.accent,
                    ),
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontFamily = Inter)
                )

                if (detectedModeText.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = detectedModeText,
                        fontFamily = Inter,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (detectedModeText == "Invalid lyric format") GratiaTheme.colors.error 
                                else GratiaTheme.colors.success
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Small Helper Examples Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = GratiaTheme.colors.surface,
                    border = ButtonDefaults.outlinedButtonBorder.copy(
                        brush = androidx.compose.ui.graphics.SolidColor(GratiaTheme.colors.glassBorder)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Examples:",
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = GratiaTheme.colors.textSecondary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Plain:\nFirst line\nSecond line\n\nLRC:\n[00:05.00] First line\n\nEnhanced LRC:\n[00:05.00] <00:05.00> First <00:05.40> line",
                            fontFamily = Inter,
                            fontSize = 10.sp,
                            color = GratiaTheme.colors.textSecondary,
                            lineHeight = 14.sp
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Error
                if (error != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GratiaTheme.colors.error.copy(alpha = 0.08f),
                    ) {
                        Text(error!!, fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.error,
                            modifier = Modifier.padding(12.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Success
                if (success) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = GratiaTheme.colors.success.copy(alpha = 0.08f),
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = GratiaTheme.colors.success, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isEditMode) "Song updated!" else "Song saved to your library!",
                                fontFamily = Inter, fontSize = 12.sp, color = GratiaTheme.colors.success
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Submit
                Button(
                    onClick = { handleSubmit() },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GratiaTheme.colors.accent,
                        contentColor = GratiaTheme.colors.background,
                        disabledContainerColor = GratiaTheme.colors.surfaceHover,
                        disabledContentColor = GratiaTheme.colors.textSecondary
                    )
                ) {
                    if (uploading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = GratiaTheme.colors.background)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving…", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    } else {
                        Text(if (isEditMode) "Save Changes" else "Upload", fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun GratiaTextField(value: String, onValueChange: (String) -> Unit, label: String, placeholder: String, errorMessage: String? = null) {
    if (label.isNotBlank()) {
        Text(label, fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = GratiaTheme.colors.textSecondary)
        Spacer(Modifier.height(6.dp))
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = GratiaTheme.colors.textSecondary, fontSize = 13.sp) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (errorMessage != null) GratiaTheme.colors.error else GratiaTheme.colors.accent.copy(alpha = 0.4f),
            unfocusedBorderColor = if (errorMessage != null) GratiaTheme.colors.error else GratiaTheme.colors.glassBorder,
            focusedContainerColor = GratiaTheme.colors.surface,
            unfocusedContainerColor = GratiaTheme.colors.surface,
            focusedTextColor = GratiaTheme.colors.textPrimary,
            unfocusedTextColor = GratiaTheme.colors.textPrimary,
            cursorColor = GratiaTheme.colors.accent,
        ),
        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, fontFamily = Inter)
    )
    if (errorMessage != null) {
        Spacer(Modifier.height(4.dp))
        Text(errorMessage, fontFamily = Inter, fontSize = 11.sp, color = GratiaTheme.colors.error)
    }
}

@Composable
private fun GratiaChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (selected) GratiaTheme.colors.accent.copy(alpha = 0.2f) else GratiaTheme.colors.surface,
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (selected) GratiaTheme.colors.accent.copy(alpha = 0.4f) else GratiaTheme.colors.glassBorder
            )
        )
    ) {
        Text(
            text,
            fontFamily = Inter,
            fontSize = 11.sp,
            color = if (selected) GratiaTheme.colors.accent else GratiaTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return ""
    val totalSecs = ms / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "$mins:${secs.toString().padStart(2, '0')}"
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    return if (bytes < 1024 * 1024) "${(bytes / 1024f).toInt()} KB"
    else "%.1f MB".format(bytes / (1024f * 1024f))
}

private fun detectFormat(mimeType: String, fileName: String): String {
    return when {
        mimeType.contains("flac") -> "FLAC"
        mimeType.contains("wav") || mimeType.contains("wave") -> "WAV"
        mimeType.contains("ogg") -> "OGG"
        mimeType.contains("aac") || mimeType.contains("mp4a") -> "AAC"
        mimeType.contains("mpeg") || fileName.endsWith(".mp3", true) -> "MP3"
        mimeType.contains("m4a") || fileName.endsWith(".m4a", true) -> "M4A"
        else -> mimeType.substringAfterLast("/").uppercase()
    }
}
